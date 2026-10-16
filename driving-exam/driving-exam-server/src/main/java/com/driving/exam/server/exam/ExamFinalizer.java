package com.driving.exam.server.exam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.entity.AnswerRecord;
import com.driving.exam.pojo.entity.MockExam;
import com.driving.exam.pojo.entity.MockExamAnswer;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.entity.StudentSubjectStatus;
import com.driving.exam.pojo.vo.ExamReportItemVO;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.pojo.vo.QuestionOptionVO;
import com.driving.exam.server.assembler.QuestionOptionAssembler;
import com.driving.exam.server.enums.AnswerSource;
import com.driving.exam.server.enums.ExamStatus;
import com.driving.exam.server.grader.AnswerGrader;
import com.driving.exam.server.mapper.AnswerRecordMapper;
import com.driving.exam.server.mapper.MockExamAnswerMapper;
import com.driving.exam.server.mapper.MockExamMapper;
import com.driving.exam.server.mapper.StudentSubjectStatusMapper;
import com.driving.exam.server.mapper.WrongBookMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模拟考试「判分 + 落库」终结器 —— <b>独立 Bean，是全模块的事务边界所在</b>。
 *
 * <h3>为什么单独抽一个 Bean</h3>
 * 交卷要<b>跨 4 张表</b>原子写入（{@code answer_record} / {@code wrong_book} / {@code mock_exam} /
 * {@code student_subject_status}）。而超时收尾可能发生在多处（{@code submit}、{@code start}、
 * {@code resume}）；若这些调用走"同类内部方法"，Spring 的 {@code @Transactional} 代理会被绕过、
 * 事务静默失效。因此把这段逻辑放成本 Bean，由各调用方**跨 Bean 注入**调用 —— 事务才真正生效。
 *
 * <h3>两个公开入口</h3>
 * <ul>
 *   <li>{@link #finalize} —— <b>写</b>：交卷判分并落库（唯一带事务的方法）；</li>
 *   <li>{@link #buildReport} —— <b>读</b>：从 {@code answer_record} 还原成绩报告。
 *       供 {@code GET /report}、{@code submit} 的幂等分支复用；{@code finalize} 写完库后**也调用它**产出报告，
 *       从而"报告组装"只有一份实现。</li>
 * </ul>
 *
 * <h3>职责单一</h3>
 * 只依赖 Mapper + {@link AnswerGrader} + {@link QuestionOptionAssembler}，<b>不依赖任何 Service</b>，
 * 因此不会被反向依赖、也不会形成循环。
 */
@Service
public class ExamFinalizer {

    private final AnswerRecordMapper answerRecordMapper;
    private final WrongBookMapper wrongBookMapper;
    private final MockExamMapper mockExamMapper;
    private final MockExamAnswerMapper mockExamAnswerMapper;
    private final StudentSubjectStatusMapper studentSubjectStatusMapper;
    private final QuestionOptionAssembler questionOptionAssembler;
    private final AnswerGrader answerGrader;

    public ExamFinalizer(AnswerRecordMapper answerRecordMapper,
                         WrongBookMapper wrongBookMapper,
                         MockExamMapper mockExamMapper,
                         MockExamAnswerMapper mockExamAnswerMapper,
                         StudentSubjectStatusMapper studentSubjectStatusMapper,
                         QuestionOptionAssembler questionOptionAssembler,
                         AnswerGrader answerGrader) {
        this.answerRecordMapper = answerRecordMapper;
        this.wrongBookMapper = wrongBookMapper;
        this.mockExamMapper = mockExamMapper;
        this.mockExamAnswerMapper = mockExamAnswerMapper;
        this.studentSubjectStatusMapper = studentSubjectStatusMapper;
        this.questionOptionAssembler = questionOptionAssembler;
        this.answerGrader = answerGrader;
    }

    /* ============================== 写：交卷判分 ============================== */

    /**
     * 判分并落库，返回成绩报告。**并发幂等由本方法内的条件抢占保证**（见下）。
     *
     * <p>本方法在事务内先用 {@code ONGOING → 终态} 的**条件更新**抢占；只有抢到的请求继续结算。
     * 未抢到（影响 0 行）时**不执行任何结算写操作、返回 {@code null}**，由调用方在事务外重新读取。</p>
     *
     * @param exam                  考试（期望 {@code ONGOING}；非进行中会因抢占 0 行而返回 {@code null}）
     * @param paperDurationMinutes  试卷时长（分钟），用于把超时交卷的用时**封顶**
     * @param paperQuestions        试卷题目（含每题分值与顺序）
     * @param answers               questionId → 用户答案（未作答的题不在此 Map 中）
     * @param isTimeout             是否因超时交卷（决定落库状态 SUBMITTED / TIMEOUT）
     * @return 成绩报告；**未抢到结算（已被他方结算 / 非进行中）时为 {@code null}**
     * @throws BusinessException {@code EXAM_PAPER_INVALID(2106)} 试卷存在不可判分题型（整体回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExamReportVO finalize(MockExam exam,
                                 Integer paperDurationMinutes,
                                 List<PaperQuestionItem> paperQuestions,
                                 Map<Long, String> answers,
                                 boolean isTimeout) {
        // 0) 校验整份试卷题型（**含未作答题**）：存在不可判分题型 → 明确业务异常、整体回滚，不产生任何结算数据
        for (PaperQuestionItem item : paperQuestions) {
            if (!answerGrader.isGradeable(item.question().getQuestionType())) {
                throw new BusinessException(ErrorCode.EXAM_PAPER_INVALID);
            }
        }

        // 1) 并发幂等：把本场从 ONGOING **条件抢占**成终态。只有影响 1 行的请求继续结算；
        //    影响 0 行 = 已被他方结算（或本就非进行中）→ 不做任何结算写操作，返回 null 交由调用方处理。
        //    本更新与下方全部写入同处本事务：后续任一步异常都会回滚，考试状态自动回到 ONGOING。
        String terminalStatus = isTimeout ? ExamStatus.TIMEOUT.name() : ExamStatus.SUBMITTED.name();
        if (mockExamMapper.claimForSettle(exam.getId(), terminalStatus) == 0) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();
        Long examId = exam.getId();
        Long userId = exam.getUserId();
        String subjectCode = exam.getSubjectCode();

        // 1) 逐题：有作答 → 归一化 + 判分 + 写 answer_record + 答错进错题本；未作答 → 跳过（不写库）
        for (PaperQuestionItem item : sortByOrder(paperQuestions)) {
            Question q = item.question();
            String userAnswer = answers == null ? null : answers.get(q.getId());
            if (userAnswer == null || userAnswer.isBlank()) {
                continue; // 未作答：不写 answer_record、不写 wrong_book
            }
            // 归一化落库（口径与练习模块一致：归一化后为空则回退到 trim 原值）
            String normalized = answerGrader.normalize(userAnswer, q.getQuestionType());
            String stored = normalized.isEmpty() ? userAnswer.trim() : normalized;
            boolean correct = answerGrader.isCorrect(q.getQuestionType(), userAnswer, q.getAnswer());

            AnswerRecord record = new AnswerRecord();
            record.setUserId(userId);
            record.setSubjectCode(subjectCode);
            record.setQuestionId(q.getId());
            record.setUserAnswer(stored);
            record.setIsCorrect(correct ? 1 : 0);
            record.setSource(AnswerSource.EXAM.name());
            record.setExamId(examId);
            // 不设 duration（考试无单题耗时）、不设 answerTime（交 DB 默认，与练习一致）
            answerRecordMapper.insert(record);

            if (!correct) {
                wrongBookMapper.upsertWrong(userId, subjectCode, q.getId());
            }
        }

        // 2) 写库完成后，统一由 buildReport 从 answer_record 组装报告（与 GET /report 同一份逻辑）
        ExamReportVO report = buildReport(exam, paperQuestions);

        // 3) 更新考试
        long elapsed = Math.max(0L, Duration.between(exam.getStartTime(), now).getSeconds());
        int durationSeconds = paperDurationMinutes == null
                ? (int) elapsed
                : (int) Math.min(elapsed, paperDurationMinutes.longValue() * 60);
        exam.setScore(report.getScore());
        exam.setSubmitTime(now);
        exam.setDuration(durationSeconds);
        exam.setStatus(isTimeout ? ExamStatus.TIMEOUT.name() : ExamStatus.SUBMITTED.name());
        mockExamMapper.updateById(exam);

        // 4) 更新学员科目进度（mock_pass_count：及格 +1 / 不及格归 0；last_mock_score 每次写）
        applySubjectStatus(userId, subjectCode, Boolean.TRUE.equals(report.getIsPass()), report.getScore());

        // 5) 清空草稿（答案已进 answer_record，草稿无用；避免重考携带旧答案）
        mockExamAnswerMapper.delete(new LambdaQueryWrapper<MockExamAnswer>()
                .eq(MockExamAnswer::getExamId, examId));

        return report;
    }

    /* ============================== 读：还原报告 ============================== */

    /**
     * 从 {@code answer_record(source=EXAM, exam_id)} 还原成绩报告（<b>只读</b>）。
     *
     * <p>以**试卷题目为主线**组装，未作答题也在其中（{@code userAnswer=null}、{@code isCorrect=false}）。</p>
     *
     * @param exam           考试（提供 examId / totalScore / passScore）
     * @param paperQuestions 试卷题目（含每题分值与顺序）
     * @return 成绩报告
     */
    public ExamReportVO buildReport(MockExam exam, List<PaperQuestionItem> paperQuestions) {
        List<PaperQuestionItem> items = sortByOrder(paperQuestions);

        // 一次性装配选项（防 N+1）
        List<Long> questionIds = items.stream()
                .map(it -> it.question().getId())
                .collect(Collectors.toList());
        Map<Long, List<QuestionOptionVO>> optionMap = questionOptionAssembler.loadByQuestionIds(questionIds);

        // 读本次考试已作答记录（只含"答过的"，未答的没有记录）
        List<AnswerRecord> records = answerRecordMapper.selectList(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getExamId, exam.getId())
                .eq(AnswerRecord::getSource, AnswerSource.EXAM.name()));
        Map<Long, String> userAnswers = new HashMap<>();
        Map<Long, Boolean> correctness = new HashMap<>();
        for (AnswerRecord r : records) {
            userAnswers.put(r.getQuestionId(), r.getUserAnswer());
            correctness.put(r.getQuestionId(), Integer.valueOf(1).equals(r.getIsCorrect()));
        }

        int score = 0;
        int correctCount = 0;
        int wrongCount = 0;
        int unansweredCount = 0;
        List<ExamReportItemVO> reportItems = new ArrayList<>(items.size());

        for (PaperQuestionItem item : items) {
            Question q = item.question();
            int itemScore = item.score() == null ? 0 : item.score();

            ExamReportItemVO ri = new ExamReportItemVO();
            ri.setQuestionId(q.getId());
            ri.setQuestionType(q.getQuestionType());
            ri.setContent(q.getContent());
            ri.setOptions(optionMap.getOrDefault(q.getId(), List.of()));
            ri.setCorrectAnswer(q.getAnswer());
            ri.setAnalysis(q.getAnalysis());

            if (!userAnswers.containsKey(q.getId())) {
                unansweredCount++;
                ri.setUserAnswer(null);
                ri.setIsCorrect(false);
            } else {
                boolean correct = Boolean.TRUE.equals(correctness.get(q.getId()));
                ri.setUserAnswer(userAnswers.get(q.getId()));
                ri.setIsCorrect(correct);
                if (correct) {
                    correctCount++;
                    score += itemScore;
                } else {
                    wrongCount++;
                }
            }
            reportItems.add(ri);
        }

        boolean isPass = score >= exam.getPassScore();

        ExamReportVO report = new ExamReportVO();
        report.setExamId(exam.getId());
        report.setScore(score);
        report.setTotalScore(exam.getTotalScore());
        report.setPassScore(exam.getPassScore());
        report.setIsPass(isPass);
        report.setCorrectCount(correctCount);
        report.setWrongCount(wrongCount);
        report.setUnansweredCount(unansweredCount);
        report.setItems(reportItems);
        return report;
    }

    /* ============================== 辅助 ============================== */

    /** 按试卷顺序（sortOrder 升序，null 排最后）排好；返回可排序的新列表。 */
    private static List<PaperQuestionItem> sortByOrder(List<PaperQuestionItem> paperQuestions) {
        List<PaperQuestionItem> items = new ArrayList<>(paperQuestions);
        items.sort(Comparator.comparing(PaperQuestionItem::sortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    /** 学员科目进度 upsert：不存在则新建，存在则只改 mock_pass_count 与 last_mock_score。 */
    private void applySubjectStatus(Long userId, String subjectCode, boolean isPass, int score) {
        StudentSubjectStatus status = studentSubjectStatusMapper.selectOne(
                new LambdaQueryWrapper<StudentSubjectStatus>()
                        .eq(StudentSubjectStatus::getStudentId, userId)
                        .eq(StudentSubjectStatus::getSubjectCode, subjectCode));
        if (status == null) {
            StudentSubjectStatus fresh = new StudentSubjectStatus();
            fresh.setStudentId(userId);
            fresh.setSubjectCode(subjectCode);
            // status 交给数据库默认值 NOT_STARTED
            fresh.setMockPassCount(isPass ? 1 : 0);
            fresh.setLastMockScore(score);
            studentSubjectStatusMapper.insert(fresh);
        } else {
            int prev = status.getMockPassCount() == null ? 0 : status.getMockPassCount();
            status.setMockPassCount(isPass ? prev + 1 : 0);
            status.setLastMockScore(score);
            studentSubjectStatusMapper.updateById(status);
        }
    }
}
