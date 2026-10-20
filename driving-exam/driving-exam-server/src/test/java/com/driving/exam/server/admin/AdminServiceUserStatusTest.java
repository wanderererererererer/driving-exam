package com.driving.exam.server.admin;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 管理员用户详情与状态参数校验（离线）。 */
@DisplayName("管理员用户状态与详情（离线）")
class AdminServiceUserStatusTest {

    private UserMapper userMapper;
    private CoachStudentMapper coachStudentMapper;
    private AdminService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        coachStudentMapper = mock(CoachStudentMapper.class);
        service = new AdminService(
                userMapper,
                coachStudentMapper,
                mock(KnowledgePointMapper.class),
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
    @DisplayName("用户详情不存在返回 1002，存在时返回基础资料")
    void getUserDetail() {
        when(userMapper.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.getUser(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));

        User user = new User();
        user.setId(9L);
        user.setUsername("student01");
        user.setRole("STUDENT");
        user.setStatus(1);
        when(userMapper.selectById(9L)).thenReturn(user);
        assertThat(service.getUser(9L).getUsername()).isEqualTo("student01");
    }

    @Test
    @DisplayName("创建/更新用户时非法状态值被拒绝")
    void invalidStatusRejected() {
        AdminDtos.UserCreateDTO create = new AdminDtos.UserCreateDTO();
        create.setUsername("new_user");
        create.setPassword("123456");
        create.setRole("STUDENT");
        create.setStatus(2);
        assertThatThrownBy(() -> service.createUser(create))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(userMapper, never()).insert(any(User.class));

        User existing = new User();
        existing.setId(9L);
        existing.setUsername("student01");
        existing.setRole("STUDENT");
        existing.setStatus(1);
        when(userMapper.selectById(9L)).thenReturn(existing);
        AdminDtos.UserUpdateDTO update = new AdminDtos.UserUpdateDTO();
        update.setStatus(2);
        assertThatThrownBy(() -> service.updateUser(1L, 9L, update))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("setUserStatus 校验 0/1，并禁止管理员禁用自己")
    void setUserStatusRules() {
        assertThatThrownBy(() -> service.setUserStatus(1L, 9L, 2))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        assertThatThrownBy(() -> service.setUserStatus(1L, 1L, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OPERATION_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("管理员绑定：禁用账号 1102、重复有效绑定 3101、解除绑定置 0")
    void bindingRules() {
        AdminDtos.BindingCreateDTO dto = new AdminDtos.BindingCreateDTO();
        dto.setCoachId(20L);
        dto.setStudentId(9L);

        User coach = user(20L, "COACH", 1);
        User student = user(9L, "STUDENT", 0);
        when(userMapper.selectById(20L)).thenReturn(coach);
        when(userMapper.selectById(9L)).thenReturn(student);
        assertThatThrownBy(() -> service.createBinding(dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode()));

        student.setStatus(1);
        CoachStudent active = new CoachStudent();
        active.setId(30L);
        active.setCoachId(20L);
        active.setStudentId(9L);
        active.setStatus(1);
        when(coachStudentMapper.selectOne(any())).thenReturn(active);
        assertThatThrownBy(() -> service.createBinding(dto))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.STUDENT_ALREADY_BOUND.getCode()));

        when(coachStudentMapper.selectById(30L)).thenReturn(active);
        service.unbind(30L);
        assertThat(active.getStatus()).isZero();
        verify(coachStudentMapper).updateById(active);
    }

    private static User user(long id, String role, int status) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
