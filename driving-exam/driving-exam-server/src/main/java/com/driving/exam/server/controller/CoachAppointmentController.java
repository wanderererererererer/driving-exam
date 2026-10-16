package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AppointmentRejectDTO;
import com.driving.exam.pojo.enums.AppointmentStatus;
import com.driving.exam.pojo.vo.AppointmentVO;
import com.driving.exam.server.appointment.AppointmentService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教练端 · 预约处理接口。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/coach/** 仅 COACH 可访问），
 * 因此本类不再重复做权限判断。所有返回统一包在 {@code ApiResponse} 中。</p>
 *
 * <p>类级前缀 {@code /api/coach/appointments}，方法级只写动作后缀（项目惯例：一 Controller 一模块一前缀）。
 * 4 个接口对齐 {@code doc/api-design.md} §4.10 的「预约处理」小节。</p>
 */
@RestController
@RequestMapping("/api/coach/appointments")
public class CoachAppointmentController {

    private final AppointmentService appointmentService;

    public CoachAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 1. 我的预约列表（教练名下的预约，分页）。
     * GET /api/coach/appointments?status=&pageNum=1&pageSize=10
     *
     * <p>{@code status} 取值为 {@code AppointmentStatus} 枚举名（**大小写敏感**，前端须传全大写；
     * 传小写会被全局异常处理器转成 1001），不传表示不过滤。
     * <b>本接口不提供科目筛选</b>（与 §4.10 一致，那里只写"可按状态筛"）。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<AppointmentVO>> listCoachAppointments(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(
                appointmentService.listCoachAppointments(coachId, status, pageNum, pageSize));
    }

    /**
     * 2. 审批通过（**仅 PENDING**，否则 3005）。
     * PUT /api/coach/appointments/{id}/approve
     */
    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approveAppointment(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id) {
        appointmentService.approveAppointment(coachId, id);
        return ApiResponse.success();
    }

    /**
     * 3. 拒绝预约（**仅 PENDING**，否则 3005）。
     * PUT /api/coach/appointments/{id}/reject
     *
     * <p>入参 {@code AppointmentRejectDTO} 只有"拒绝原因"一个字段，可为空：
     * 传 {@code {}} 即不填原因；**完全不传 body 会返回 1001**（缺请求体属参数错误）。
     * 拒绝落 {@code REJECTED} 状态，原因写入 {@code coach_remark}。</p>
     */
    @PutMapping("/{id}/reject")
    public ApiResponse<Void> rejectAppointment(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id,
            @RequestBody @Valid AppointmentRejectDTO dto) {
        appointmentService.rejectAppointment(coachId, id, dto.getCoachRemark());
        return ApiResponse.success();
    }

    /**
     * 4. 标记完成（**仅 CONFIRMED**，否则 3005）。
     * PUT /api/coach/appointments/{id}/complete
     */
    @PutMapping("/{id}/complete")
    public ApiResponse<Void> completeAppointment(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id) {
        appointmentService.completeAppointment(coachId, id);
        return ApiResponse.success();
    }
}
