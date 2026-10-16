package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.StudentBindDTO;
import com.driving.exam.pojo.dto.StudentUpdateDTO;
import com.driving.exam.pojo.vo.StudentVO;
import com.driving.exam.pojo.vo.SubjectStatusVO;
import com.driving.exam.server.coach.CoachStudentService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 教练端 · 学员管理接口。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/coach/** 仅 COACH 可访问），
 * 因此本类不再重复做权限判断。所有返回统一包在 {@code ApiResponse} 中。</p>
 *
 * <p>类级前缀 {@code /api/coach/students}，方法级只写动作后缀（项目惯例：一 Controller 一模块一前缀）。
 * 5 个接口对齐 {@code doc/api-design.md} §4.10 的「学员管理」小节。</p>
 *
 * <p><b>★ 路径变量 {@code {id}} 一律表示 {@code coach_student.id}（绑定记录 ID），
 * 不是学员的用户 ID。</b>名称统一写 {@code bindId}，让"这是哪条绑定的 ID"在签名上就无歧义；
 * 学员的 {@code studentId} 由 Service 从该绑定记录<b>反查</b>得到，<b>不接受前端传入</b>——
 * 这是防止跨教练越权读取学员数据的关口。</p>
 *
 * <p>本类只做「接收请求 → 参数校验（{@code @Valid}）→ 调 Service → 封装响应」；
 * 归属校验、防 N+1、事务等一律在 {@code CoachStudentService} 内完成。</p>
 *
 * <p>设计依据：{@code doc/coach-student-design.md}（Step 0 定稿）。</p>
 */
@RestController
@RequestMapping("/api/coach/students")
public class CoachStudentController {

    private final CoachStudentService coachStudentService;

    public CoachStudentController(CoachStudentService coachStudentService) {
        this.coachStudentService = coachStudentService;
    }

    /**
     * 1. 我的学员列表（教练名下有效绑定的学员，分页）。
     * GET /api/coach/students?pageNum=1&pageSize=10
     *
     * <p>只返回<b>本人名下且仍有效</b>（{@code coach_student.status = 1}）的绑定；未绑定任何学员时
     * 返回 {@code total = 0} 的分页结果（<b>不是错误</b>）。本期<b>不提供</b>关键字 / 科目筛选。</p>
     *
     * <p>分页默认值与上限（1 / 10 / 上限 20）由 Service 统一处理，本类不重复约定。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<StudentVO>> listMyStudents(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(coachStudentService.listMyStudents(coachId, pageNum, pageSize));
    }

    /**
     * 2. 绑定或创建学员。
     * POST /api/coach/students
     *
     * <p><b>同一入口两种模式</b>：{@code username} 已存在 → 仅绑定（此时只需传 {@code username}）；
     * 不存在 → 创建学员账号并绑定（此时 {@code username / realName / phone / password} 四项必填，
     * 由 Service 判空，缺失返回 {@code 1001}）。</p>
     *
     * <p>返回 {@code Void}——前端操作成功后需<b>重新拉列表</b>（不返回新建 id）。</p>
     */
    @PostMapping
    public ApiResponse<Void> bindOrCreateStudent(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @RequestBody @Valid StudentBindDTO dto) {
        coachStudentService.bindOrCreateStudent(coachId, dto);
        return ApiResponse.success();
    }

    /**
     * 3. 修改学员的科目阶段 / 教练备注。
     * PUT /api/coach/students/{id}
     *
     * <p>{@code id} 为 {@code coach_student.id}。入参 {@code subjectCode} 必填，
     * {@code phaseStatus} 与 {@code coachRemark} <b>至少传一个</b>（都不传返回 {@code 1001}）；
     * 传 {@code null} 的字段表示<b>不修改</b>（{@code COALESCE} 语义，暂不支持清空备注）。</p>
     *
     * <p>绑定不存在 / 不属于当前教练 / 已解绑，一律返回 {@code 3102}（不泄露存在性）。</p>
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> updateStudentSubject(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id,
            @RequestBody @Valid StudentUpdateDTO dto) {
        coachStudentService.updateStudentSubject(coachId, id, dto);
        return ApiResponse.success();
    }

    /**
     * 4. 解绑学员。
     * DELETE /api/coach/students/{id}
     *
     * <p>{@code id} 为 {@code coach_student.id}。只把绑定 {@code status} 置 0（<b>软解绑</b>），
     * <b>不级联</b>取消预约、不删除任何历史数据。绑定不存在 / 不属于当前教练 / 已解绑，
     * 一律返回 {@code 3102}。</p>
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> unbindStudent(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id) {
        coachStudentService.unbindStudent(coachId, id);
        return ApiResponse.success();
    }

    /**
     * 5. 学员状态详情（各科进度，固定四科）。
     * GET /api/coach/students/{id}/status
     *
     * <p>{@code id} 为 {@code coach_student.id}。返回 **4 项**（{@code SUBJECT_1}~{@code SUBJECT_4}，顺序固定）；
     * 库中缺失的科目由 Service <b>在内存中</b>补 {@code NOT_STARTED}——<b>查询不写库</b>。</p>
     *
     * <p>绑定不存在 / 不属于当前教练 / 已解绑，一律返回 {@code 3102}。</p>
     */
    @GetMapping("/{id}/status")
    public ApiResponse<List<SubjectStatusVO>> listStudentStatus(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id) {
        return ApiResponse.success(coachStudentService.listStudentStatus(coachId, id));
    }
}
