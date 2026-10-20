package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.enums.AppointmentStatus;
import com.driving.exam.pojo.vo.AppointmentVO;
import com.driving.exam.server.appointment.AppointmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员端 · 预约查看接口（只读）。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（{@code /api/admin/**} 仅 ADMIN）。
 * 本接口不提供任何审批、拒绝、完成或取消操作；管理员只能查看全量预约。</p>
 *
 * <p>状态参数取 {@code AppointmentStatus} 枚举名（全大写），不传表示全部；
 * 排序固定为 {@code create_time DESC, id DESC}，分页上限 20。</p>
 */
@RestController
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    public AdminAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 全量预约列表（只读）。
     * GET /api/admin/appointments?status=&pageNum=1&pageSize=10
     */
    @GetMapping
    public ApiResponse<PageResult<AppointmentVO>> listAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(appointmentService.listAllAppointments(status, pageNum, pageSize));
    }
}
