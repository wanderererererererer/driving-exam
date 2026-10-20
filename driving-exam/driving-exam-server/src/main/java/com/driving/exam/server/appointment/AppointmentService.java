package com.driving.exam.server.appointment;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AppointmentCreateDTO;
import com.driving.exam.pojo.enums.AppointmentStatus;
import com.driving.exam.pojo.vo.AppointmentVO;
import com.driving.exam.pojo.vo.CoachVO;

import java.util.List;

/**
 * 预约 Service（学员端 + 教练端）。
 *
 * <p>只服务科二 / 科三：学员向<b>自己已绑定且绑定有效（{@code coach_student.status = 1}）</b>的教练
 * 发起练车预约，教练审批（通过 / 拒绝），完成后标记。</p>
 *
 * <p>差异于 {@code QuestionService} 等实体 Service：本接口是**业务流程 Service**，
 * 不继承 {@code IService}。</p>
 *
 * <p><b>错误码约定</b>（详见各方法）：{@code APPOINTMENT_NOT_FOUND(3001)} 预约不存在或不属于当前用户
 * （越权与不存在**返回同一错误码**，不泄露存在性）；{@code APPOINTMENT_TIME_CONFLICT(3002)} 该时段已被预约；
 * {@code STUDENT_NOT_BOUND(3003)} 该教练不是本人有效绑定的教练；{@code APPOINTMENT_CANCEL_NOT_ALLOWED(3004)}
 * 仅可取消待确认的预约；{@code APPOINTMENT_STATUS_ILLEGAL(3005)} 当前状态不允许该操作。</p>
 *
 * <p><b>事务</b>：五个写方法（{@code create / cancel / approve / reject / complete}）都是**单表单条写**，
 * 按项目惯例<b>不加 {@code @Transactional}</b>（单条 SQL 自身原子，与刷题作答、AI 落库一致）；
 * 只读方法也不加。</p>
 */
public interface AppointmentService {

    /* ============================== 学员端 ============================== */

    /**
     * 我的教练列表（学员端）。
     *
     * <p>取该学员**有效绑定**（{@code coach_student.status = 1}）的教练，关联 {@code sys_user} 补姓名与电话。
     * <b>未绑定任何教练时返回空数组</b>（这不是错误，不抛 3003）。只读，不加事务。</p>
     *
     * @param studentId 当前登录学员（取 JWT）
     */
    List<CoachVO> listMyCoaches(Long studentId);

    /**
     * 创建预约（学员端）。
     *
     * <p>校验与副作用：① 该教练必须是本人**有效绑定**的教练，否则 {@code STUDENT_NOT_BOUND(3003)}；
     * ② 该教练该日该时段不能已有**占用中**的预约（占用 = {@code status IN (PENDING, CONFIRMED)}），
     * 否则 {@code APPOINTMENT_TIME_CONFLICT(3002)}——注意终态（取消 / 拒绝 / 完成）**释放时段**。
     * 新建记录状态为 {@code PENDING}。</p>
     *
     * <p><b>单表单条写，不涉及跨表写，故不加 {@code @Transactional}</b>。
     * （"先查冲突、再插入"存在并发竞态：无唯一键时两个并发请求可能都通过——课设不处理，已记录在案。）</p>
     *
     * @param studentId 当前登录学员（取 JWT）
     * @param dto       教练、科目、日期、时段、备注；科目限科二 / 科三，时段限固定四项，日期不得早于今天
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code STUDENT_NOT_BOUND(3003)} 该教练不是本人有效绑定的教练；
     *         {@code APPOINTMENT_TIME_CONFLICT(3002)} 该时段已被预约
     */
    void createAppointment(Long studentId, AppointmentCreateDTO dto);

    /**
     * 我的预约列表（学员端，分页）。
     *
     * <p>只返回**本人**的预约（{@code student_id = 当前学员}），可按状态、科目过滤，
     * 按创建时间倒序。姓名与电话一次 {@code IN} 查 {@code sys_user} 内存映射（防 N+1）。只读，不加事务。</p>
     *
     * <p><b>⚠️ {@code status} 大小写敏感</b>：由 Spring 走 {@code Enum.valueOf()} 转换
     * （{@code StringToEnumConverterFactory}，<b>只去掉首尾空格、不改大小写</b>）——前端必须传
     * {@code PENDING / CONFIRMED / CANCELLED / REJECTED / COMPLETED} 的**全大写**枚举名；
     * 传小写（或 `" pending "`）会触发 {@code MethodArgumentTypeMismatchException} → 全局处理器 → {@code 1001}。
     * 这是**刻意的严格校验，不是 bug**。为 {@code null} 表示不过滤。</p>
     *
     * @param studentId   当前登录学员（取 JWT）
     * @param status      状态过滤；为 {@code null} 表示全部（取值见上）
     * @param subjectCode 科目过滤；为 {@code null} 表示全部
     * @param pageSize    后端会强制上限（默认 20）
     */
    PageResult<AppointmentVO> listMyAppointments(Long studentId, AppointmentStatus status,
                                                 String subjectCode, int pageNum, int pageSize);

    /**
     * 预约详情（学员端）。
     *
     * <p>只读，不加事务。越权（查别人的）与不存在**返回同一个 3001**。</p>
     *
     * @param studentId     当前登录学员（取 JWT）
     * @param appointmentId 预约 ID
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code APPOINTMENT_NOT_FOUND(3001)} 预约不存在或不属于当前学员
     */
    AppointmentVO getMyAppointment(Long studentId, Long appointmentId);

    /**
     * 取消预约（学员端）。
     *
     * <p><b>仅 {@code PENDING} 允许取消</b>（{@code CONFIRMED} 之后需联系教练，见
     * {@code doc/api-design.md} §5 第 2 条）；状态不符抛 {@code APPOINTMENT_CANCEL_NOT_ALLOWED(3004)}。
     * 取消后状态置 {@code CANCELLED}，该时段<b>释放</b>（可被重新预约）。</p>
     *
     * <p>单表单条写，不涉及跨表写，故不加 {@code @Transactional}。</p>
     *
     * @param studentId     当前登录学员（取 JWT）
     * @param appointmentId 预约 ID
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code APPOINTMENT_NOT_FOUND(3001)} 预约不存在或不属于当前学员；
     *         {@code APPOINTMENT_CANCEL_NOT_ALLOWED(3004)} 当前状态不是 PENDING
     */
    void cancelMyAppointment(Long studentId, Long appointmentId);

    /* ============================== 教练端 ============================== */

    /**
     * 我的预约列表（教练端，分页）。
     *
     * <p>只返回**本人名下**的预约（{@code coach_id = 当前教练}），可按状态过滤，按创建时间倒序。
     * <b>不提供科目筛选</b>（与 {@code doc/api-design.md} §4.10 一致，那里只写"可按状态筛"）。只读，不加事务。</p>
     *
     * <p><b>⚠️ {@code status} 大小写敏感</b>：同 {@link #listMyAppointments}，必须传全大写枚举名，
     * 否则 {@code 1001}。</p>
     *
     * @param coachId  当前登录教练（取 JWT）
     * @param status   状态过滤；为 {@code null} 表示全部
     * @param pageSize 后端会强制上限（默认 20）
     */
    PageResult<AppointmentVO> listCoachAppointments(Long coachId, AppointmentStatus status,
                                                    int pageNum, int pageSize);

    /**
     * 管理员端 · 全量预约只读列表（分页）。
     *
     * <p>仅管理员路径 `/api/admin/appointments` 使用；不按学员/教练身份收窄。
     * 可按状态过滤，按创建时间倒序；姓名/电话一次 IN 装配。只读，不加事务。</p>
     *
     * @param status   状态过滤；null 表示全部
     * @param pageNum  页码（小于 1 按 1）
     * @param pageSize 每页条数（后端上限 20）
     */
    PageResult<AppointmentVO> listAllAppointments(AppointmentStatus status, int pageNum, int pageSize);

    /**
     * 审批通过（教练端）。
     *
     * <p><b>仅 {@code PENDING} 可审批</b>；状态不符抛 {@code APPOINTMENT_STATUS_ILLEGAL(3005)}。
     * 通过后状态置 {@code CONFIRMED}。</p>
     *
     * <p>单表单条写，不涉及跨表写，故不加 {@code @Transactional}。</p>
     *
     * @param coachId       当前登录教练（取 JWT）
     * @param appointmentId 预约 ID
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code APPOINTMENT_NOT_FOUND(3001)} 预约不存在或不属于当前教练；
     *         {@code APPOINTMENT_STATUS_ILLEGAL(3005)} 当前状态不是 PENDING
     */
    void approveAppointment(Long coachId, Long appointmentId);

    /**
     * 拒绝预约（教练端）。
     *
     * <p><b>仅 {@code PENDING} 可拒绝</b>；状态不符抛 {@code APPOINTMENT_STATUS_ILLEGAL(3005)}。
     * 拒绝后状态置 {@code REJECTED}（<b>独立状态</b>，与学员主动取消的 {@code CANCELLED} 区分），
     * 该时段<b>释放</b>；拒绝原因写入 {@code coach_remark}。</p>
     *
     * <p>单表单条写，不涉及跨表写，故不加 {@code @Transactional}。</p>
     *
     * @param coachId       当前登录教练（取 JWT）
     * @param appointmentId 预约 ID
     * @param coachRemark   拒绝原因；<b>可为 {@code null}</b>（后端不强校验，仅设长度上限 255）
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code APPOINTMENT_NOT_FOUND(3001)} 预约不存在或不属于当前教练；
     *         {@code APPOINTMENT_STATUS_ILLEGAL(3005)} 当前状态不是 PENDING
     */
    void rejectAppointment(Long coachId, Long appointmentId, String coachRemark);

    /**
     * 标记完成（教练端）。
     *
     * <p><b>仅 {@code CONFIRMED} 可完成</b>；状态不符抛 {@code APPOINTMENT_STATUS_ILLEGAL(3005)}。
     * 完成后状态置 {@code COMPLETED}（终态）。</p>
     *
     * <p>单表单条写，不涉及跨表写，故不加 {@code @Transactional}。</p>
     *
     * @param coachId       当前登录教练（取 JWT）
     * @param appointmentId 预约 ID
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code APPOINTMENT_NOT_FOUND(3001)} 预约不存在或不属于当前教练；
     *         {@code APPOINTMENT_STATUS_ILLEGAL(3005)} 当前状态不是 CONFIRMED
     */
    void completeAppointment(Long coachId, Long appointmentId);
}
