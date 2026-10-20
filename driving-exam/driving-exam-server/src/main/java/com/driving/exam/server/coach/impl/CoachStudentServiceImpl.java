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
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.StudentVO;
import com.driving.exam.pojo.vo.SubjectStatusVO;
import com.driving.exam.pojo.vo.WrongBookItemVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.coach.AiSuggestVO;
import com.driving.exam.server.coach.CoachStudentService;
import com.driving.exam.server.coach.LearningDataVO;
import com.driving.exam.server.exam.ExamService;
import com.driving.exam.server.integration.deepseek.DeepSeekClient;
import com.driving.exam.server.integration.deepseek.dto.ChatMessage;
import com.driving.exam.server.integration.deepseek.dto.ChatResult;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.StudentSubjectStatusMapper;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.service.WrongBookService;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Set;
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
 * <p><b>{@code student_subject_status} 的写入边界</b>：本类通过
 * {@code upsertCoachFields} 写 {@code status}/{@code coach_remark}，通过
 * {@code upsertAiSuggestion} 写建议字段，通过 {@code upsertCoachConfirm} 写确认状态。
 * {@code mock_pass_count} / {@code last_mock_score} 由 {@code ExamFinalizer} 在交卷时维护，
 * 本类<b>不得覆盖</b>；{@code study_progress} 等列当前无写入方。</p>
 *
 * <p>设计依据：{@code doc/coach-student-design.md}（Step 0 定稿）。</p>
 */
@Slf4j
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

    /** 系统建议值（student_subject_status.exam_ready_suggest） */
    private static final String SUGGEST_READY = "READY";
    private static final String SUGGEST_NEED_MORE = "NEED_MORE";
    private static final String SUGGEST_NEED_HELP = "NEED_HELP";

    /** 教练确认值（student_subject_status.coach_confirm_status） */
    private static final Set<String> CONFIRM_STATUSES =
            Set.of("CONFIRMED_READY", "NOT_READY", "NEED_HELP");

    /** AI 提示词中最多列出的薄弱知识点数量 */
    private static final int AI_PROMPT_WEAK_LIMIT = 5;

    private final CoachStudentMapper coachStudentMapper;
    private final UserMapper userMapper;
    private final StudentSubjectStatusMapper studentSubjectStatusMapper;
    private final PasswordEncoder passwordEncoder;
    private final AnalysisService analysisService;
    private final WrongBookService wrongBookService;
    private final ExamService examService;
    private final DeepSeekClient deepSeekClient;

    public CoachStudentServiceImpl(CoachStudentMapper coachStudentMapper,
                                   UserMapper userMapper,
                                   StudentSubjectStatusMapper studentSubjectStatusMapper,
                                   PasswordEncoder passwordEncoder,
                                   AnalysisService analysisService,
                                   WrongBookService wrongBookService,
                                   ExamService examService,
                                   DeepSeekClient deepSeekClient) {
        this.coachStudentMapper = coachStudentMapper;
        this.userMapper = userMapper;
        this.studentSubjectStatusMapper = studentSubjectStatusMapper;
        this.passwordEncoder = passwordEncoder;
        this.analysisService = analysisService;
        this.wrongBookService = wrongBookService;
        this.examService = examService;
        this.deepSeekClient = deepSeekClient;
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
        // 防 N+1：本页学员一次 IN 查 student_subject_status，内存推导当前科目/确认状态
        Map<Long, StudentSubjectStatus> currentStatusMap = loadCurrentStatusMap(
                page.getRecords().stream().map(CoachStudent::getStudentId).collect(Collectors.toList()));
        List<StudentVO> records = page.getRecords().stream()
                .map(bind -> toStudentVO(bind, userMap, currentStatusMap))
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
        } else if (student.getStatus() == null || student.getStatus() != USER_STATUS_NORMAL) {
            // 已禁用学员不允许被绑定/创建新绑定；历史数据仍保留
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
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

    /* ========================= 6. 学员学习数据 ========================= */

    @Override
    public LearningDataVO getLearningData(Long coachId, Long bindId, String subjectCode,
                                          int pageNum, int pageSize) {
        // ① 先归属：不存在 / 不是当前教练 / 已解绑，统一 3102；studentId 只从该记录反查
        CoachStudent bind = loadOwnedActive(coachId, bindId);

        // ② 再校验科目；既有分析/考试历史均按科目统计，不接受空白或未知科目
        String subject = requireSubjectCode(subjectCode);
        Long studentId = bind.getStudentId();

        // ③ 全部复用既有 Service 的统计口径；本方法只读，不写任何业务表
        LearningDataVO vo = new LearningDataVO();
        vo.setStudentId(studentId);
        vo.setSubjectCode(subject);

        AnalysisOverviewVO overview = analysisService.overview(studentId, subject);
        vo.setOverview(overview);

        WrongBookSummaryVO wrongBookSummary = wrongBookService.summary(studentId, subject);
        vo.setWrongBookSummary(wrongBookSummary);

        List<KPStatVO> stats = analysisService.knowledge(studentId, subject);
        List<KPStatVO> weak = (stats == null)
                ? Collections.<KPStatVO>emptyList()
                : stats.stream()
                        .filter(item -> Boolean.TRUE.equals(item.getIsWeak()))
                        .collect(Collectors.toList());
        vo.setWeakKnowledgePoints(weak);

        // 分页、排序、只统计已结束考试的规则全部沿用学员考试历史接口
        vo.setMockExamHistory(examService.history(studentId, subject, pageNum, pageSize));
        return vo;
    }

    /* ========================= 7. 学员错题数据 ========================= */

    @Override
    public PageResult<WrongBookItemVO> listStudentWrongBook(Long coachId, Long bindId,
                                                            String subjectCode,
                                                            WrongBookCategory category,
                                                            int pageNum, int pageSize) {
        // ① 先归属：不存在 / 不是当前教练 / 已解绑，统一 3102；失败时绝不查询错题
        CoachStudent bind = loadOwnedActive(coachId, bindId);

        // ② 分页归一化沿用教练端既有规则（页码最小 1、每页默认 10、上限 20）
        int current = Math.max(pageNum, 1);
        int size = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        // ③ 复用学员端错题本 Service：分类口径、排序、防 N+1 全部保持一致
        return wrongBookService.list(bind.getStudentId(), subjectCode, category, current, size);
    }

    /* ========================= 8. AI 学习建议 ========================= */

    @Override
    public AiSuggestVO generateAiSuggest(Long coachId, Long bindId, String subjectCode) {
        // ① 先归属：失败统一 3102，且不会调用 AI / 分析 / 错题服务
        CoachStudent bind = loadOwnedActive(coachId, bindId);
        String subject = requireSubjectCode(subjectCode);
        Long studentId = bind.getStudentId();

        // ② 复用既有学情分析和错题统计；不重复实现掌握度/错题分类
        AnalysisOverviewVO overview = analysisService.overview(studentId, subject);
        List<KPStatVO> stats = analysisService.knowledge(studentId, subject);
        WrongBookSummaryVO wrongBook = wrongBookService.summary(studentId, subject);
        PageResult<ExamRecordVO> exams = examService.history(studentId, subject, 1, 5);

        String examReadySuggest = decideExamReadySuggest(overview);
        String ruleSuggestion = buildRuleSuggestion(overview, stats, wrongBook);
        String prompt = buildAiPrompt(subject, overview, stats, wrongBook, exams);

        // ③ 调用既有 DeepSeek 客户端；失败时由本业务层降级为规则建议
        String aiSuggestion = null;
        boolean aiSuccess = false;
        String degradedReason = null;
        try {
            ChatResult result = deepSeekClient.chat(List.of(
                    ChatMessage.system("你是驾考教练助手。只能依据给定的学情数据给出复习建议，"
                            + "不得编造未提供的数据；用不超过 200 字给出 2~3 条可执行建议。"),
                    ChatMessage.user(prompt)));
            if (result == null || !StringUtils.hasText(result.getContent())) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED);
            }
            aiSuggestion = result.getContent().trim();
            aiSuccess = true;
        } catch (BusinessException e) {
            degradedReason = aiFailureReason(e.getCode());
            log.warn("教练端 AI 建议生成失败，降级为规则建议 code={} reason={}",
                    e.getCode(), degradedReason);
        }

        LocalDateTime aiSuggestTime = aiSuccess ? LocalDateTime.now() : null;
        String suggestion = aiSuccess ? aiSuggestion : ruleSuggestion;

        // ④ 覆盖本次建议：AI 失败时 ai_suggest/ai_suggest_time 写 null，避免伪造“AI 成功”
        //    新建议不重置教练确认状态；确认与 AI 建议是独立职责
        studentSubjectStatusMapper.upsertAiSuggestion(
                studentId, subject, examReadySuggest,
                aiSuccess ? aiSuggestion : null, aiSuggestTime);

        // 生成新建议不重置确认状态；README/设计约定中确认与 AI 建议独立。
        // 由于表没有“建议版本”字段，确认状态可能来自更早一次建议，这是已知数据语义限制。
        StudentSubjectStatus saved = studentSubjectStatusMapper.selectOne(
                new LambdaQueryWrapper<StudentSubjectStatus>()
                        .eq(StudentSubjectStatus::getStudentId, studentId)
                        .eq(StudentSubjectStatus::getSubjectCode, subject));

        AiSuggestVO vo = new AiSuggestVO();
        vo.setStudentId(studentId);
        vo.setSubjectCode(subject);
        vo.setExamReadySuggest(examReadySuggest);
        vo.setSuggestion(suggestion);
        vo.setSource(aiSuccess ? AiSuggestVO.SOURCE_AI : AiSuggestVO.SOURCE_RULE);
        vo.setAiSuccess(aiSuccess);
        vo.setDegraded(!aiSuccess);
        vo.setDegradedReason(degradedReason);
        vo.setAiSuggestTime(aiSuggestTime);
        vo.setCoachConfirmStatus(saved == null ? null : saved.getCoachConfirmStatus());
        return vo;
    }

    @Override
    public AiSuggestVO getSavedAiSuggest(Long coachId, Long bindId, String subjectCode) {
        // ① 先归属：失败统一 3102，且不会读取建议行
        CoachStudent bind = loadOwnedActive(coachId, bindId);
        String subject = requireSubjectCode(subjectCode);
        Long studentId = bind.getStudentId();

        // ② 只读已保存行；不调用 AI、不生成规则文本、不写库
        StudentSubjectStatus row = studentSubjectStatusMapper.selectOne(
                new LambdaQueryWrapper<StudentSubjectStatus>()
                        .eq(StudentSubjectStatus::getStudentId, studentId)
                        .eq(StudentSubjectStatus::getSubjectCode, subject));
        if (row == null) {
            return null;
        }

        boolean hasAi = StringUtils.hasText(row.getAiSuggest());
        boolean hasRule = StringUtils.hasText(row.getExamReadySuggest());
        if (!hasAi && !hasRule) {
            return null;
        }

        AiSuggestVO vo = new AiSuggestVO();
        vo.setStudentId(studentId);
        vo.setSubjectCode(subject);
        vo.setExamReadySuggest(row.getExamReadySuggest());
        vo.setCoachConfirmStatus(row.getCoachConfirmStatus());

        if (hasAi) {
            // 当前写入约定：只有 AI 成功才写 ai_suggest/ai_suggest_time
            vo.setSuggestion(row.getAiSuggest());
            vo.setSource(AiSuggestVO.SOURCE_AI);
            vo.setAiSuccess(true);
            vo.setDegraded(false);
            vo.setDegradedReason(null);
            vo.setAiSuggestTime(row.getAiSuggestTime());
        } else {
            // 规则建议只持久化了 exam_ready_suggest 等级，完整文本未保存，不能伪造
            vo.setSuggestion(null);
            vo.setSource(AiSuggestVO.SOURCE_RULE);
            vo.setAiSuccess(false);
            vo.setDegraded(true);
            vo.setDegradedReason(null);
            vo.setAiSuggestTime(null);
        }
        return vo;
    }

    @Override
    public void confirmSuggestion(Long coachId, Long bindId, String subjectCode, String confirmStatus) {
        // ① 先归属：失败统一 3102；确认不依赖 AI/规则建议是否存在
        CoachStudent bind = loadOwnedActive(coachId, bindId);
        String subject = requireSubjectCode(subjectCode);
        String status = confirmStatus == null ? "" : confirmStatus.trim().toUpperCase();
        if (!CONFIRM_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        // ② 独立 upsert：无建议行时创建确认行，已有确认时允许修改；不触碰建议文本/时间
        studentSubjectStatusMapper.upsertCoachConfirm(bind.getStudentId(), subject, status);
    }

    /* ============================== 私有工具 ============================== */

    /** 依据既有学情总览给出系统建议；阈值是本接口最小实现的可解释规则。 */
    private String decideExamReadySuggest(AnalysisOverviewVO overview) {
        int total = intValue(overview == null ? null : overview.getTotalQuestions());
        int answered = intValue(overview == null ? null : overview.getAnsweredQuestions());
        int weak = intValue(overview == null ? null : overview.getWeakCount());
        Double rate = overview == null ? null : overview.getCorrectRate();
        Double progress = overview == null ? null : overview.getProgress();

        if (total == 0 || answered == 0) {
            return SUGGEST_NEED_MORE;
        }
        if (weak > 0) {
            return SUGGEST_NEED_HELP;
        }
        if (progress != null && progress >= 1.0 && rate != null && rate >= 0.8) {
            return SUGGEST_READY;
        }
        return SUGGEST_NEED_MORE;
    }

    /** 规则型兜底建议；明确不宣称由 AI 生成。 */
    private String buildRuleSuggestion(AnalysisOverviewVO overview,
                                       List<KPStatVO> stats,
                                       WrongBookSummaryVO wrongBook) {
        int answered = intValue(overview == null ? null : overview.getAnsweredQuestions());
        int total = intValue(overview == null ? null : overview.getTotalQuestions());
        int weakCount = intValue(overview == null ? null : overview.getWeakCount());
        Double rate = overview == null ? null : overview.getCorrectRate();
        Double progress = overview == null ? null : overview.getProgress();

        if (total == 0 || answered == 0) {
            return "该学员尚未开始本科目练习，建议先完成顺序练习，熟悉基础知识点并整理错题。";
        }

        List<KPStatVO> weak = weakPoints(stats);
        if (weakCount > 0 || !weak.isEmpty()) {
            String names = weak.stream()
                    .map(KPStatVO::getKnowledgePointName)
                    .filter(StringUtils::hasText)
                    .limit(3)
                    .collect(Collectors.joining("、"));
            String focus = names.isEmpty() ? "" : "（重点：" + names + "）";
            return "该学员有 " + Math.max(weakCount, weak.size()) + " 个薄弱知识点" + focus
                    + "，建议优先复习这些知识点，并完成对应章节练习。";
        }

        if (progress == null || progress < 1.0 || rate == null || rate < 0.8) {
            return "该学员已完成 " + answered + "/" + total + " 题，正确率 " + percent(rate)
                    + "，建议继续完成剩余题目并复习错题，再进行模拟考试。";
        }

        int pending = intValue(wrongBook == null ? null : wrongBook.getPending());
        int high = intValue(wrongBook == null ? null : wrongBook.getHighFrequency());
        return "该学员已完成本科目全部题目，正确率 " + percent(rate)
                + "；当前待攻克错题 " + pending + " 道、高频错题 " + high
                + " 道，建议进行模拟考试并复盘错题。";
    }

    /** 组装给 DeepSeek 的数据上下文；不包含任何密钥或请求头。 */
    private String buildAiPrompt(String subject,
                                 AnalysisOverviewVO overview,
                                 List<KPStatVO> stats,
                                 WrongBookSummaryVO wrongBook,
                                 PageResult<ExamRecordVO> exams) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("科目：").append(subject).append('\n');
        sb.append("题目总数：").append(intValue(overview == null ? null : overview.getTotalQuestions())).append('\n');
        sb.append("已答题数：").append(intValue(overview == null ? null : overview.getAnsweredQuestions())).append('\n');
        sb.append("总体正确率：").append(percent(overview == null ? null : overview.getCorrectRate())).append('\n');
        sb.append("练习进度：").append(percent(overview == null ? null : overview.getProgress())).append('\n');
        sb.append("薄弱知识点数量：").append(intValue(overview == null ? null : overview.getWeakCount())).append('\n');

        List<KPStatVO> weak = weakPoints(stats);
        sb.append("薄弱知识点：");
        if (weak.isEmpty()) {
            sb.append("无");
        } else {
            sb.append(weak.stream().limit(AI_PROMPT_WEAK_LIMIT)
                    .map(kp -> kp.getKnowledgePointName() + "（掌握度" + percent(kp.getMastery()) + "）")
                    .collect(Collectors.joining("；")));
        }
        sb.append('\n');

        sb.append("错题统计：待攻克=").append(intValue(wrongBook == null ? null : wrongBook.getPending()))
                .append("，高频错=").append(intValue(wrongBook == null ? null : wrongBook.getHighFrequency()))
                .append("，已攻克=").append(intValue(wrongBook == null ? null : wrongBook.getMastered()))
                .append('\n');

        sb.append("最近模拟考试：");
        List<ExamRecordVO> records = exams == null ? Collections.emptyList() : exams.getRecords();
        if (records == null || records.isEmpty()) {
            sb.append("无");
        } else {
            sb.append(records.stream()
                    .map(e -> e.getPaperTitle() + "(得分" + (e.getScore() == null ? "未知" : e.getScore())
                            + (Boolean.TRUE.equals(e.getIsPass()) ? "，及格" : "，未及格") + ")")
                    .collect(Collectors.joining("；")));
        }
        return sb.toString();
    }

    private List<KPStatVO> weakPoints(List<KPStatVO> stats) {
        if (stats == null) {
            return Collections.emptyList();
        }
        return stats.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsWeak()))
                .collect(Collectors.toList());
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String percent(Double value) {
        return value == null ? "无" : Math.round(value * 100) + "%";
    }

    private static String aiFailureReason(Integer code) {
        if (ErrorCode.AI_TIMEOUT.getCode().equals(code)) {
            return "AI_TIMEOUT";
        }
        if (ErrorCode.AI_QUOTA_EXCEEDED.getCode().equals(code)) {
            return "AI_QUOTA_EXCEEDED";
        }
        return "AI_CALL_FAILED";
    }

    /** 校验并归一化学科编码；只接受四科，避免把未知科目当成"空数据"返回。 */
    private String requireSubjectCode(String subjectCode) {
        String subject = subjectCode == null ? "" : subjectCode.trim().toUpperCase();
        if (!SUBJECT_CODES.contains(subject)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return subject;
    }

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
     * 本页学员的“当前学习科目”映射。
     *
     * <p>规则：READY_FOR_EXAM &gt; LEARNING &gt; EXAM_FAILED &gt; NOT_STARTED；
     * 同阶段取科目编码较大者（SUBJECT_4 &gt; SUBJECT_3 ...），只是稳定的展示顺序。
     * EXAM_PASSED 不视为“进行中”，没有其它状态时返回 null，前端显示“暂无进行中科目”。</p>
     *
     * <p>一次 IN 查询完成，避免列表 N+1；空集合直接返回空 Map。</p>
     */
    private Map<Long, StudentSubjectStatus> loadCurrentStatusMap(Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<StudentSubjectStatus> rows = studentSubjectStatusMapper.selectList(
                new LambdaQueryWrapper<StudentSubjectStatus>()
                        .in(StudentSubjectStatus::getStudentId, studentIds));
        Map<Long, StudentSubjectStatus> map = new HashMap<>();
        for (StudentSubjectStatus row : rows) {
            if (currentStatusRank(row.getStatus()) >= 99) {
                continue;
            }
            StudentSubjectStatus prev = map.get(row.getStudentId());
            if (prev == null || isMoreCurrent(row, prev)) {
                map.put(row.getStudentId(), row);
            }
        }
        return map;
    }

    private static int currentStatusRank(String status) {
        if ("READY_FOR_EXAM".equals(status)) return 0;
        if ("LEARNING".equals(status)) return 1;
        if ("EXAM_FAILED".equals(status)) return 2;
        if ("NOT_STARTED".equals(status)) return 3;
        return 99;
    }

    private static boolean isMoreCurrent(StudentSubjectStatus candidate, StudentSubjectStatus current) {
        int cr = currentStatusRank(candidate.getStatus());
        int rr = currentStatusRank(current.getStatus());
        if (cr != rr) return cr < rr;
        String cs = candidate.getSubjectCode() == null ? "" : candidate.getSubjectCode();
        String rs = current.getSubjectCode() == null ? "" : current.getSubjectCode();
        return cs.compareTo(rs) > 0;
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
    private StudentVO toStudentVO(CoachStudent bind, Map<Long, User> userMap,
                                  Map<Long, StudentSubjectStatus> currentStatusMap) {
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

        StudentSubjectStatus current = currentStatusMap.get(bind.getStudentId());
        if (current != null) {
            vo.setCurrentSubjectCode(current.getSubjectCode());
            vo.setCurrentPhaseStatus(current.getStatus());
            vo.setCoachConfirmStatus(current.getCoachConfirmStatus());
        }
        return vo;
    }
}
