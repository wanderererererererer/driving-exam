package com.driving.exam.server.coach.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.StudentSubjectStatus;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.vo.StudentVO;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学员列表的当前科目/确认状态推导测试（纯 Mockito）。
 */
@DisplayName("教练端学员列表当前科目与确认状态（单元）")
class CoachStudentServiceImplListTest {

    private static final Long COACH_ID = 100L;

    private CoachStudentMapper coachStudentMapper;
    private UserMapper userMapper;
    private StudentSubjectStatusMapper studentSubjectStatusMapper;
    private CoachStudentServiceImpl service;

    @BeforeEach
    void setUp() {
        coachStudentMapper = mock(CoachStudentMapper.class);
        userMapper = mock(UserMapper.class);
        studentSubjectStatusMapper = mock(StudentSubjectStatusMapper.class);

        service = new CoachStudentServiceImpl(
                coachStudentMapper,
                userMapper,
                studentSubjectStatusMapper,
                mock(PasswordEncoder.class),
                mock(AnalysisService.class),
                mock(WrongBookService.class),
                mock(ExamService.class),
                mock(DeepSeekClient.class));
    }

    @Test
    @DisplayName("优先 READY_FOR_EXAM，其次 LEARNING；确认状态取同一条当前科目行")
    void listCurrentSubject_priorityAndConfirmStatus() {
        Page<CoachStudent> page = pageOf(bind(1L, 101L), bind(2L, 102L));
        when(coachStudentMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(
                user(101L, "student01"), user(102L, "student02")));
        when(studentSubjectStatusMapper.selectList(any())).thenReturn(List.of(
                row(101L, "SUBJECT_1", "LEARNING", null),
                row(101L, "SUBJECT_2", "READY_FOR_EXAM", "CONFIRMED_READY"),
                row(102L, "SUBJECT_1", "NOT_STARTED", null),
                row(102L, "SUBJECT_4", "EXAM_PASSED", "NEED_HELP")));

        List<StudentVO> records = service.listMyStudents(COACH_ID, 1, 10).getRecords();

        assertThat(records).hasSize(2);
        StudentVO first = records.get(0);
        assertThat(first.getStudentId()).isEqualTo(101L);
        assertThat(first.getCurrentSubjectCode()).isEqualTo("SUBJECT_2");
        assertThat(first.getCurrentPhaseStatus()).isEqualTo("READY_FOR_EXAM");
        assertThat(first.getCoachConfirmStatus()).isEqualTo("CONFIRMED_READY");

        StudentVO second = records.get(1);
        assertThat(second.getStudentId()).isEqualTo(102L);
        assertThat(second.getCurrentSubjectCode()).isEqualTo("SUBJECT_1");
        assertThat(second.getCurrentPhaseStatus()).isEqualTo("NOT_STARTED");
        assertThat(second.getCoachConfirmStatus()).isNull();

        // 一次 IN 查询，不随学员数量逐条查询
        verify(studentSubjectStatusMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("只有 EXAM_PASSED 时不伪造当前科目")
    void onlyExamPassed_hasNoCurrentSubject() {
        Page<CoachStudent> page = pageOf(bind(3L, 103L));
        when(coachStudentMapper.selectPage(any(), any())).thenReturn(page);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(103L, "student03")));
        when(studentSubjectStatusMapper.selectList(any())).thenReturn(List.of(
                row(103L, "SUBJECT_1", "EXAM_PASSED", "CONFIRMED_READY")));

        List<StudentVO> records = service.listMyStudents(COACH_ID, 1, 10).getRecords();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCurrentSubjectCode()).isNull();
        assertThat(records.get(0).getCurrentPhaseStatus()).isNull();
        assertThat(records.get(0).getCoachConfirmStatus()).isNull();
    }

    private Page<CoachStudent> pageOf(CoachStudent... binds) {
        Page<CoachStudent> page = new Page<>(1, 10);
        page.setRecords(List.of(binds));
        page.setTotal(binds.length);
        return page;
    }

    private CoachStudent bind(Long id, Long studentId) {
        CoachStudent bind = new CoachStudent();
        bind.setId(id);
        bind.setCoachId(COACH_ID);
        bind.setStudentId(studentId);
        bind.setStatus(1);
        return bind;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRealName("学员" + id);
        return user;
    }

    private StudentSubjectStatus row(Long studentId, String subject, String status,
                                     String confirmStatus) {
        StudentSubjectStatus row = new StudentSubjectStatus();
        row.setStudentId(studentId);
        row.setSubjectCode(subject);
        row.setStatus(status);
        row.setCoachConfirmStatus(confirmStatus);
        return row;
    }
}
