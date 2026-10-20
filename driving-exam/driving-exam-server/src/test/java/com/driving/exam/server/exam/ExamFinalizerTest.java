package com.driving.exam.server.exam;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.pojo.entity.AnswerRecord;
import com.driving.exam.pojo.entity.MockExam;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.server.assembler.QuestionOptionAssembler;
import com.driving.exam.server.grader.AnswerGrader;
import com.driving.exam.server.mapper.AnswerRecordMapper;
import com.driving.exam.server.mapper.MockExamAnswerMapper;
import com.driving.exam.server.mapper.MockExamMapper;
import com.driving.exam.server.mapper.StudentSubjectStatusMapper;
import com.driving.exam.server.mapper.WrongBookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExamFinalizer} 交卷结算「条件抢占」契约的单元测试。
 *
 * <h3>这是什么、不是什么</h3>
 * <p><b>是</b>：对结算核心契约（条件抢占决定谁结算；不可判分题型在抢占前整体拒绝）的
 * <b>纯 Mockito 单元测试</b>——不启动 Spring、连数据库，用 {@code mock(…Mapper.class)} 桩住 SQL 边界，
 * 断言 {@code ExamFinalizer} 在「抢到 / 未抢到 / 非法题型」三种情况下对 Mapper 的调用集合。</p>
 *
 * <p><b>不是</b>：这不是数据库级并发验证。它能证明「应用层逻辑只让抢占成功者执行结算写操作」，
 * 但<b>无法</b>证明「两条真实并发事务在 MySQL 上确实只有一个 {@code claimForSettle} 影响 1 行」——
 * 后者需要真实数据库的并发集成测试（本环境不具备可靠并发集成条件，见工作日志）。此处不将顺序单测
 * 冒充并发验证。</p>
 *
 * <p>本测试不改动任何业务规则，仅验证既有实现。</p>
 */
@DisplayName("ExamFinalizer 结算条件抢占契约（单元，Mockito）")
class ExamFinalizerTest {

    private AnswerRecordMapper answerRecordMapper;
    private WrongBookMapper wrongBookMapper;
    private MockExamMapper mockExamMapper;
    private MockExamAnswerMapper mockExamAnswerMapper;
    private StudentSubjectStatusMapper studentSubjectStatusMapper;
    private QuestionOptionAssembler questionOptionAssembler;
    private ExamFinalizer finalizer;

    @BeforeEach
    void setUp() {
        answerRecordMapper = mock(AnswerRecordMapper.class);
        wrongBookMapper = mock(WrongBookMapper.class);
        mockExamMapper = mock(MockExamMapper.class);
        mockExamAnswerMapper = mock(MockExamAnswerMapper.class);
        studentSubjectStatusMapper = mock(StudentSubjectStatusMapper.class);
        questionOptionAssembler = mock(QuestionOptionAssembler.class);
        finalizer = new ExamFinalizer(answerRecordMapper, wrongBookMapper, mockExamMapper,
                mockExamAnswerMapper, studentSubjectStatusMapper, questionOptionAssembler, new AnswerGrader());
    }

    /* ==================== 1. 不可判分题型：抢占前整体拒绝 ==================== */

    @Test
    @DisplayName("试卷含不可判分题型 → 抛 EXAM_PAPER_INVALID(2106)，且未发生任何抢占/写库")
    void ungradeablePaper_rejected_beforeClaim() {
        MockExam exam = ongoingExam();
        // 一道题型为 ESSAY（既非 SINGLE/MULTIPLE/JUDGE）的题 → 不可判分
        List<PaperQuestionItem> items = List.of(new PaperQuestionItem(question(1L, "ESSAY", "x"), 5, 1));

        BusinessException ex = catchThrowableOfType(
                () -> finalizer.finalize(exam, 30, items, Map.of(), false),
                BusinessException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(2106);
        // 关键：题型校验在抢占之前——不可判分不应产生任何状态变更
        verify(mockExamMapper, never()).claimForSettle(anyLong(), anyString());
        verify(answerRecordMapper, never()).insert(any(AnswerRecord.class));
        verify(mockExamMapper, never()).updateById(any(MockExam.class));
    }

    /* ==================== 2. 未抢到结算：静默返回，无任何写 ==================== */

    @Test
    @DisplayName("未抢到结算（claimForSettle 影响 0 行）→ 返回 null，且不写任何结算数据")
    void lostClaim_returnsNull_andWritesNothing() {
        MockExam exam = ongoingExam();
        List<PaperQuestionItem> items = List.of(new PaperQuestionItem(question(1L, "SINGLE", "A"), 5, 1));
        // 已被他方（或并发请求）结算：抢占影响 0 行
        when(mockExamMapper.claimForSettle(anyLong(), anyString())).thenReturn(0);

        ExamReportVO report = finalizer.finalize(exam, 30, items, Map.of(1L, "A"), false);

        assertThat(report).isNull();
        verify(answerRecordMapper, never()).insert(any(AnswerRecord.class));
        verify(wrongBookMapper, never()).upsertWrong(anyLong(), anyString(), anyLong());
        verify(mockExamMapper, never()).updateById(any(MockExam.class));
        verify(mockExamAnswerMapper, never()).delete(any());
    }

    /* ==================== 3. 抢到结算：仅对已作答题目写记录 ==================== */

    @Test
    @DisplayName("抢到结算（claimForSettle 影响 1 行）→ 仅对已作答题目写 answer_record，并更新考试/科目进度/清草稿")
    void winnerClaim_settlesAnsweredOnly() {
        MockExam exam = ongoingExam();
        Question q1 = question(1L, "SINGLE", "A");
        Question q2 = question(2L, "SINGLE", "B");
        List<PaperQuestionItem> items = List.of(
                new PaperQuestionItem(q1, 5, 1),
                new PaperQuestionItem(q2, 5, 2));
        when(mockExamMapper.claimForSettle(anyLong(), anyString())).thenReturn(1);

        // 报告从 answer_record 还原：该场仅 q1 已作答且正确
        AnswerRecord rec = new AnswerRecord();
        rec.setQuestionId(1L);
        rec.setUserAnswer("A");
        rec.setIsCorrect(1);
        rec.setSource("EXAM");
        rec.setExamId(100L);
        when(answerRecordMapper.selectList(any())).thenReturn(List.of(rec));
        when(questionOptionAssembler.loadByQuestionIds(any())).thenReturn(Collections.emptyMap());

        // 仅作答 q1；q2 未作答
        ExamReportVO report = finalizer.finalize(exam, 30, items, Map.of(1L, "A"), false);

        assertThat(report).isNotNull();
        assertThat(report.getScore()).isEqualTo(5);              // 仅 q1 得分
        assertThat(report.getCorrectCount()).isEqualTo(1);
        assertThat(report.getUnansweredCount()).isEqualTo(1);    // q2 计入未作答（不因跳过而失真）

        // 只写一条 answer_record，且是 q1、来源 EXAM
        ArgumentCaptor<AnswerRecord> captor = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRecordMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getQuestionId()).isEqualTo(1L);
        assertThat(captor.getValue().getSource()).isEqualTo("EXAM");

        // 考试落库一次、清草稿一次；q1 答对 → 不进错题本
        verify(mockExamMapper, times(1)).updateById(any(MockExam.class));
        verify(mockExamAnswerMapper, times(1)).delete(any());
        verify(wrongBookMapper, never()).upsertWrong(anyLong(), anyString(), anyLong());
    }

    /* ==================== 辅助 ==================== */

    private static MockExam ongoingExam() {
        MockExam exam = new MockExam();
        exam.setId(100L);
        exam.setUserId(7L);
        exam.setSubjectCode("SUBJECT_1");
        exam.setStartTime(LocalDateTime.now().minusMinutes(1));
        exam.setTotalScore(100);
        exam.setPassScore(60);
        exam.setStatus("ONGOING");
        return exam;
    }

    private static Question question(Long id, String type, String answer) {
        Question q = new Question();
        q.setId(id);
        q.setQuestionType(type);
        q.setAnswer(answer);
        q.setContent("题干" + id);
        q.setAnalysis("解析" + id);
        return q;
    }
}
