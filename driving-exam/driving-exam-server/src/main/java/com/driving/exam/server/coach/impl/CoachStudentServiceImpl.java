package com.driving.exam.server.coach.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.StudentBindDTO;
import com.driving.exam.pojo.dto.StudentUpdateDTO;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.StudentSubjectStatus;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.vo.StudentVO;
import com.driving.exam.pojo.vo.SubjectStatusVO;
import com.driving.exam.server.coach.CoachStudentService;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.StudentSubjectStatusMapper;
import com.driving.exam.server.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 教练端 · 学员管理 Service 实现。
 *
 * <h3>三条贯穿全类的原则</h3>
 * <ol>
 *   <li><b>先归属、再操作</b>：所有按 {@code bindId} 的操作，先把 id 归约为「属于当前教练且
 *       {@code status = 1}」的一条绑定记录（{@link #loadOwnedActive}），查不到<b>一律</b>抛
 *       {@code BINDING_NOT_FOUND(3102)}——不存在 / 不是我的 / 已解绑<b>三者同一响应</b>，
 *       不泄露"该学员确实存在"。{@code studentId} 只能由这条记录反查，<b>绝不采信前端传参</b>。</li>
 *   <li><b>防 N+1</b>：列表先分页查出绑定记录，再收集去重后的 {@code studentId}，
 *       <b>一次 {@code IN} 查 {@code sys_user}</b> 后内存装配；空集合直接返回空 Map
 *       （{@code IN ()} 是 MySQL 语法错误）。</li>
 *   <li><b>不无依据地加事务</b>：只有 {@link #bindOrCreateStudent} 跨表写（{@code sys_user} +
 *       {@code coach_student}），需要 {@code @Transactional}；改科目阶段是单条原子 upsert、
 *       解绑是单表单条写，均不加——与预约模块、{@code ExamFinalizer} 的判据一致。</li>
 * </ol>
 *
 * <p><b>{@code student_subject_status} 的写入边界</b>：本类只写 {@code status} 与 {@code coach_remark}
 * 两列（走 {@code upsertCoachFields}）。{@code mock_pass_count} / {@code last_mock_score} 由
 * {@code ExamFinalizer} 在交卷时维护，本类<b>不得覆盖</b>；{@code study_progress} 等列当前无写入方。</p>
 *
 * <p>设计依据：{@code doc/coach-student-design.md}（Step 0 定稿）。</p>
 */
@Service
public class CoachStudentServiceImpl implements CoachStudentService {

    /** 分页每页默认条数（非法 pageSize 时使用） */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 分页每页上限（防止前端传大值一次拉太多），与预约模块一致 */
    private static final int MAX_PAGE_SIZE = 20;

    /** 绑定"有效"状态（{@code coach_student.status}）：1 有效 / 0 解绑 */
    private static final int BIND_STATUS_ACTIVE = 1;

    /** 绑定"已解绑"状态：解绑 = 置 0，不删行 */
    private static final int BIND_STATUS_RELEASED = 0;

    /** 学员角色（{@code sys_user.role}）：本接口只允许绑定该角色 */
    private static final String ROLE_STUDENT = "STUDENT";

    /** 账号"正常"状态（{@code sys_user.status}）：1 正常 / 0 禁用 */
    private static final int USER_STATUS_NORMAL = 1;

    /** 学员状态详情固定返回的四科（顺序即返回顺序） */
    private static final List<String> SUBJECT_CODES =
            List.of("SUBJECT_1", "SUBJECT_2", "SUBJECT_3", "SUBJECT_4");

    /** 科目进度缺失时的默认学习阶段（与数据库列默认值一致） */
    private static final String STATUS_NOT_STARTED = "NOT_STARTED";

    private final CoachStudentMapper coachStudentMapper;
    private final UserMapper userMapper;
    private final StudentSubjectStatusMapper studentSubjectStatusMapper;
    private final PasswordEncoder passwordEncoder;

    public CoachStudentServiceImpl(CoachStudentMapper coachStudentMapper,
                                   UserMapper userMapper,
                                   StudentSubjectStatusMapper studentSubjectStatusMapper,
                                   PasswordEncoder passwordEncoder) {
        this.coachStudentMapper = coachStudentMapper;
        this.userMapper = userMapper;
        this.studentSubjectStatusMapper = studentSubjectStatusMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /* ============================== 1. 我的学员列表 ============================== */

    @Override
    public PageResult<StudentVO> listMyStudents(Long coachId, int pageNum, int pageSize) {
        int current = Math.max(pageNum, 1);
        int size = (pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<CoachStudent> page = coachStudentMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<CoachStudent>()
                        .eq(CoachStudent::getCoachId, coachId)      // 越权收敛：只看自己名下
                        .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE)  // 已解绑的学员不出现
                        // 排序：最近绑定的在最前；id 作次级键 —— bind_time 是秒精度，
                        // 同秒记录靠它保证全序稳定，避免分页漂移（与预约模块同一口径）。
                        .orderByDesc(CoachStudent::getBindTime)
                        .orderByDesc(CoachStudent::getId));

        // 防 N+1：一页绑定记录 → 一次 IN 查 sys_user → 内存装配
        // （page.getRecords() 为空时 loadUserMap 直接返回空 Map，不会拼出 IN ()）
        Map<Long, User> userMap = loadUserMap(
                page.getRecords().stream().map(CoachStudent::getStudentId).collect(Collectors.toList()));
        List<StudentVO> records = page.getRecords().stream()
                .map(bind -> toStudentVO(bind, userMap))
                .collect(Collectors.toList());

        // 未绑定任何学员时，这里得到 total=0、records=[] 的正常 PageResult —— 不是错误。
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    /* ========================= 2. 绑定或创建学员 ========================= */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindOrCreateStudent(Long coachId, StudentBindDTO dto) {
        // ① 按用户名找账号（User 带 @TableLogic，逻辑删除的账号查不出来）
        User student = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));

        if (student == null) {
            // ② 不存在 → 创建学员账号。创建模式的四项必填在此手写判空（不用 Bean Validation 分组，
            //    因为本 DTO 同时服务"绑定已有学员"模式，那三个字段在绑定时是可以不传的）。
            //    注：手机号的"大陆格式"约束已在 StudentBindDTO 上用 @Pattern 声明，
            //    由 Controller 的 @Valid 把关，此处不重复校验。
            if (!StringUtils.hasText(dto.getRealName())
                    || !StringUtils.hasText(dto.getPhone())
                    || !StringUtils.hasText(dto.getPassword())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }

            student = new User();
            student.setUsername(dto.getUsername());
            // 复用项目既有编码器（SecurityConfig 中的 BCryptPasswordEncoder）；
            // 明文密码不落库、不返回、不写日志。
            student.setPassword(passwordEncoder.encode(dto.getPassword()));
            student.setRealName(dto.getRealName());
            student.setPhone(dto.getPhone());
            student.setRole(ROLE_STUDENT);
            student.setStatus(USER_STATUS_NORMAL);
            // deleted / create_time / update_time 无需手动赋值：
            // 前者由数据库默认值落 0（insert 策略为默认 NOT_NULL，null 字段不参与 INSERT），
            // 后两者由 MyMetaObjectHandler 自动填充。
            userMapper.insert(student);
        } else if (!ROLE_STUDENT.equals(student.getRole())) {
            // ③ 账号已存在但不是学员 → 不允许被"绑成"学员
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }

        // ④ 绑定：先看这对 (coach_id, student_id) 是否已有记录（唯一键 uk_coach_student 保证至多一条）
        CoachStudent existing = coachStudentMapper.selectOne(new LambdaQueryWrapper<CoachStudent>()
                .eq(CoachStudent::getCoachId, coachId)
                .eq(CoachStudent::getStudentId, student.getId()));

        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == BIND_STATUS_ACTIVE) {
                throw new BusinessException(ErrorCode.STUDENT_ALREADY_BOUND);
            }
            // 历史解绑记录 → 复用该行：恢复有效并刷新绑定时间（不可插新行，否则撞唯一键）
            existing.setStatus(BIND_STATUS_ACTIVE);
            existing.setBindTime(LocalDateTime.now());
            coachStudentMapper.updateById(existing);
            return;
        }

        CoachStudent bind = new CoachStudent();
        bind.setCoachId(coachId);
        bind.setStudentId(student.getId());
        bind.setStatus(BIND_STATUS_ACTIVE);
        // bind_time 不赋值：该列有数据库默认值 CURRENT_TIMESTAMP，
        // 且 MyMetaObjectHandler 按字段名只填 createTime / updateTime（见 CoachStudent 实体说明）。
        coachStudentMapper.insert(bind);
    }

    /* ==================== 3. 改学员科目阶段 / 备注 ==================== */

    @Override
    public void updateStudentSubject(Long coachId, Long bindId, StudentUpdateDTO dto) {
        // 空白串按"未传"处理：与 upsert 的 COALESCE 语义保持一致（空串不会被写进去）
        String status = StringUtils.hasText(dto.getPhaseStatus()) ? dto.getPhaseStatus() : null;
        String coachRemark = StringUtils.hasText(dto.getCoachRemark()) ? dto.getCoachRemark() : null;
        if (status == null && coachRemark == null) {
            // 两个可选字段一个都没传 → 本次请求没有任何修改内容
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // 先归属、后写入：0 行（不存在 / 不是我的 / 已解绑）统一 3102
        CoachStudent bind = loadOwnedActive(coachId, bindId);

        // 单条原子 upsert（照 MockExamAnswerMapper / WrongBookMapper 的既有写法）：
        // 目标行不存在则创建，已存在则只改本次传入的非空字段；单条 SQL 自身原子，故不加事务。
        studentSubjectStatusMapper.upsertCoachFields(
                bind.getStudentId(), dto.getSubjectCode(), status, coachRemark);
    }

    /* ============================== 4. 解绑 ============================== */

    @Override
    public void unbindStudent(Long coachId, Long bindId) {
        CoachStudent bind = loadOwnedActive(coachId, bindId);   // 先归属
        bind.setStatus(BIND_STATUS_RELEASED);
        // 软解绑：只置 status=0。不级联取消预约、不删除任何历史数据
        //（解绑后教练端不再可见，但历史记录与学员本人的功能不受影响）。
        coachStudentMapper.updateById(bind);
    }

    /* ========================= 5. 学员状态详情 ========================= */

    @Override
    public List<SubjectStatusVO> listStudentStatus(Long coachId, Long bindId) {
        CoachStudent bind = loadOwnedActive(coachId, bindId);   // 先归属
        Long studentId = bind.getStudentId();

        // 一次查出该学员已有的科目进度行（可能 0~4 条），内存按科目建索引
        List<StudentSubjectStatus> rows = studentSubjectStatusMapper.selectList(
                new LambdaQueryWrapper<StudentSubjectStatus>()
                        .eq(StudentSubjectStatus::getStudentId, studentId));
        Map<String, StudentSubjectStatus> bySubject = rows.stream()
                .collect(Collectors.toMap(StudentSubjectStatus::getSubjectCode,
                        Function.identity(), (a, b) -> a));

        // 固定补齐四科：库中缺失的科目在**内存里**按默认值补 NOT_STARTED —— 只读接口绝不写库。
        List<SubjectStatusVO> list = new ArrayList<>(SUBJECT_CODES.size());
        for (String subjectCode : SUBJECT_CODES) {
            StudentSubjectStatus row = bySubject.get(subjectCode);
            SubjectStatusVO vo = new SubjectStatusVO();
            vo.setSubjectCode(subjectCode);
            vo.setStatus(row == null || row.getStatus() == null ? STATUS_NOT_STARTED : row.getStatus());
            vo.setMockPassCount(row == null || row.getMockPassCount() == null ? 0 : row.getMockPassCount());
            vo.setLastMockScore(row == null ? null : row.getLastMockScore());
            list.add(vo);
        }
        return list;
    }

    /* ============================== 私有工具 ============================== */

    /**
     * 归属校验：把 {@code bindId} 归约为「属于当前教练且仍有效」的一条绑定记录。
     *
     * <p>{@code WHERE id = ? AND coach_id = ? AND status = 1}，0 行即抛 {@code 3102}——
     * "不存在 / 不是我的 / 已解绑"三种情况<b>返回同一响应</b>，不泄露其他教练的绑定信息。</p>
     */
    private CoachStudent loadOwnedActive(Long coachId, Long bindId) {
        CoachStudent bind = coachStudentMapper.selectOne(new LambdaQueryWrapper<CoachStudent>()
                .eq(CoachStudent::getId, bindId)
                .eq(CoachStudent::getCoachId, coachId)
                .eq(CoachStudent::getStatus, BIND_STATUS_ACTIVE));
        if (bind == null) {
            throw new BusinessException(ErrorCode.BINDING_NOT_FOUND);
        }
        return bind;
    }

    /**
     * <b>防 N+1 的关键</b>：按去重后的用户 ID 集合，一次 {@code IN} 查回 {@code sys_user}，
     * 组装成 {@code Map<id, User>} 供内存映射。
     *
     * <p>空集合直接返回空 Map——{@code selectBatchIds(空)} 会拼出 {@code WHERE id IN ()}，
     * 在 MySQL 是语法错误（与 {@code QuestionOptionAssembler.loadByQuestionIds} 同样的防御）。</p>
     *
     * <p>注意：{@code User} 带 {@code @TableLogic}，被逻辑删除的账号查不到 → 调用方必须容忍
     * {@code Map} 里没有对应 key，<b>绝不 NPE</b>。</p>
     */
    private Map<Long, User> loadUserMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    /**
     * 绑定记录 + 用户信息 → {@code StudentVO}。
     *
     * <p><b>{@code bindId} 取自绑定记录自身的主键（{@code coach_student.id}）</b>，
     * 与 {@code studentId}（{@code coach_student.student_id}）<b>是两个不同的 id</b>：
     * 前者供教练端后续三个按 id 操作的接口（PUT / DELETE / status）使用，
     * 后者指向学员用户。切勿混用。</p>
     *
     * <p>姓名 / 电话取不到就留 {@code null}（对方账号被逻辑删除时，绑定记录仍在、姓名留空）——
     * 与预约模块"历史记录要完整"的既定行为一致。</p>
     */
    private StudentVO toStudentVO(CoachStudent bind, Map<Long, User> userMap) {
        StudentVO vo = new StudentVO();
        vo.setBindId(bind.getId());                 // ← 绑定记录 ID（coach_student.id）
        vo.setStudentId(bind.getStudentId());       // ← 学员用户 ID（sys_user.id）
        vo.setBindTime(bind.getBindTime());

        User student = userMap.get(bind.getStudentId());
        if (student != null) {
            vo.setUsername(student.getUsername());
            vo.setRealName(student.getRealName());
            vo.setPhone(student.getPhone());
        }
        return vo;
    }
}
