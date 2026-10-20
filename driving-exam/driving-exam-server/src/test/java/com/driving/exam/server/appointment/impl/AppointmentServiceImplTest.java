package com.driving.exam.server.appointment.impl;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.dto.AppointmentCreateDTO;
import com.driving.exam.pojo.entity.Appointment;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.enums.AppointmentStatus;
import com.driving.exam.server.mapper.AppointmentMapper;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AppointmentServiceImpl} 创建预约的锁顺序与冲突分支测试。
 *
 * <p>这是 Mockito 单元测试：验证“先锁教练行、再查绑定/冲突、最后插入”的调用顺序和
 * 冲突/未绑定分支，不声称替代真实 MySQL 的并发事务测试。</p>
 */
@DisplayName("预约创建锁顺序与冲突规则（离线）")
class AppointmentServiceImplTest {

    private static final long STUDENT_ID = 7L;
    private static final long COACH_ID = 20L;

    private AppointmentMapper appointmentMapper;
    private CoachStudentMapper coachStudentMapper;
    private UserMapper userMapper;
    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        appointmentMapper = mock(AppointmentMapper.class);
        coachStudentMapper = mock(CoachStudentMapper.class);
        userMapper = mock(UserMapper.class);
        service = new AppointmentServiceImpl(appointmentMapper, coachStudentMapper, userMapper);
    }

    @Test
    @DisplayName("正常预约：先锁教练行，再查绑定、查冲突、插入")
    void createLocksCoachBeforeConflictCheck() {
        when(userMapper.selectByIdForUpdate(COACH_ID)).thenReturn(coach());
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);

        service.createAppointment(STUDENT_ID, dto("2026-10-21", "08:00-10:00"));

        var order = inOrder(userMapper, coachStudentMapper, appointmentMapper);
        order.verify(userMapper).selectByIdForUpdate(COACH_ID);
        order.verify(coachStudentMapper).selectCount(any());
        order.verify(appointmentMapper).selectCount(any());
        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        order.verify(appointmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getCoachId()).isEqualTo(COACH_ID);
        assertThat(captor.getValue().getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.PENDING.name());
    }

    @Test
    @DisplayName("同一时段已有占用预约：返回 3002，不插入")
    void duplicateSlotRejected() {
        when(userMapper.selectByIdForUpdate(COACH_ID)).thenReturn(coach());
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(appointmentMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.createAppointment(STUDENT_ID, dto("2026-10-21", "08:00-10:00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.APPOINTMENT_TIME_CONFLICT.getCode()));
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    @Test
    @DisplayName("不同日期/时段：冲突查询为 0 时允许创建")
    void differentSlotAllowed() {
        when(userMapper.selectByIdForUpdate(COACH_ID)).thenReturn(coach());
        when(coachStudentMapper.selectCount(any())).thenReturn(1L);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);

        service.createAppointment(STUDENT_ID, dto("2026-10-22", "10:00-12:00"));

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getAppointmentDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        assertThat(captor.getValue().getTimeSlot()).isEqualTo("10:00-12:00");
    }

    @Test
    @DisplayName("未绑定教练：返回 3003，不查冲突、不插入")
    void unboundCoachRejected() {
        when(userMapper.selectByIdForUpdate(COACH_ID)).thenReturn(coach());
        when(coachStudentMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.createAppointment(STUDENT_ID, dto("2026-10-21", "08:00-10:00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.STUDENT_NOT_BOUND.getCode()));
        verify(appointmentMapper, never()).selectCount(any());
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    @Test
    @DisplayName("教练账号被禁用：返回 1102，不查冲突、不插入")
    void disabledCoachRejected() {
        User coach = coach();
        coach.setStatus(0);
        when(userMapper.selectByIdForUpdate(COACH_ID)).thenReturn(coach);

        assertThatThrownBy(() -> service.createAppointment(STUDENT_ID, dto("2026-10-21", "08:00-10:00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.ACCOUNT_DISABLED.getCode()));
        verify(appointmentMapper, never()).selectCount(any());
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    @Test
    @DisplayName("取消 PENDING：状态转为 CANCELLED，时段按现有规则释放")
    void cancelReleasesSlot() {
        Appointment pending = new Appointment();
        pending.setId(99L);
        pending.setStudentId(STUDENT_ID);
        pending.setCoachId(COACH_ID);
        pending.setStatus(AppointmentStatus.PENDING.name());
        when(appointmentMapper.selectStudentOwnedForUpdate(99L, STUDENT_ID)).thenReturn(pending);

        service.cancelMyAppointment(STUDENT_ID, 99L);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("审批 PENDING：使用教练所属预约行锁并转为 CONFIRMED")
    void approveUsesOwnedRowLock() {
        Appointment pending = new Appointment();
        pending.setId(99L);
        pending.setStudentId(STUDENT_ID);
        pending.setCoachId(COACH_ID);
        pending.setStatus(AppointmentStatus.PENDING.name());
        when(appointmentMapper.selectCoachOwnedForUpdate(99L, COACH_ID)).thenReturn(pending);

        service.approveAppointment(COACH_ID, 99L);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED.name());
    }

    private static AppointmentCreateDTO dto(String date, String slot) {
        AppointmentCreateDTO dto = new AppointmentCreateDTO();
        dto.setCoachId(COACH_ID);
        dto.setSubjectCode("SUBJECT_2");
        dto.setAppointmentDate(LocalDate.parse(date));
        dto.setTimeSlot(slot);
        return dto;
    }

    private static User coach() {
        User coach = new User();
        coach.setId(COACH_ID);
        coach.setRole("COACH");
        coach.setStatus(1);
        return coach;
    }
}
