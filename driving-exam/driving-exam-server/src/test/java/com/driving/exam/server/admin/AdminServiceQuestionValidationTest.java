package com.driving.exam.server.admin;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.entity.KnowledgePoint;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 题目语义校验（单条编辑与 Excel 共用）的离线测试。 */
@DisplayName("题目语义校验（离线）")
class AdminServiceQuestionValidationTest {

    private KnowledgePointMapper knowledgePointMapper;
    private AdminService service;

    @BeforeEach
    void setUp() {
        knowledgePointMapper = mock(KnowledgePointMapper.class);
        service = new AdminService(
                mock(UserMapper.class),
                mock(CoachStudentMapper.class),
                knowledgePointMapper,
                mock(QuestionMapper.class),
                mock(QuestionOptionMapper.class),
                mock(ExamPaperMapper.class),
                mock(ExamPaperQuestionMapper.class),
                mock(MockExamMapper.class),
                mock(AppointmentMapper.class),
                mock(AiChatRecordMapper.class),
                mock(AnswerRecordMapper.class),
                mock(KnowledgePointService.class),
                mock(PasswordEncoder.class));
    }

    @Test
    @DisplayName("正常单选题目通过校验")
    void validQuestionPasses() {
        when(knowledgePointMapper.selectById(1L)).thenReturn(kp("SUBJECT_1"));
        service.validateQuestion(question("SUBJECT_1", "SINGLE", "A", List.of(option("A"), option("B")), 1));
    }

    @Test
    @DisplayName("非法科目、无效知识点、选项不足、答案越界、非法状态分别给出明确错误")
    void semanticErrorsAreExplicit() {
        assertError("SUBJECT_9", "SINGLE", "A", List.of(option("A"), option("B")), 1,
                "科目编码不合法");

        when(knowledgePointMapper.selectById(1L)).thenReturn(null);
        assertError("SUBJECT_1", "SINGLE", "A", List.of(option("A"), option("B")), 1,
                "知识点不存在或与科目不匹配");

        when(knowledgePointMapper.selectById(1L)).thenReturn(kp("SUBJECT_1"));
        assertError("SUBJECT_1", "SINGLE", "A", List.of(option("A")), 1,
                "至少需要两个选项");
        assertError("SUBJECT_1", "SINGLE", "C", List.of(option("A"), option("B")), 1,
                "单选题答案必须是一个有效选项");
        assertError("SUBJECT_1", "SINGLE", "A", List.of(option("A"), option("B")), 2,
                "状态必须为 0 或 1");
    }

    private void assertError(String subject, String type, String answer,
                             List<AdminDtos.QuestionOptionDTO> options, int status, String message) {
        AdminDtos.QuestionDTO dto = question(subject, type, answer, options, status);
        assertThatThrownBy(() -> service.validateQuestion(dto))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
                    assertThat(e.getMessage()).contains(message);
                });
    }

    private static AdminDtos.QuestionDTO question(String subject, String type, String answer,
                                                  List<AdminDtos.QuestionOptionDTO> options, int status) {
        AdminDtos.QuestionDTO dto = new AdminDtos.QuestionDTO();
        dto.setSubjectCode(subject);
        dto.setKnowledgePointId(1L);
        dto.setQuestionType(type);
        dto.setContent("红灯亮时应当怎样通行？");
        dto.setAnswer(answer);
        dto.setOptions(options);
        dto.setDifficulty(1);
        dto.setStatus(status);
        return dto;
    }

    private static AdminDtos.QuestionOptionDTO option(String key) {
        AdminDtos.QuestionOptionDTO option = new AdminDtos.QuestionOptionDTO();
        option.setOptionKey(key);
        option.setOptionContent("选项 " + key);
        return option;
    }

    private static KnowledgePoint kp(String subject) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(1L);
        kp.setSubjectCode(subject);
        return kp;
    }
}
