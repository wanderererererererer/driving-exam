package com.driving.exam.server.admin;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.entity.ExamPaper;
import com.driving.exam.pojo.entity.MockExam;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.server.mapper.AiChatRecordMapper;
import com.driving.exam.server.mapper.AnswerRecordMapper;
import com.driving.exam.server.mapper.AppointmentMapper;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.ExamPaperMapper;
import com.driving.exam.server.mapper.ExamPaperQuestionMapper;
import com.driving.exam.server.mapper.KnowledgePointMapper;
import com.driving.exam.server.mapper.MockExamMapper;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.mapper.QuestionOptionMapper;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.service.KnowledgePointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理员试卷/题库与考试一致性规则的离线单元测试。
 *
 * <p>验证重复题目、时长/及格分边界，以及题目/试卷被考试引用后的变更保护；
 * 不连接数据库，不替代 MySQL 唯一键或浏览器验收。</p>
 */
@DisplayName("管理员试卷/题库一致性规则（离线）")
class AdminServiceExamConsistencyTest {

    private UserMapper userMapper;
    private CoachStudentMapper coachStudentMapper;
    private KnowledgePointMapper knowledgePointMapper;
    private QuestionMapper questionMapper;
    private QuestionOptionMapper questionOptionMapper;
    private ExamPaperMapper examPaperMapper;
    private ExamPaperQuestionMapper examPaperQuestionMapper;
    private MockExamMapper mockExamMapper;
    private AppointmentMapper appointmentMapper;
    private AiChatRecordMapper aiChatRecordMapper;
    private AnswerRecordMapper answerRecordMapper;
    private KnowledgePointService knowledgePointService;
    private AdminService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        coachStudentMapper = mock(CoachStudentMapper.class);
        knowledgePointMapper = mock(KnowledgePointMapper.class);
        questionMapper = mock(QuestionMapper.class);
        questionOptionMapper = mock(QuestionOptionMapper.class);
        examPaperMapper = mock(ExamPaperMapper.class);
        examPaperQuestionMapper = mock(ExamPaperQuestionMapper.class);
        mockExamMapper = mock(MockExamMapper.class);
        appointmentMapper = mock(AppointmentMapper.class);
        aiChatRecordMapper = mock(AiChatRecordMapper.class);
        answerRecordMapper = mock(AnswerRecordMapper.class);
        knowledgePointService = mock(KnowledgePointService.class);
        service = new AdminService(userMapper, coachStudentMapper, knowledgePointMapper,
                questionMapper, questionOptionMapper, examPaperMapper, examPaperQuestionMapper,
                mockExamMapper, appointmentMapper, aiChatRecordMapper, answerRecordMapper,
                knowledgePointService, mock(PasswordEncoder.class));
    }

    @Test
    @DisplayName("试卷包含重复题目 → 1001，不插入试卷")
    void duplicatePaperQuestionsRejected() {
        Question question = enabledQuestion(1L);
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question));

        AdminDtos.PaperDTO dto = paperDto(List.of(
                paperQuestion(1L, 5, 1),
                paperQuestion(1L, 5, 2)));

        assertThatThrownBy(() -> service.createPaper(dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("试卷时长为 0 → 1001")
    void zeroDurationRejected() {
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(enabledQuestion(1L)));
        AdminDtos.PaperDTO dto = paperDto(List.of(paperQuestion(1L, 5, 1)));
        dto.setDuration(0);

        assertThatThrownBy(() -> service.createPaper(dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("及格分大于总分 → 1001")
    void passScoreAboveTotalRejected() {
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(enabledQuestion(1L)));
        AdminDtos.PaperDTO dto = paperDto(List.of(paperQuestion(1L, 5, 1)));
        dto.setPassScore(6);

        assertThatThrownBy(() -> service.createPaper(dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    @DisplayName("题目已进入考试 → 修改内容/状态/删除被拒绝")
    void questionUsedByExamIsFrozen() {
        Question question = enabledQuestion(1L);
        when(questionMapper.selectById(1L)).thenReturn(question);
        when(examPaperQuestionMapper.countExamUsageByQuestionId(1L)).thenReturn(1L);

        AdminDtos.QuestionDTO dto = new AdminDtos.QuestionDTO();
        assertThatThrownBy(() -> service.updateQuestion(1L, dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.getCode()));

        assertThatThrownBy(() -> service.setQuestionStatus(1L, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.getCode()));

        assertThatThrownBy(() -> service.deleteQuestion(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("存在进行中考试时禁用试卷 → 拒绝，避免学生失去继续考试入口")
    void disablePaperWithOngoingExamRejected() {
        ExamPaper paper = new ExamPaper();
        paper.setId(20L);
        paper.setStatus(1);
        when(examPaperMapper.selectById(20L)).thenReturn(paper);
        when(mockExamMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.setPaperStatus(20L, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.getCode()));
    }

    private static Question enabledQuestion(long id) {
        Question question = new Question();
        question.setId(id);
        question.setSubjectCode("SUBJECT_1");
        question.setQuestionType("SINGLE");
        question.setContent("红灯亮时应当怎样通行？");
        question.setAnswer("A");
        question.setStatus(1);
        return question;
    }

    private static AdminDtos.PaperQuestionDTO paperQuestion(long questionId, int score, int sort) {
        AdminDtos.PaperQuestionDTO item = new AdminDtos.PaperQuestionDTO();
        item.setQuestionId(questionId);
        item.setScore(score);
        item.setSortOrder(sort);
        return item;
    }

    private static AdminDtos.PaperDTO paperDto(List<AdminDtos.PaperQuestionDTO> questions) {
        AdminDtos.PaperDTO dto = new AdminDtos.PaperDTO();
        dto.setSubjectCode("SUBJECT_1");
        dto.setTitle("测试试卷");
        dto.setDuration(20);
        dto.setQuestions(questions);
        return dto;
    }
}
