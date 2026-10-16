package com.driving.exam.server.appointment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AppointmentCreateDTO;
import com.driving.exam.pojo.entity.Appointment;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.enums.AppointmentStatus;
import com.driving.exam.pojo.vo.AppointmentVO;
import com.driving.exam.pojo.vo.CoachVO;
import com.driving.exam.server.appointment.AppointmentService;
import com.driving.exam.server.mapper.AppointmentMapper;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预约 Service 实现。
 *
 * <h3>三条贯穿全类的原则</h3>
 * <ol>
 *   <li><b>先归属、后状态</b>：所有按 id 的操作先做归属校验（{@code WHERE id=? AND student_id/coach_id=?}），
 *       查不到就抛 {@code 3001}；<b>确认存在且属于本人之后</b>才判状态（{@code 3004 / 3005}）。
 *       顺序反了会泄露"这条记录存在"给非本人。</li>
 *   <li><b>防 N+1</b>：列表先把记录查出来，再收集去重后的用户 ID，<b>一次 {@code IN} 查 {@code sys_user}</b>，
 *       然后在内存里装配姓名与电话——绝不循环里逐条查库。</li>
 *   <li><b>不加 {@code @Transactional}</b>：五个写方法（create / cancel / approve / reject / complete）
 *       都是单表单条写，SQL 自身原子，与刷题作答、AI 落库一致；只读方法也不加。</li>
 * </ol>
 *
 * <h3>关于 {@code sys_user} 的逻辑删除</h3>
 * <p>{@code User} 实体带 {@code @TableLogic}，因此 {@code selectBatchIds} 会自动过滤掉已删除账号。
 * 由此产生一条<b>刻意的差异</b>：</p>
 * <ul>
 *   <li>{@link #listMyCoaches} 是"**选择对象**"——查不到就**跳过**，不返回姓名可能为空的卡片；</li>
 *   <li>{@link #listMyAppointments} / {@link #listCoachAppointments} 是"**历史记录**"——查不到就
 *       **保留记录**、姓名电话留 {@code null}，历史不该因对方账号被删而消失。</li>
 * </ul>
 */
@Service
public class AppointmentServiceImpl implements AppointmentService {

    /** 分页每页上限（防止前端传大值一次拉太多） */
    private static final int MAX_PAGE_SIZE = 20;

    /** 非法或未指定 pageSize 时的默认值 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 绑定"有效"状态（{@code coach_student.status}）：1 有效 / 0 解除 */
    private static final int BIND_STATUS_ACTIVE = 1;

    /** "占用中"的预约状态名：只有这两个占用时段；终态（CANCELLED/REJECTED/COMPLETED）会释放时段 */
    private static final List<String> OCCUPYING_STATUSES = List.of(
            AppointmentStatus.PENDING.name(),
            AppointmentStatus.CONFIRMED.name());

    private final AppointmentMapper appointmentMapper;
    private final CoachStudentMapper coachStudentMapper;
    private final UserMapper userMapper;

    public AppointmentServiceImpl(AppointmentMapper appointmentMapper,
                                  CoachStudentMapper coachStudentMapper,
                                  UserMapper userMapper) {
        this.appointmentMapper = appointmentMapper;
        this.coachStudentMapper = coachStudentMapper;
        this.userMapper = userMapper;
    }

    /* ============================== 学员端 ============================== */

    @Override
    public List<CoachVO> listMyCoaches(Long studentId) {
        // coach_student 没有 deleted 列，用 status=1 过滤"有效绑定"（切勿指望框架自动过滤）
        List<CoachStudent> binds = coachStudentMapper.selectList(new LambdaQueryWrapper<CoachStudent>()
                .eq(CoachStudent::getStudentId, studentId)
                .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE));
        if (binds.isEmpty()) {
            return new ArrayList<>(); // 未绑定任何教练 → 空数组（这不是错误）
        }

        List<Long> coachIds = binds.stream()
                .map(CoachStudent::getCoachId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, User> userMap = loadUserMap(coachIds);

        List<CoachVO> list = new ArrayList<>(coachIds.size());
        for (Long coachId : coachIds) {
            User coach = userMap.get(coachId);
            if (coach == null) {
                continue; // 选择对象要可用：教练账号已删 → 跳过，不给学员一张点不了的卡片
            }
            list.add(toCoachVO(coach));
        }
        return list;
    }

    @Override
    public void createAppointment(Long studentId, AppointmentCreateDTO dto) {
        // ① 该教练必须是本人"有效绑定"的教练（coach_student 无 deleted，用 status=1）
        Long bound = coachStudentMapper.selectCount(new LambdaQueryWrapper<CoachStudent>()
                .eq(CoachStudent::getStudentId, studentId)
                .eq(CoachStudent::getCoachId, dto.getCoachId())
                .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE));
        if (bound == null || bound < 1) {
            throw new BusinessException(ErrorCode.STUDENT_NOT_BOUND);
        }

        // ② 时段冲突：占用只算 PENDING / CONFIRMED —— 必须排除终态，
        //    否则"取消 / 拒绝过的时段会永久占位"，学员再也约不上（最容易写错的一条）
        Long conflict = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getCoachId, dto.getCoachId())
                .eq(Appointment::getAppointmentDate, dto.getAppointmentDate())
                .eq(Appointment::getTimeSlot, dto.getTimeSlot())
                .in(Appointment::getStatus, OCCUPYING_STATUSES));
        if (conflict != null && conflict > 0) {
            throw new BusinessException(ErrorCode.APPOINTMENT_TIME_CONFLICT);
        }

        // ③ 落库（单表单条写，不加事务；"先查后插"的并发竞态记录在案、课设不处理）
        Appointment appointment = new Appointment();
        appointment.setStudentId(studentId);
        appointment.setCoachId(dto.getCoachId());
        appointment.setSubjectCode(dto.getSubjectCode());
        appointment.setAppointmentDate(dto.getAppointmentDate());
        appointment.setTimeSlot(dto.getTimeSlot());
        appointment.setStatus(AppointmentStatus.PENDING.name());
        appointment.setRemark(dto.getRemark());
        appointmentMapper.insert(appointment);
    }

    @Override
    public PageResult<AppointmentVO> listMyAppointments(Long studentId, AppointmentStatus status,
                                                        String subjectCode, int pageNum, int pageSize) {
        int current = Math.max(pageNum, 1);
        int size = (pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusName = (status == null) ? null : status.name();

        Page<Appointment> page = appointmentMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getStudentId, studentId) // 越权收敛：只看自己的
                        .eq(statusName != null, Appointment::getStatus, statusName)
                        .eq(StringUtils.hasText(subjectCode), Appointment::getSubjectCode, subjectCode)
                        // 排序：按"操作时间"倒序 —— 最新发生的在最前，学员提交 / 取消后立刻能在列表顶部看到结果。
                        // 刻意不用 appointment_date：预约列表混合了过去与未来，DESC（最远未来在前）与
                        // ASC（最久过去在前）都没有自然正确的方向；"按预约日期看"应由前端分 tab / 筛选解决。
                        // id 作次级键：create_time 是秒精度，同秒记录靠它保证全序稳定，避免分页漂移。
                        .orderByDesc(Appointment::getCreateTime)
                        .orderByDesc(Appointment::getId));

        return toPageResult(page);
    }

    @Override
    public AppointmentVO getMyAppointment(Long studentId, Long appointmentId) {
        Appointment appointment = loadStudentOwned(studentId, appointmentId); // 先归属 → 0 行抛 3001
        Map<Long, User> userMap = loadUserMap(userIdsOf(appointment));
        return toVO(appointment, userMap);
    }

    @Override
    public void cancelMyAppointment(Long studentId, Long appointmentId) {
        Appointment appointment = loadStudentOwned(studentId, appointmentId); // 先归属、后状态
        if (!AppointmentStatus.PENDING.name().equals(appointment.getStatus())) {
            // 仅 PENDING 可取消；CONFIRMED 需联系教练（api-design §5 第 2 条）
            throw new BusinessException(ErrorCode.APPOINTMENT_CANCEL_NOT_ALLOWED);
        }
        appointment.setStatus(AppointmentStatus.CANCELLED.name()); // 终态：时段随之释放
        appointmentMapper.updateById(appointment);
    }

    /* ============================== 教练端 ============================== */

    @Override
    public PageResult<AppointmentVO> listCoachAppointments(Long coachId, AppointmentStatus status,
                                                           int pageNum, int pageSize) {
        int current = Math.max(pageNum, 1);
        int size = (pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusName = (status == null) ? null : status.name();

        // 不提供科目筛选（与 api-design §4.10 一致，那里只写"可按状态筛"）
        Page<Appointment> page = appointmentMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getCoachId, coachId) // 越权收敛：教练只看自己名下
                        .eq(statusName != null, Appointment::getStatus, statusName)
                        // 排序同上：操作时间倒序 + id 次级键（理由见 listMyAppointments）。
                        // 教练端刻意不做"PENDING 置顶"：§4.10 只要求"可按状态筛"，且两端排序保持一致更好维护；
                        // 真要"待处理优先"，正解是前端默认选中 status=PENDING 筛选，而不是后端塞排序。
                        .orderByDesc(Appointment::getCreateTime)
                        .orderByDesc(Appointment::getId));

        return toPageResult(page);
    }

    @Override
    public void approveAppointment(Long coachId, Long appointmentId) {
        Appointment appointment = loadCoachOwned(coachId, appointmentId); // 先归属、后状态
        if (!AppointmentStatus.PENDING.name().equals(appointment.getStatus())) {
            throw new BusinessException(ErrorCode.APPOINTMENT_STATUS_ILLEGAL);
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED.name());
        appointmentMapper.updateById(appointment);
    }

    @Override
    public void rejectAppointment(Long coachId, Long appointmentId, String coachRemark) {
        Appointment appointment = loadCoachOwned(coachId, appointmentId); // 先归属、后状态
        if (!AppointmentStatus.PENDING.name().equals(appointment.getStatus())) {
            throw new BusinessException(ErrorCode.APPOINTMENT_STATUS_ILLEGAL);
        }
        // 拒绝落 REJECTED（与学员主动取消的 CANCELLED 区分）；时段随之释放
        appointment.setStatus(AppointmentStatus.REJECTED.name());
        appointment.setCoachRemark(coachRemark); // 拒绝原因可为 null，直接存
        appointmentMapper.updateById(appointment);
    }

    @Override
    public void completeAppointment(Long coachId, Long appointmentId) {
        Appointment appointment = loadCoachOwned(coachId, appointmentId); // 先归属、后状态
        if (!AppointmentStatus.CONFIRMED.name().equals(appointment.getStatus())) {
            throw new BusinessException(ErrorCode.APPOINTMENT_STATUS_ILLEGAL);
        }
        appointment.setStatus(AppointmentStatus.COMPLETED.name());
        appointmentMapper.updateById(appointment);
    }

    /* ============================== 私有工具 ============================== */

    /**
     * 学员视角的归属校验：{@code WHERE id = ? AND student_id = ?}。
     *
     * <p>查不到（不存在**或**不属于当前学员）统一抛 {@code 3001}——越权与不存在返回同一错误码，
     * 不泄露存在性。</p>
     */
    private Appointment loadStudentOwned(Long studentId, Long appointmentId) {
        Appointment appointment = appointmentMapper.selectOne(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getId, appointmentId)
                .eq(Appointment::getStudentId, studentId));
        if (appointment == null) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return appointment;
    }

    /**
     * 教练视角的归属校验：{@code WHERE id = ? AND coach_id = ?}。语义同 {@link #loadStudentOwned}。
     *
     * <p>刻意与学员版<b>分成两个方法</b>（而不是一个方法带 role 参数）——参数名即身份，
     * 调用处一眼能看出"这是按哪个身份收敛的"，也避免把角色判断散进 SQL 条件里。</p>
     */
    private Appointment loadCoachOwned(Long coachId, Long appointmentId) {
        Appointment appointment = appointmentMapper.selectOne(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getId, appointmentId)
                .eq(Appointment::getCoachId, coachId));
        if (appointment == null) {
            throw new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND);
        }
        return appointment;
    }

    /**
     * <b>防 N+1 的关键</b>：按去重后的用户 ID 集合，一次 {@code IN} 查回 {@code sys_user}，
     * 组装成 {@code Map<id, User>} 供内存映射。
     *
     * <p>空集合直接返回空 Map——{@code selectBatchIds(空)} 会拼出 {@code WHERE id IN ()}，
     * 在 MySQL 是语法错误（与 {@code QuestionOptionAssembler.loadByQuestionIds} 同样的防御）。</p>
     *
     * <p>注意：{@code User} 带 {@code @TableLogic}，被逻辑删除的账号查不到 → 调用方必须容忍
     * {@code Map} 里没有对应 key。</p>
     */
    private Map<Long, User> loadUserMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> map = new HashMap<>();
        for (User user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    /** 收集一条预约涉及的两个用户 ID（去重、忽略 null），供 {@link #loadUserMap} 使用。 */
    private List<Long> userIdsOf(Appointment appointment) {
        Set<Long> ids = new HashSet<>();
        if (appointment.getStudentId() != null) {
            ids.add(appointment.getStudentId());
        }
        if (appointment.getCoachId() != null) {
            ids.add(appointment.getCoachId());
        }
        return new ArrayList<>(ids);
    }

    /** 收集一页预约涉及的全部用户 ID（去重），供 {@link #loadUserMap} 一次查询。 */
    private List<Long> userIdsOf(List<Appointment> appointments) {
        Set<Long> ids = new HashSet<>();
        for (Appointment a : appointments) {
            if (a.getStudentId() != null) {
                ids.add(a.getStudentId());
            }
            if (a.getCoachId() != null) {
                ids.add(a.getCoachId());
            }
        }
        return new ArrayList<>(ids);
    }

    /** 分页结果 → {@code PageResult<AppointmentVO>}（含一次 IN 装配姓名电话）。 */
    private PageResult<AppointmentVO> toPageResult(Page<Appointment> page) {
        Map<Long, User> userMap = loadUserMap(userIdsOf(page.getRecords()));
        List<AppointmentVO> records = page.getRecords().stream()
                .map(a -> toVO(a, userMap))
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    /**
     * 预约实体 → VO。
     *
     * <p>姓名 / 电话从 {@code userMap} 里取；<b>取不到就留 {@code null}，绝不 NPE</b>——
     * 这是"历史记录要完整"那一侧的既定行为（对方账号被逻辑删除时，记录仍在、姓名留空）。</p>
     */
    private AppointmentVO toVO(Appointment appointment, Map<Long, User> userMap) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(appointment.getId());
        vo.setStudentId(appointment.getStudentId());
        vo.setCoachId(appointment.getCoachId());
        vo.setSubjectCode(appointment.getSubjectCode());
        vo.setAppointmentDate(appointment.getAppointmentDate());
        vo.setTimeSlot(appointment.getTimeSlot());
        // status 只由本模块写入，值必为 5 个枚举名之一；若出现未知值说明数据被外部篡改，应立刻暴露
        vo.setStatus(appointment.getStatus() == null
                ? null
                : AppointmentStatus.valueOf(appointment.getStatus()));
        vo.setRemark(appointment.getRemark());
        vo.setCoachRemark(appointment.getCoachRemark());
        vo.setCreateTime(appointment.getCreateTime());
        vo.setUpdateTime(appointment.getUpdateTime());

        User student = userMap.get(appointment.getStudentId());
        if (student != null) {
            vo.setStudentName(student.getRealName());
            vo.setStudentPhone(student.getPhone());
        }
        User coach = userMap.get(appointment.getCoachId());
        if (coach != null) {
            vo.setCoachName(coach.getRealName());
            vo.setCoachPhone(coach.getPhone());
        }
        return vo;
    }

    /** 用户（教练）实体 → 教练简要信息。 */
    private CoachVO toCoachVO(User coach) {
        CoachVO vo = new CoachVO();
        vo.setCoachId(coach.getId());
        vo.setRealName(coach.getRealName());
        vo.setPhone(coach.getPhone());
        return vo;
    }
}
