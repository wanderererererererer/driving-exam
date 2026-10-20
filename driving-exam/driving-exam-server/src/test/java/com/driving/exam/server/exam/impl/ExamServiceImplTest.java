package com.driving.exam.server.exam.impl;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.ExamAnswerDTO;
import com.driving.exam.pojo.dto.ExamSubmitDTO;
import com.driving.exam.pojo.entity.ExamPaper;
import com.driving.exam.pojo.entity.ExamPaperQuestion;
import com.driving.exam.pojo.entity.MockExam;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.server.enums.ExamStatus;
import com.driving.exam.server.exam.ExamFinalizer;
import com.driving.exam.server.grader.AnswerGrader;
import com.driving.exam.server.mapper.ExamPaperMapper;
import com.driving.exam.server.mapper.ExamPaperQuestionMapper;
import com.driving.exam.server.mapper.MockExamAnswerMapper;
import com.driving.exam.server.mapper.MockExamMapper;
import com.driving.exam.server.service.QuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ExamServiceImpl} 考试归属、答案归属和超时收尾的离线单元测试。
 *
 * <p>使用 Mockito 模拟 Mapper/Service，不连数据库；重点验证服务端的业务边界，
 * 不把本测试当作 MySQL 并发或浏览器验收。</p>
 */
@DisplayName("模拟考试 Service 归属与超时规则（离线）")
class ExamServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long EXAM_ID = 100L;
    private static final long PAPER_ID = 20L;

    private ExamPaperMapper examPaperMapper;
    private ExamPaperQuestionMapper examPaperQuestionMapper;
    private MockExamMapper mockExamMapper;
    private MockExamAnswerMapper mockExamAnswerMapper;
    private ExamFinalizer examFinalizer;
    private QuestionService questionService;
    private ExamServiceImpl service;

    @BeforeEach
    void setUp() {
        examPaperMapper = mock(ExamPaperMapper.class);
        examPaperQuestionMapper = mock(ExamPaperQuestionMapper.class);
        mockExamMapper = mock(MockExamMapper.class);
        mockExamAnswerMapper = mock(MockExamAnswerMapper.class);
        examFinalizer = mock(ExamFinalizer.class);
        questionService = mock(QuestionService.class);
        service = new ExamServiceImpl(examPaperMapper, examPaperQuestionMapper, mockExamMapper,
                mockExamAnswerMapper, examFinalizer, questionService, new AnswerGrader());
    }

    @Test
    @DisplayName("非本人考试：统一返回 2104，不读取试卷和报告")
    void foreignExamRejected() {
        MockExam exam = exam(EXAM_ID, 99L, PAPER_ID, LocalDateTime.now().minusMinutes(1), "ONGOING");
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(exam);

        assertThatThrownBy(() -> service.report(USER_ID, EXAM_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EXAM_NOT_FOUND.getCode()));
        verify(examPaperMapper, never()).selectById(any());
        verify(examFinalizer, never()).buildReport(any(), anyList());
    }

    @Test
    @DisplayName("暂存答案：题目不属于当前试卷 → 1001，且不写草稿")
    void answerOutsidePaperRejected() {
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(
                exam(EXAM_ID, USER_ID, PAPER_ID, LocalDateTime.now(), "ONGOING"));
        when(examPaperMapper.selectById(PAPER_ID)).thenReturn(paper(PAPER_ID, 20, 1));
        when(examPaperQuestionMapper.selectCount(any())).thenReturn(0L);

        ExamAnswerDTO dto = answer(999L, "A");

        assertThatThrownBy(() -> service.answer(USER_ID, EXAM_ID, dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(mockExamAnswerMapper, never()).upsertAnswer(any(), any(), anyString());
    }

    @Test
    @DisplayName("交卷请求体包含试卷外题目 → 1001，不进入结算")
    void submitOutsidePaperRejected() {
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(
                exam(EXAM_ID, USER_ID, PAPER_ID, LocalDateTime.now(), "ONGOING"));
        when(examPaperMapper.selectById(PAPER_ID)).thenReturn(paper(PAPER_ID, 20, 1));
        stubPaperItems();

        ExamSubmitDTO dto = new ExamSubmitDTO();
        dto.setAnswers(List.of(answer(999L, "A")));

        assertThatThrownBy(() -> service.submit(USER_ID, EXAM_ID, dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(examFinalizer, never()).finalize(any(), any(), anyList(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("超时后的暂存答案：先按超时结算，再拒绝答案写入")
    void answerAfterTimeoutFinalizesAndRejects() {
        MockExam exam = exam(EXAM_ID, USER_ID, PAPER_ID,
                LocalDateTime.now().minusMinutes(30), "ONGOING");
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(exam);
        when(examPaperMapper.selectById(PAPER_ID)).thenReturn(paper(PAPER_ID, 20, 1));
        when(examPaperQuestionMapper.selectCount(any())).thenReturn(1L);
        stubPaperItems();
        when(examFinalizer.finalize(any(), any(), anyList(), anyMap(), anyBoolean()))
                .thenReturn(new ExamReportVO());

        assertThatThrownBy(() -> service.answer(USER_ID, EXAM_ID, answer(1L, "A")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EXAM_SUBMITTED.getCode()));
        verify(mockExamAnswerMapper, never()).upsertAnswer(any(), any(), anyString());
        verify(examFinalizer).finalize(eq(exam), eq(20), anyList(), anyMap(), eq(true));
    }

    @Test
    @DisplayName("开始考试：锁定试卷、无进行中考试时创建一场新考试")
    void startLocksPaperAndCreatesExam() {
        ExamPaper paper = paper(PAPER_ID, 20, 1);
        when(examPaperMapper.selectByIdForUpdate(PAPER_ID)).thenReturn(paper);
        when(mockExamMapper.selectList(any())).thenReturn(List.of());
        stubPaperItems();
        when(questionService.assemble(anyList())).thenReturn(List.of(new com.driving.exam.pojo.vo.QuestionVO()));
        when(mockExamAnswerMapper.selectList(any())).thenReturn(List.of());
        when(mockExamMapper.insert(any(MockExam.class))).thenAnswer(invocation -> {
            ((MockExam) invocation.getArgument(0)).setId(EXAM_ID);
            return 1;
        });

        var vo = service.start(USER_ID, PAPER_ID);

        assertThat(vo.getExamId()).isEqualTo(EXAM_ID);
        verify(examPaperMapper).selectByIdForUpdate(PAPER_ID);
        verify(mockExamMapper).insert(any(MockExam.class));
    }

    @Test
    @DisplayName("重复交卷：已结算考试直接返回既有报告，不再次结算")
    void repeatedSubmitReturnsExistingReport() {
        MockExam exam = exam(EXAM_ID, USER_ID, PAPER_ID, LocalDateTime.now().minusMinutes(5), "SUBMITTED");
        ExamPaper paper = paper(PAPER_ID, 20, 1);
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(exam);
        when(examPaperMapper.selectById(PAPER_ID)).thenReturn(paper);
        stubPaperItems();
        ExamReportVO existing = new ExamReportVO();
        when(examFinalizer.buildReport(eq(exam), anyList())).thenReturn(existing);

        ExamReportVO result = service.submit(USER_ID, EXAM_ID, new ExamSubmitDTO());

        assertThat(result).isSameAs(existing);
        verify(examFinalizer, never()).finalize(any(), any(), anyList(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("试卷停用后：已开始的考试仍可交卷，不因试卷状态被错误拒绝")
    void submitStillWorksForDisabledPaper() {
        MockExam exam = exam(EXAM_ID, USER_ID, PAPER_ID, LocalDateTime.now(), "ONGOING");
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(exam);
        when(examPaperMapper.selectById(PAPER_ID)).thenReturn(paper(PAPER_ID, 20, 0));
        stubPaperItems();
        when(mockExamAnswerMapper.selectList(any())).thenReturn(List.of());
        when(examFinalizer.finalize(any(), any(), anyList(), anyMap(), anyBoolean()))
                .thenReturn(new ExamReportVO());

        service.submit(USER_ID, EXAM_ID, new ExamSubmitDTO());

        verify(examFinalizer).finalize(eq(exam), eq(20), anyList(), anyMap(), eq(false));
    }

    @Test
    @DisplayName("定时超时扫描：对已到期考试按 TIMEOUT 结算")
    void scheduledTimeoutFinalizesExpiredExam() {
        MockExam exam = exam(EXAM_ID, USER_ID, PAPER_ID,
                LocalDateTime.now().minusMinutes(30), "ONGOING");
        when(mockExamMapper.selectExpiredOngoingIds(50)).thenReturn(List.of(EXAM_ID));
        when(mockExamMapper.selectById(EXAM_ID)).thenReturn(exam);
        when(examPaperMapper.selectById(PAPER_ID)).thenReturn(paper(PAPER_ID, 20, 1));
        stubPaperItems();
        when(mockExamAnswerMapper.selectList(any())).thenReturn(List.of());
        when(examFinalizer.finalize(any(), any(), anyList(), anyMap(), anyBoolean()))
                .thenReturn(new ExamReportVO());

        int settled = service.finalizeExpiredExams(50);

        assertThat(settled).isEqualTo(1);
        verify(examFinalizer).finalize(eq(exam), eq(20), anyList(), anyMap(), eq(true));
    }

    /* ============================== helpers ============================== */

    private void stubPaperItems() {
        ExamPaperQuestion link = new ExamPaperQuestion();
        link.setPaperId(PAPER_ID);
        link.setQuestionId(1L);
        link.setScore(5);
        link.setSortOrder(1);
        when(examPaperQuestionMapper.selectList(any())).thenReturn(List.of(link));

        Question question = new Question();
        question.setId(1L);
        question.setQuestionType("SINGLE");
        question.setContent("红灯亮时应当怎样通行？");
        question.setAnswer("A");
        when(questionService.listByIds(anyList())).thenReturn(List.of(question));
    }

    private static MockExam exam(long id, long userId, long paperId,
                                 LocalDateTime startTime, String status) {
        MockExam exam = new MockExam();
        exam.setId(id);
        exam.setUserId(userId);
        exam.setPaperId(paperId);
        exam.setSubjectCode("SUBJECT_1");
        exam.setStartTime(startTime);
        exam.setStatus(status);
        exam.setTotalScore(5);
        exam.setPassScore(3);
        return exam;
    }

    private static ExamPaper paper(long id, int duration, int status) {
        ExamPaper paper = new ExamPaper();
        paper.setId(id);
        paper.setTitle("测试试卷");
        paper.setSubjectCode("SUBJECT_1");
        paper.setDuration(duration);
        paper.setStatus(status);
        paper.setTotalScore(5);
        paper.setPassScore(3);
        return paper;
    }

    private static ExamAnswerDTO answer(long questionId, String value) {
        ExamAnswerDTO dto = new ExamAnswerDTO();
        dto.setQuestionId(questionId);
        dto.setUserAnswer(value);
        return dto;
    }
}
