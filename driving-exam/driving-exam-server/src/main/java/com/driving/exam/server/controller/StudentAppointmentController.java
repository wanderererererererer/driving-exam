package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AppointmentCreateDTO;
import com.driving.exam.pojo.enums.AppointmentStatus;
import com.driving.exam.pojo.vo.AppointmentVO;
import com.driving.exam.pojo.vo.CoachVO;
import com.driving.exam.server.appointment.AppointmentService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员端 · 预约接口（科二 / 科三共用）。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/student/** 仅 STUDENT 可访问），
 * 因此本类不再重复做权限判断。所有返回统一包在 {@code ApiResponse} 中。</p>
 *
 * <p>5 个接口对齐 {@code doc/api-design.md} §4.8。</p>
 */
@RestController
@RequestMapping("/api/student/appointment")
public class StudentAppointmentController {

    private final AppointmentService appointmentService;

    public StudentAppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * 1. 我的教练列表（已绑定且绑定有效的教练）。
     * GET /api/student/appointment/coaches
     *
     * <p>未绑定任何教练时返回空数组。</p>
     */
    @GetMapping("/coaches")
    public ApiResponse<List<CoachVO>> listMyCoaches(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId) {
        return ApiResponse.success(appointmentService.listMyCoaches(studentId));
    }

    /**
     * 2. 创建预约。
     * POST /api/student/appointment
     *
     * <p>入参经 {@code @Valid} 校验：教练必填、科目限科二 / 科三、日期不得早于今天、时段限固定四项。
     * 返回 {@code Void}——前端创建成功后需**重新拉列表**（不返回新建 id）。</p>
     */
    @PostMapping
    public ApiResponse<Void> createAppointment(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @RequestBody @Valid AppointmentCreateDTO dto) {
        appointmentService.createAppointment(studentId, dto);
        return ApiResponse.success();
    }

    /**
     * 3. 我的预约列表（分页）。
     * GET /api/student/appointment?status=&subjectCode=&pageNum=1&pageSize=10
     *
     * <p>{@code status} 取值为 {@code AppointmentStatus} 枚举名（**大小写敏感**，前端须传全大写；
     * 传小写会被全局异常处理器转成 1001），不传表示不过滤；{@code subjectCode} 同理不传表示不过滤。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<AppointmentVO>> listMyAppointments(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String subjectCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(
                appointmentService.listMyAppointments(studentId, status, subjectCode, pageNum, pageSize));
    }

    /**
     * 4. 预约详情。
     * GET /api/student/appointment/{id}
     *
     * <p>越权（查别人的）与不存在返回同一个 3001，不泄露存在性。</p>
     */
    @GetMapping("/{id}")
    public ApiResponse<AppointmentVO> getMyAppointment(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @PathVariable Long id) {
        return ApiResponse.success(appointmentService.getMyAppointment(studentId, id));
    }

    /**
     * 5. 取消预约（**仅 PENDING**）。
     * PUT /api/student/appointment/{id}/cancel
     *
     * <p>{@code CONFIRMED} 之后需联系教练，非 PENDING 取消会返回 3004。</p>
     */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Void> cancelMyAppointment(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @PathVariable Long id) {
        appointmentService.cancelMyAppointment(studentId, id);
        return ApiResponse.success();
    }
}
