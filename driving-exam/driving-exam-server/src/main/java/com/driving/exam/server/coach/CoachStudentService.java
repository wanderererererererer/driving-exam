package com.driving.exam.server.coach;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.StudentBindDTO;
import com.driving.exam.pojo.dto.StudentUpdateDTO;
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.StudentVO;
import com.driving.exam.pojo.vo.SubjectStatusVO;
import com.driving.exam.pojo.vo.WrongBookItemVO;

import java.util.List;

/**
 * 教练端 · 学员管理 Service（{@code /api/coach/students}）。
 *
 * <p>教练对自己<b>名下有效绑定</b>（{@code coach_student.status = 1}）的学员进行：查看列表、
 * 绑定或创建学员、改科目阶段 / 备注、解绑、查看各科状态详情、查看学习数据、查看错题数据。</p>
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

    /**
     * 学员学习数据（教练端，只读）。
     *
     * <p>先按 {@code coach_student.id + coach_id + status = 1} 做归属校验；不存在 / 不属于当前教练 /
     * 已解绑一律 {@code BINDING_NOT_FOUND(3102)}。{@code studentId} 只能从该绑定记录反查。</p>
     *
     * <p>统计口径全部复用既有 Service：</p>
     * <ul>
     *   <li>总览/正确率/薄弱知识点 → {@code AnalysisService}；</li>
     *   <li>错题分类数量 → {@code WrongBookService.summary}；</li>
     *   <li>已结束模拟考试 → {@code ExamService.history}（分页与排序沿用考试历史接口）。</li>
     * </ul>
     *
     * <p>既有分析/考试历史接口均按科目统计，因此 {@code subjectCode} 必填；
     * 取值必须为 SUBJECT_1~SUBJECT_4，否则 {@code PARAM_ERROR(1001)}。本方法只读，不加事务。</p>
     *
     * @param coachId    当前登录教练（取 JWT，不接受前端传参）
     * @param bindId     绑定记录 ID（{@code coach_student.id}）
     * @param subjectCode 科目编码（SUBJECT_1~SUBJECT_4）
     * @param pageNum    模拟考试历史页码（从 1 开始）
     * @param pageSize   模拟考试历史每页条数
     * @return 学员学习数据；无考试/错题/薄弱知识点时返回空值或空列表
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑；
     *         {@code PARAM_ERROR(1001)} subjectCode 非法
     */
    LearningDataVO getLearningData(Long coachId, Long bindId, String subjectCode,
                                   int pageNum, int pageSize);

    /**
     * 学员错题数据（教练端，只读，分页）。
     *
     * <p>先按 {@code coach_student.id + coach_id + status = 1} 做归属校验；不存在 / 不属于当前教练 /
     * 已解绑一律 {@code BINDING_NOT_FOUND(3102)}，且不会调用错题查询服务。{@code studentId}
     * 只能从该绑定记录反查，不接受前端传入。</p>
     *
     * <p>列表、分类口径、排序全部复用 {@code WrongBookService.list}，等价于学员端
     * {@code GET /api/student/wrong-book}；本方法只读，不修改任何错题记录。</p>
     *
     * <p>分页按教练端既有规则归一化：页码最小 1，每页默认 10、上限 20。</p>
     *
     * @param coachId     当前登录教练（取 JWT，不接受前端传参）
     * @param bindId      绑定记录 ID（{@code coach_student.id}）
     * @param subjectCode 可选科目过滤；null/空白表示全部
     * @param category    可选分类：MASTERED / HIGH_FREQUENCY / PENDING；null 表示全部
     * @param pageNum     页码（小于 1 按 1）
     * @param pageSize    每页条数（小于 1 按 10，最大 20）
     * @return 错题分页；无数据时返回 total=0 的空分页，不是错误
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑
     */
    PageResult<WrongBookItemVO> listStudentWrongBook(Long coachId, Long bindId, String subjectCode,
                                                     WrongBookCategory category,
                                                     int pageNum, int pageSize);

    /**
     * 生成并保存学员 AI 学习建议（教练端）。
     *
     * <p>先按 {@code coach_student.id + coach_id + status = 1} 做归属校验；失败统一 3102，
     * 且不会调用 AI/分析/错题服务。建议按科目写入 {@code student_subject_status}：</p>
     * <ul>
     *   <li>规则建议 → {@code exam_ready_suggest}；</li>
     *   <li>AI 成功文本 → {@code ai_suggest} + {@code ai_suggest_time}；</li>
     *   <li>新建议生成时重置 {@code coach_confirm_status} 为 NULL。</li>
     * </ul>
     *
     * <p>AI 失败（4001/4002/4003）时返回规则建议，{@code source=RULE}、{@code aiSuccess=false}、
     * {@code degraded=true}，不会把规则文本写成 AI 成功结果。</p>
     *
     * @param coachId     当前登录教练（取 JWT，不接受前端传参）
     * @param bindId      绑定记录 ID（{@code coach_student.id}）
     * @param subjectCode 科目编码（SUBJECT_1~SUBJECT_4）
     * @return 建议内容与来源/降级标识
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑；
     *         {@code PARAM_ERROR(1001)} subjectCode 非法
     */
    AiSuggestVO generateAiSuggest(Long coachId, Long bindId, String subjectCode);

    /**
     * 读取已保存的学员学习建议（教练端，只读）。
     *
     * <p>先做绑定归属校验（失败统一 3102），再读取 {@code student_subject_status}：
     * 不调用 DeepSeek、不生成规则建议、不写入数据库、不重置确认状态。</p>
     *
     * <p>来源语义（数据库没有独立 source 列）：</p>
     * <ul>
     *   <li>{@code ai_suggest} 非空 → {@code source=AI}；</li>
     *   <li>{@code ai_suggest} 为空但 {@code exam_ready_suggest} 非空 → {@code source=RULE}；
     *       历史规则建议只保存了等级，完整文本未持久化，因此 {@code suggestion=null}；</li>
     *   <li>两者都为空 / 行不存在 → 返回 {@code null}，表示尚无已保存建议。</li>
     * </ul>
     *
     * <p>{@code coachConfirmStatus} 原样读取；本方法绝不修改它。</p>
     *
     * @param coachId     当前登录教练（取 JWT）
     * @param bindId      绑定记录 ID（{@code coach_student.id}）
     * @param subjectCode 科目编码（SUBJECT_1~SUBJECT_4）
     * @return 已保存建议；无建议时为 null
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑；
     *         {@code PARAM_ERROR(1001)} subjectCode 非法
     */
    AiSuggestVO getSavedAiSuggest(Long coachId, Long bindId, String subjectCode);

    /**
     * 教练独立确认/修改当前学习建议状态（教练端）。
     *
     * <p>先做绑定归属校验（失败统一 3102）。确认状态不依赖 AI/规则建议是否存在：
     * 没有建议行时会创建确认行；已有确认时允许修改为新的确认值。本操作只改
     * {@code coach_confirm_status}，不触发 AI、不修改建议文本/时间。</p>
     *
     * @param coachId       当前登录教练（取 JWT）
     * @param bindId        绑定记录 ID（{@code coach_student.id}）
     * @param subjectCode   科目编码（SUBJECT_1~SUBJECT_4）
     * @param confirmStatus CONFIRMED_READY / NOT_READY / NEED_HELP
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code BINDING_NOT_FOUND(3102)} 绑定不存在 / 不属于当前教练 / 已解绑；
     *         {@code PARAM_ERROR(1001)} subjectCode 或 confirmStatus 非法
     */
    void confirmSuggestion(Long coachId, Long bindId, String subjectCode, String confirmStatus);
}
