package com.driving.exam.server.coach;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.StudentBindDTO;
import com.driving.exam.pojo.dto.StudentUpdateDTO;
import com.driving.exam.pojo.vo.StudentVO;
import com.driving.exam.pojo.vo.SubjectStatusVO;

import java.util.List;

/**
 * 教练端 · 学员管理 Service（{@code /api/coach/students}）。
 *
 * <p>教练对自己<b>名下有效绑定</b>（{@code coach_student.status = 1}）的学员进行：查看列表、
 * 绑定或创建学员、改科目阶段 / 备注、解绑、查看各科状态详情。</p>
 *
 * <p>差异于 {@code QuestionService} 等实体 Service：本接口是**业务流程 Service**，
 * 不继承 {@code IService}（与 {@code AppointmentService} / {@code ExamService} 同类）。</p>
 *
 * <p><b>★ 越权口径（全接口统一）</b>：所有针对具体学员的操作，都先把路径里的 {@code id}
 * 归约为「<b>属于当前教练且仍有效</b>」的一条绑定记录
 * （{@code WHERE coach_student.id = ? AND coach_student.coach_id = ? AND coach_student.status = 1}）。
 * 查不到（不存在 / 不是我的 / 已解绑）<b>一律抛同一个错误码</b>，不区分、不泄露"该学员真实存在"。
 * <b>{@code studentId} 只能由该绑定记录反查得到，绝不接受前端传入</b>——这是防止跨教练越权读取学情的关口。</p>
 *
 * <p><b>错误码约定</b>（详见各方法）：{@code PARAM_ERROR(1001)} 参数错误（创建模式必填项缺失、
 * 两个可选字段都没传）；{@code ROLE_MISMATCH(1106)} 目标账号不是学员；{@code USERNAME_EXISTS(1105)}
 * 并发建号撞唯一键；{@code STUDENT_ALREADY_BOUND(3101)} 该学员已绑定；{@code BINDING_NOT_FOUND(3102)}
 * 绑定关系不存在或不属于当前教练。<b>本模块不新增错误码。</b></p>
 *
 * <p><b>事务</b>：只有 {@link #bindOrCreateStudent} 需要 {@code @Transactional}
 * （跨表写 {@code sys_user} + {@code coach_student}，避免"账号建了但绑定失败"产生孤儿账号）；
 * 其余方法均为单表单条写或只读，按项目惯例不加（`UPDATE ... ON DUPLICATE KEY` 单条 SQL 自身原子）。</p>
 *
 * <p>设计依据：{@code doc/coach-student-design.md}（Step 0 定稿）。</p>
 */
public interface CoachStudentService {

    /* ============================== 学员管理 ============================== */

    /**
     * 我的学员列表（教练端，分页）。
     *
     * <p>只返回 <b>本人名下且仍有效</b>的绑定（{@code coach_id = 当前教练 AND status = 1}）。
     * 排序 {@code bind_time DESC, id DESC}（次级键保证分页稳定）。</p>
     *
     * <p><b>防 N+1</b>：先分页查出绑定记录，再收集去重后的 {@code studentId}，
     * <b>一次 {@code IN} 查 {@code sys_user}</b> 后内存装配姓名与电话——绝不循环里逐条查库；
     * <b>空集合直接返回空分页结果</b>（{@code IN ()} 在 MySQL 是语法错误）。</p>
     *
     * <p>未绑定任何学员时返回 {@code total = 0} 的 {@code PageResult}，<b>这不是错误</b>。
     * 学员账号被逻辑删除时<b>保留记录、姓名电话留 {@code null}</b>（历史记录要完整）。</p>
     *
     * @param coachId  当前登录教练（取 JWT）
     * @param pageNum  页码，从 1 开始（小于 1 时按 1 处理）
     * @param pageSize 每页条数（后端强制上限 20，非法值取默认 10）
     */
    PageResult<StudentVO> listMyStudents(Long coachId, int pageNum, int pageSize);

    /**
     * 绑定或创建学员（教练端）。
     *
     * <p><b>同一入口、两种模式</b>，由 {@code username} 是否已存在于 {@code sys_user} 分流：</p>
     * <ul>
     *   <li><b>账号已存在</b>：若其 {@code role != STUDENT} → {@code ROLE_MISMATCH(1106)}
     *       （不得把教练 / 管理员"绑成"学员）；若与本教练的有效绑定已存在 →
     *       {@code STUDENT_ALREADY_BOUND(3101)}；否则建立绑定。</li>
     *   <li><b>账号不存在</b>：以入参创建 {@code sys_user}（{@code role = STUDENT}、{@code status = 1}、
     *       密码用项目现有 {@code PasswordEncoder}（BCrypt）加密；{@code deleted} 与时间戳由框架 / 数据库默认处理），
     *       再建立绑定。此时 {@code username / realName / phone / password} <b>四项必填</b>，
     *       缺失任一则抛 {@code PARAM_ERROR(1001)}。</li>
     * </ul>
     *
     * <p><b>重复绑定的处理</b>：若存在同 {@code (coach_id, student_id)} 的<b>历史解绑记录</b>
     * （{@code status = 0}），<b>复用该行</b>：置 {@code status = 1} 并<b>刷新 {@code bind_time}</b>——
     * 不可插入新行，否则会被唯一键 {@code uk_coach_student} 拒绝。</p>
     *
     * <p><b>密码安全</b>：不返回明文、不写日志。</p>
     *
     * <p><b>事务</b>：本方法跨表写 {@code sys_user} 与 {@code coach_student}，<b>加
     * {@code @Transactional(rollbackFor = Exception.class)}</b>（由实现类标注）。</p>
     *
     * @param coachId 当前登录教练（取 JWT）
     * @param dto     学员登录名 + （创建模式下的）姓名 / 手机号 / 初始密码
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code PARAM_ERROR(1001)} 创建模式必填项缺失；
     *         {@code ROLE_MISMATCH(1106)} 目标账号不是学员；
     *         {@code STUDENT_ALREADY_BOUND(3101)} 该学员已绑定本教练；
     *         {@code USERNAME_EXISTS(1105)} 并发建号撞 {@code uk_username}
     */
    void bindOrCreateStudent(Long coachId, StudentBindDTO dto);

    /**
     * 修改学员的科目阶段 / 教练备注（教练端）。
     *
     * <p><b>落点说明</b>：{@code coach_student} 表没有"备注"与"科目阶段"列，故本操作写入的是
     * {@code student_subject_status}（按反查出的 {@code studentId} + 入参 {@code subjectCode} 定位）。</p>
     *
     * <p><b>校验顺序（不可颠倒）</b>：先做归属校验（{@code id + coach_id + status = 1}，0 行 →
     * {@code BINDING_NOT_FOUND(3102)}），<b>再</b>以反查出的 {@code studentId} 写库。</p>
     *
     * <p><b>写入语义</b>：{@code phaseStatus} 与 {@code coachRemark} 均为可选，但<b>至少要传一个</b>，
     * 两个都不传抛 {@code PARAM_ERROR(1001)}。实现侧用
     * {@code INSERT ... ON DUPLICATE KEY UPDATE} + {@code COALESCE}（照 {@code MockExamAnswerMapper} 的既有写法）：
     * 目标行不存在则创建，已存在则只改传入的字段；<b>传 {@code null} 表示不修改</b>，
     * 因此<b>暂不支持把备注清空为 {@code null}</b>（本次拍板接受，见设计文档 §八 Q12）。
     * 单条 SQL 自身原子，<b>不加 {@code @Transactional}</b>。</p>
     *
     * @param coachId 当前登录教练（取 JWT）
     * @param bindId  绑定记录 ID（{@code coach_student.id}，<b>不是</b> {@code studentId}）
     * @param dto     科目（必填）+ 学习阶段 / 教练备注（可选，至少一个）
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code PARAM_ERROR(1001)} 两个可选字段都没传或取值非法；
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑
     */
    void updateStudentSubject(Long coachId, Long bindId, StudentUpdateDTO dto);

    /**
     * 解绑学员（教练端）。
     *
     * <p>只把绑定记录的 {@code status} 置为 {@code 0}，<b>不删除任何历史数据</b>
     * （预约、答题记录、错题、模考、科目进度、AI 记录全部保留），也<b>不联动取消未完成的预约</b>。
     * 解绑后教练端不可再访问该学员（后续对同一 id 的操作返回 {@code BINDING_NOT_FOUND(3102)}），
     * 但学员本人仍可正常登录与使用自己的功能。</p>
     *
     * <p>单表单条写，<b>不加 {@code @Transactional}</b>。</p>
     *
     * @param coachId 当前登录教练（取 JWT）
     * @param bindId  绑定记录 ID（{@code coach_student.id}）
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑
     */
    void unbindStudent(Long coachId, Long bindId);

    /**
     * 学员状态详情（教练端）——固定返回四科。
     *
     * <p>先做归属校验（{@code id + coach_id + status = 1}，0 行 → {@code BINDING_NOT_FOUND(3102)}），
     * 再以反查出的 {@code studentId} 查 {@code student_subject_status}。</p>
     *
     * <p><b>四科补齐</b>：固定返回 {@code SUBJECT_1}~{@code SUBJECT_4} 共 4 项；库中缺失的科目
     * <b>在内存中按默认值补齐</b>（{@code status = "NOT_STARTED"}、{@code mockPassCount = 0}、
     * {@code lastMockScore = null}）。<b>★ 查询操作绝不写库</b>——不得为了"补齐展示数据"而插入行。</p>
     *
     * <p>只读方法，<b>不加 {@code @Transactional}</b>。</p>
     *
     * @param coachId 当前登录教练（取 JWT）
     * @param bindId  绑定记录 ID（{@code coach_student.id}）
     * @return 长度为 4 的列表（{@code SUBJECT_1}~{@code SUBJECT_4}，顺序固定）
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑
     */
    List<SubjectStatusVO> listStudentStatus(Long coachId, Long bindId);
}
