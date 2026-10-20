package com.driving.exam.server.exam.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.ExamAnswerDTO;
import com.driving.exam.pojo.dto.ExamSubmitDTO;
import com.driving.exam.pojo.entity.ExamPaper;
import com.driving.exam.pojo.entity.ExamPaperQuestion;
import com.driving.exam.pojo.entity.MockExam;
import com.driving.exam.pojo.entity.MockExamAnswer;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.ExamJudgeVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.pojo.vo.ExamVO;
import com.driving.exam.pojo.vo.PaperVO;
import com.driving.exam.server.enums.ExamStatus;
import com.driving.exam.server.exam.ExamFinalizer;
import com.driving.exam.server.exam.ExamService;
import com.driving.exam.server.exam.PaperQuestionItem;
import com.driving.exam.server.grader.AnswerGrader;
import com.driving.exam.server.mapper.ExamPaperMapper;
import com.driving.exam.server.mapper.ExamPaperQuestionMapper;
import com.driving.exam.server.mapper.MockExamAnswerMapper;
import com.driving.exam.server.mapper.MockExamMapper;
import com.driving.exam.server.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模拟考试 Service 实现。
 *
 * <p><b>事务边界</b>：结算写库由 {@link ExamFinalizer}（独立 Bean + {@code @Transactional}）承担；
 * {@link #start(Long, Long)} 额外用试卷行锁和本地事务串行化同一试卷的并发开考。
 * 交卷与超时收尾一律通过注入的 Bean 调用 {@code examFinalizer.xxx(...)}——跨 Bean 才走 AOP 代理。</p>
 *
 * <p>口径详见 {@code doc/exam-design.md}。</p>
 */
@Slf4j
@Service
public class ExamServiceImpl implements ExamService {

    /** 试卷类型：本期只用模拟卷 */
    private static final String PAPER_TYPE_MOCK = "MOCK";
    /** 试卷启用状态 */
    private static final int PAPER_STATUS_ENABLED = 1;
    /** 历史分页每页上限 */
    private static final int MAX_PAGE_SIZE = 20;
    /** 历史分页默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final MockExamMapper mockExamMapper;
    private final MockExamAnswerMapper mockExamAnswerMapper;
    private final ExamFinalizer examFinalizer;
    private final QuestionService questionService;
    private final AnswerGrader answerGrader;

    public ExamServiceImpl(ExamPaperMapper examPaperMapper,
                           ExamPaperQuestionMapper examPaperQuestionMapper,
                           MockExamMapper mockExamMapper,
                           MockExamAnswerMapper mockExamAnswerMapper,
                           ExamFinalizer examFinalizer,
                           QuestionService questionService,
                           AnswerGrader answerGrader) {
        this.examPaperMapper = examPaperMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.mockExamMapper = mockExamMapper;
        this.mockExamAnswerMapper = mockExamAnswerMapper;
        this.examFinalizer = examFinalizer;
        this.questionService = questionService;
        this.answerGrader = answerGrader;
    }

    /* ============================== 1. 试卷列表 ============================== */

    @Override
    public List<PaperVO> papers(Long userId, String subjectCode) {
        List<ExamPaper> papers = examPaperMapper.selectList(new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getSubjectCode, subjectCode)
                .eq(ExamPaper::getPaperType, PAPER_TYPE_MOCK)
                .eq(ExamPaper::getStatus, PAPER_STATUS_ENABLED)
                .orderByAsc(ExamPaper::getId));
        if (papers.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次查该学员全部进行中的考试 → paperId → examId
        List<MockExam> ongoing = mockExamMapper.selectList(new LambdaQueryWrapper<MockExam>()
                .eq(MockExam::getUserId, userId)
                .eq(MockExam::getStatus, ExamStatus.ONGOING.name()));
        Map<Long, Long> ongoingByPaper = new HashMap<>();
        for (MockExam e : ongoing) {
            ongoingByPaper.putIfAbsent(e.getPaperId(), e.getId());
        }

        List<PaperVO> list = new ArrayList<>(papers.size());
        for (ExamPaper p : papers) {
            PaperVO vo = new PaperVO();
            vo.setId(p.getId());
            vo.setTitle(p.getTitle());
            vo.setSubjectCode(p.getSubjectCode());
            vo.setQuestionCount(p.getQuestionCount());
            vo.setDuration(p.getDuration());
            vo.setTotalScore(p.getTotalScore());
            vo.setPassScore(p.getPassScore());
            vo.setOngoingExamId(ongoingByPaper.get(p.getId()));
            list.add(vo);
        }
        return list;
    }

    /* ============================== 2. 开始考试 ============================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamVO start(Long userId, Long paperId) {
        // 锁定试卷行：同一试卷的并发开始请求串行化，避免“查无进行中 → 两次插入”
        ExamPaper paper = loadPaperForUpdate(paperId);

        MockExam ongoing = findOngoing(userId, paperId);
        if (ongoing != null) {
            if (!isTimeout(ongoing, paper)) {
                // 未超时：直接断点续考
                return buildExamVO(ongoing, paper, loadPaperItems(paperId));
            }
            // 已超时：先收尾旧的（惰性判定），再新建一份
            examFinalizer.finalize(ongoing, paper.getDuration(), loadPaperItems(paperId),
                    loadAnsweredMap(ongoing.getId()), true);
        }

        // 新考试创建前校验整份试卷题型：存在不可判分题型 → 拒绝开考（不创建新考试）
        assertPaperGradeable(loadPaperItems(paperId));

        MockExam created = createExam(userId, paper);
        return buildExamVO(created, paper, loadPaperItems(paperId));
    }

    /* ============================== 3. 断点续考 ============================== */

    @Override
    public ExamVO resume(Long userId, Long examId) {
        MockExam exam = loadOwnedExam(userId, examId);
        // 已开始的考试即使试卷之后被停用，也必须能恢复/交卷/查看历史报告
        ExamPaper paper = loadPaperForExistingExam(exam.getPaperId());

        if (ExamStatus.ONGOING.name().equals(exam.getStatus()) && isTimeout(exam, paper)) {
            // 惰性超时收尾（本方法名义 GET，实际会写库）
            List<PaperQuestionItem> items = loadPaperItems(paper.getId());
            examFinalizer.finalize(exam, paper.getDuration(), items, loadAnsweredMap(examId), true);
            // finalize 会改内存里的 exam，但为确定性从库重读一次
            exam = mockExamMapper.selectById(examId);
            return buildExamVO(exam, paper, items);
        }
        return buildExamVO(exam, paper, loadPaperItems(paper.getId()));
    }

    /* ============================== 4. 暂存答案 ============================== */

    @Override
    public void answer(Long userId, Long examId, ExamAnswerDTO dto) {
        MockExam exam = loadOwnedExam(userId, examId);
        if (!ExamStatus.ONGOING.name().equals(exam.getStatus())) {
            throw new BusinessException(ErrorCode.EXAM_SUBMITTED);
        }
        if (dto == null || dto.getQuestionId() == null
                || dto.getUserAnswer() == null || dto.getUserAnswer().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        ExamPaper paper = loadPaperForExistingExam(exam.getPaperId());
        assertQuestionInPaper(paper.getId(), dto.getQuestionId());

        // 服务端时间已超时：先按超时结算，再拒绝这次延迟到达的答案；
        // 不能把“超时后的暂存”留成一场仍可继续的 ONGOING 考试。
        if (isTimeout(exam, paper)) {
            finalizeAsTimeout(exam, paper);
            throw new BusinessException(ErrorCode.EXAM_SUBMITTED);
        }

        mockExamAnswerMapper.upsertAnswer(examId, dto.getQuestionId(), dto.getUserAnswer());
    }

    /* ============================== 5. 交卷 ============================== */

    @Override
    public ExamReportVO submit(Long userId, Long examId, ExamSubmitDTO dto) {
        MockExam exam = loadOwnedExam(userId, examId);
        // 已开始的考试允许在试卷停用后继续交卷/查看报告
        ExamPaper paper = loadPaperForExistingExam(exam.getPaperId());
        List<PaperQuestionItem> items = loadPaperItems(paper.getId());
        Set<Long> paperQuestionIds = items.stream()
                .map(it -> it.question().getId())
                .collect(Collectors.toSet());

        // 幂等：已交卷直接返回既有报告（不报错、不重复写库）
        if (!ExamStatus.ONGOING.name().equals(exam.getStatus())) {
            return examFinalizer.buildReport(exam, items);
        }

        // 答案集 = 暂存草稿 ∪ 请求体（请求体优先）。请求体中的题必须属于本卷；
        // 历史草稿中的多余额外 ID 不回灌到报告（只保留试卷内题目）。
        Map<Long, String> answers = loadAnsweredMap(examId);
        answers.keySet().removeIf(questionId -> !paperQuestionIds.contains(questionId));
        if (dto != null && dto.getAnswers() != null) {
            for (ExamAnswerDTO a : dto.getAnswers()) {
                if (a == null || a.getQuestionId() == null) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR);
                }
                if (!paperQuestionIds.contains(a.getQuestionId())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR);
                }
                answers.put(a.getQuestionId(), a.getUserAnswer());
            }
        }

        boolean timeout = isTimeout(exam, paper);
        ExamReportVO report = examFinalizer.finalize(exam, paper.getDuration(), items, answers, timeout);
        if (report != null) {
            return report;
        }
        // 未抢到结算（已被他方并发结算）：**事务外**重读考试与报告，返回既有成绩——绝不伪造成功报告。
        MockExam settled = mockExamMapper.selectById(examId);
        if (settled == null) {
            throw new BusinessException(ErrorCode.EXAM_NOT_FOUND);
        }
        if (ExamStatus.ONGOING.name().equals(settled.getStatus())) {
            // 状态与"已结算"不一致（如并发结算已回滚）：明确报错，不返回报告
            throw new BusinessException(ErrorCode.EXAM_ONGOING);
        }
        return examFinalizer.buildReport(settled, items);
    }

    /* ============================== 6. 成绩报告 ============================== */

    @Override
    public ExamReportVO report(Long userId, Long examId) {
        MockExam exam = loadOwnedExam(userId, examId);
        if (ExamStatus.ONGOING.name().equals(exam.getStatus())) {
            // 进行中无报告（前端应先 resume/submit 触发收尾）
            throw new BusinessException(ErrorCode.EXAM_ONGOING);
        }
        ExamPaper paper = loadPaperForExistingExam(exam.getPaperId());
        return examFinalizer.buildReport(exam, loadPaperItems(paper.getId()));
    }

    /* ========================= 7. 单题判题（考试中） ========================= */

    @Override
    public ExamJudgeVO judge(Long userId, Long examId, Long questionId) {
        MockExam exam = loadOwnedExam(userId, examId);            // 归属校验（不存在/非本人 → 2104）
        if (!ExamStatus.ONGOING.name().equals(exam.getStatus())) {
            throw new BusinessException(ErrorCode.EXAM_SUBMITTED); // 已交卷/超时 → 2102
        }
        ExamPaper paper = loadPaperForExistingExam(exam.getPaperId());
        // 校验题目属于当前试卷；不属于 → 1001
        PaperQuestionItem target = loadPaperItems(paper.getId()).stream()
                .filter(it -> it.question().getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR));
        // 非法题型 → 明确业务错误（避免落到未处理的 HTTP 500）
        if (!answerGrader.isGradeable(target.question().getQuestionType())) {
            throw new BusinessException(ErrorCode.EXAM_PAPER_INVALID);
        }

        ExamJudgeVO vo = new ExamJudgeVO();
        vo.setQuestionId(questionId);

        // 判题所用答案 = 该题【服务器已保存】的答案（不采信前端传入；uk_exam_question 保证至多一行）
        MockExamAnswer saved = mockExamAnswerMapper.selectOne(new LambdaQueryWrapper<MockExamAnswer>()
                .eq(MockExamAnswer::getExamId, examId)
                .eq(MockExamAnswer::getQuestionId, questionId));
        if (saved == null || saved.getUserAnswer() == null) {
            vo.setAnswered(false);
            vo.setIsCorrect(false);
            return vo;
        }
        vo.setAnswered(true);
        vo.setIsCorrect(answerGrader.isCorrect(
                target.question().getQuestionType(), saved.getUserAnswer(), target.question().getAnswer()));
        return vo;
    }

    /* ============================== 8. 考试历史 ============================== */

    @Override
    public PageResult<ExamRecordVO> history(Long userId, String subjectCode, int pageNum, int pageSize) {
        int current = Math.max(pageNum, 1);
        int size = (pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);

        Page<MockExam> page = mockExamMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<MockExam>()
                        .eq(MockExam::getUserId, userId)
                        .eq(MockExam::getSubjectCode, subjectCode)
                        .ne(MockExam::getStatus, ExamStatus.ONGOING.name())
                        .orderByDesc(MockExam::getSubmitTime)
                        .orderByDesc(MockExam::getId));

        List<MockExam> records = page.getRecords();
        // 一次 IN 查试卷标题（防 N+1）
        Map<Long, String> titles = new HashMap<>();
        Set<Long> paperIds = records.stream().map(MockExam::getPaperId).collect(Collectors.toSet());
        if (!paperIds.isEmpty()) {
            for (ExamPaper p : examPaperMapper.selectList(
                    new LambdaQueryWrapper<ExamPaper>().in(ExamPaper::getId, paperIds))) {
                titles.put(p.getId(), p.getTitle());
            }
        }

        List<ExamRecordVO> vos = new ArrayList<>(records.size());
        for (MockExam e : records) {
            ExamRecordVO vo = new ExamRecordVO();
            vo.setExamId(e.getId());
            vo.setPaperTitle(titles.get(e.getPaperId()));
            vo.setScore(e.getScore());
            vo.setIsPass(e.getScore() != null && e.getPassScore() != null && e.getScore() >= e.getPassScore());
            vo.setSubmitTime(e.getSubmitTime());
            vo.setDuration(e.getDuration());
            vos.add(vo);
        }
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), vos);
    }

    /* ========================= 9. 定时超时收尾 ========================= */

    @Override
    public int finalizeExpiredExams(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<Long> expiredIds = mockExamMapper.selectExpiredOngoingIds(safeLimit);
        if (expiredIds == null || expiredIds.isEmpty()) {
            return 0;
        }
        int settled = 0;
        for (Long examId : expiredIds) {
            try {
                MockExam exam = mockExamMapper.selectById(examId);
                if (exam == null || !ExamStatus.ONGOING.name().equals(exam.getStatus())) {
                    continue;
                }
                ExamPaper paper = loadPaperForExistingExam(exam.getPaperId());
                if (!isTimeout(exam, paper)) {
                    continue; // 查询与处理之间被手动交卷，或数据库/应用时钟边界
                }
                List<PaperQuestionItem> items = loadPaperItems(paper.getId());
                ExamReportVO report = examFinalizer.finalize(
                        exam, paper.getDuration(), items, loadAnsweredMap(examId), true);
                if (report != null) {
                    settled++;
                    log.info("超时考试已自动收尾 examId={}", examId);
                }
            } catch (RuntimeException e) {
                // 单场异常不阻塞其它考试；下一轮扫描会再次尝试
                log.warn("超时考试自动收尾失败 examId={} msg={}", examId, e.getMessage(), e);
            }
        }
        return settled;
    }

    /* ============================== 私有工具 ============================== */

    /** 归属校验：不存在或非本人**统一**抛 2104（不泄露存在性）。 */
    private MockExam loadOwnedExam(Long userId, Long examId) {
        MockExam exam = mockExamMapper.selectById(examId);
        if (exam == null || !exam.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.EXAM_NOT_FOUND);
        }
        return exam;
    }

    /** 开考事务内锁定试卷行，校验存在且启用。 */
    private ExamPaper loadPaperForUpdate(Long paperId) {
        ExamPaper paper = examPaperMapper.selectByIdForUpdate(paperId);
        if (paper == null || !Integer.valueOf(PAPER_STATUS_ENABLED).equals(paper.getStatus())) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    /**
     * 已开始考试的试卷读取：允许试卷后来被停用。
     *
     * <p>停用只应影响“新开考”，不能让已经存在的考试无法恢复、交卷或查看报告；
     * 试卷内容变更仍由管理员端的一致性保护禁止。</p>
     */
    private ExamPaper loadPaperForExistingExam(Long paperId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND);
        }
        return paper;
    }

    /** 校验题目属于当前考试对应的试卷；不属于 → 1001。 */
    private void assertQuestionInPaper(Long paperId, Long questionId) {
        Long count = examPaperQuestionMapper.selectCount(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, paperId)
                        .eq(ExamPaperQuestion::getQuestionId, questionId));
        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
    }

    /** 超时收尾复用现有条件抢占；并发下返回 null 表示已由他方结算。 */
    private void finalizeAsTimeout(MockExam exam, ExamPaper paper) {
        List<PaperQuestionItem> items = loadPaperItems(paper.getId());
        examFinalizer.finalize(exam, paper.getDuration(), items, loadAnsweredMap(exam.getId()), true);
    }

    /** 校验整份试卷题型可判分；存在不可判分题型 → {@code EXAM_PAPER_INVALID(2106)}。 */
    private void assertPaperGradeable(List<PaperQuestionItem> items) {
        for (PaperQuestionItem it : items) {
            if (!answerGrader.isGradeable(it.question().getQuestionType())) {
                throw new BusinessException(ErrorCode.EXAM_PAPER_INVALID);
            }
        }
    }

    /** 取试卷题目（含每题分值与顺序）；题目实体一次 {@code listByIds} 取回，防 N+1。 */
    private List<PaperQuestionItem> loadPaperItems(Long paperId) {
        List<ExamPaperQuestion> links = examPaperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, paperId)
                        .orderByAsc(ExamPaperQuestion::getSortOrder)
                        .orderByAsc(ExamPaperQuestion::getId));
        if (links.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> questionIds = links.stream()
                .map(ExamPaperQuestion::getQuestionId)
                .collect(Collectors.toList());
        Map<Long, Question> byId = questionService.listByIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<PaperQuestionItem> items = new ArrayList<>(links.size());
        for (ExamPaperQuestion link : links) {
            Question q = byId.get(link.getQuestionId());
            if (q == null) {
                continue; // 题目被逻辑删除等（已知局限，记录在案）
            }
            items.add(new PaperQuestionItem(q, link.getScore(), link.getSortOrder()));
        }
        return items;
    }

    /** 剩余秒数：{@code duration*60 − (now − startTime)}，负数归 0；非进行中直接 0。 */
    private long computeRemainingSeconds(MockExam exam, ExamPaper paper) {
        if (!ExamStatus.ONGOING.name().equals(exam.getStatus())) {
            return 0L;
        }
        long limit = paper.getDuration() == null ? 0L : paper.getDuration().longValue() * 60;
        long elapsed = Duration.between(exam.getStartTime(), LocalDateTime.now()).getSeconds();
        return Math.max(0L, limit - elapsed);
    }

    /** 是否已超时：{@code now − startTime > duration*60}。 */
    private boolean isTimeout(MockExam exam, ExamPaper paper) {
        if (paper.getDuration() == null) {
            return false;
        }
        long limit = paper.getDuration().longValue() * 60;
        long elapsed = Duration.between(exam.getStartTime(), LocalDateTime.now()).getSeconds();
        return elapsed > limit;
    }

    /** 读暂存答案：questionId → userAnswer。 */
    private Map<Long, String> loadAnsweredMap(Long examId) {
        List<MockExamAnswer> rows = mockExamAnswerMapper.selectList(
                new LambdaQueryWrapper<MockExamAnswer>().eq(MockExamAnswer::getExamId, examId));
        Map<Long, String> map = new LinkedHashMap<>();
        for (MockExamAnswer r : rows) {
            map.put(r.getQuestionId(), r.getUserAnswer());
        }
        return map;
    }

    /** 新建一场考试（状态 ONGOING，参数从试卷复制）。 */
    private MockExam createExam(Long userId, ExamPaper paper) {
        MockExam exam = new MockExam();
        exam.setUserId(userId);
        exam.setSubjectCode(paper.getSubjectCode());
        exam.setPaperId(paper.getId());
        exam.setStartTime(LocalDateTime.now());
        exam.setTotalScore(paper.getTotalScore());
        exam.setPassScore(paper.getPassScore());
        exam.setStatus(ExamStatus.ONGOING.name());
        mockExamMapper.insert(exam); // 自增 id 回填
        return exam;
    }

    /** 查该学员对某试卷"进行中"的考试（理论上最多一条，取最新）。 */
    private MockExam findOngoing(Long userId, Long paperId) {
        List<MockExam> list = mockExamMapper.selectList(new LambdaQueryWrapper<MockExam>()
                .eq(MockExam::getUserId, userId)
                .eq(MockExam::getPaperId, paperId)
                .eq(MockExam::getStatus, ExamStatus.ONGOING.name())
                .orderByDesc(MockExam::getId));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 组装考试视图：题目用 QuestionVO（不含答案），已答从草稿读。 */
    private ExamVO buildExamVO(MockExam exam, ExamPaper paper, List<PaperQuestionItem> items) {
        ExamVO vo = new ExamVO();
        vo.setExamId(exam.getId());
        vo.setPaperTitle(paper.getTitle());
        vo.setStatus(exam.getStatus());
        vo.setRemainingSeconds(computeRemainingSeconds(exam, paper));
        vo.setQuestions(questionService.assemble(items.stream()
                .map(PaperQuestionItem::question)
                .collect(Collectors.toList())));
        Map<Long, String> answeredMap = loadAnsweredMap(exam.getId());
        vo.setAnsweredMap(answeredMap);
        vo.setCorrectMap(buildCorrectMap(items, answeredMap));
        return vo;
    }

    /**
     * 已作答题目 → 是否答对（服务端用与交卷同一套 {@link AnswerGrader} 计算）。
     *
     * <p><b>只回布尔结果，不下发标准答案 / 解析</b>；未作答的题不出现在 Map 中。</p>
     */
    private Map<Long, Boolean> buildCorrectMap(List<PaperQuestionItem> items, Map<Long, String> answeredMap) {
        Map<Long, Boolean> map = new LinkedHashMap<>();
        for (PaperQuestionItem it : items) {
            Long qid = it.question().getId();
            String ans = answeredMap.get(qid);
            if (ans == null) {
                continue; // 未作答：不判、不写入
            }
            if (!answerGrader.isGradeable(it.question().getQuestionType())) {
                continue; // 非法题型：**不伪造对/错**（详情页表现为"未判"；最终结算会严格失败）
            }
            try {
                map.put(qid, answerGrader.isCorrect(
                        it.question().getQuestionType(), ans, it.question().getAnswer()));
            } catch (RuntimeException ex) {
                // ★ 单题判分异常（题型/数据异常等）**不得中断整场考试加载**：跳过该题、不写入正误。
                //   该题在答题卡上仅表现为"已答未判"，考试仍能正常打开、答题、恢复。
                //   （判分口径与交卷一致；此处仅是"不让个别脏题拖垮整个详情接口"。）
            }
        }
        return map;
    }
}
