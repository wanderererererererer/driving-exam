package com.driving.exam.server.coach.impl;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.StudentBindDTO;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.exam.ExamService;
import com.driving.exam.server.integration.deepseek.DeepSeekClient;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.StudentSubjectStatusMapper;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.service.WrongBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 教练绑定接口对目标学员账号状态的保护（离线）。 */
@DisplayName("教练绑定目标账号状态（离线）")
class CoachStudentServiceImplBindStatusTest {

    private CoachStudentMapper coachStudentMapper;
    private UserMapper userMapper;
    private CoachStudentServiceImpl service;

    @BeforeEach
    void setUp() {
        coachStudentMapper = mock(CoachStudentMapper.class);
        userMapper = mock(UserMapper.class);
        service = new CoachStudentServiceImpl(
                coachStudentMapper,
                userMapper,
                mock(StudentSubjectStatusMapper.class),
                mock(PasswordEncoder.class),
                mock(AnalysisService.class),
                mock(WrongBookService.class),
                mock(ExamService.class),
                mock(DeepSeekClient.class));
    }

    @Test
    @DisplayName("已存在但被禁用的学员不能被绑定/复活")
    void disabledStudentCannotBeBound() {
        User student = new User();
        student.setId(9L);
        student.setUsername("student01");
        student.setRole("STUDENT");
        student.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(student);

        StudentBindDTO dto = new StudentBindDTO();
        dto.setUsername("student01");

        assertThatThrownBy(() -> service.bindOrCreateStudent(20L, dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode()));
        verifyNoInteractions(coachStudentMapper);
    }
}
