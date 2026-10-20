package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.vo.KPTreeNodeVO;
import com.driving.exam.pojo.vo.UserVO;
import com.driving.exam.server.admin.AdminDtos;
import com.driving.exam.server.admin.AdminQuestionExcelService;
import com.driving.exam.server.admin.AdminService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理员端 · 基础管理接口。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（{@code /api/admin/**} 仅 ADMIN）。
 * 本类只做参数接收与响应封装；业务规则在 {@link AdminService}。</p>
 *
 * <p>高风险约束：用户不物理删除；绑定为软解绑；题目为逻辑删除；
 * 已被模拟考试引用的试卷禁止改写题目组成。</p>
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminQuestionExcelService excelService;

    public AdminController(AdminService adminService, AdminQuestionExcelService excelService) {
        this.adminService = adminService;
        this.excelService = excelService;
    }

    /* ============================== 概览 / AI 统计 ============================== */

    @GetMapping("/dashboard")
    public ApiResponse<AdminDtos.DashboardVO> dashboard() {
        return ApiResponse.success(adminService.dashboard());
    }

    @GetMapping("/ai/stats")
    public ApiResponse<AdminDtos.AiStatsVO> aiStats() {
        return ApiResponse.success(adminService.aiStats());
    }

    /* ============================== 用户管理 ============================== */

    @GetMapping("/users")
    public ApiResponse<PageResult<UserVO>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listUsers(role, keyword, pageNum, pageSize));
    }

    /** 用户详情：供管理端编辑前读取最新资料。 */
    @GetMapping("/users/{id}")
    public ApiResponse<UserVO> getUser(@PathVariable Long id) {
        return ApiResponse.success(adminService.getUser(id));
    }

    @PostMapping("/users")
    public ApiResponse<Void> createUser(@RequestBody @Valid AdminDtos.UserCreateDTO dto) {
        adminService.createUser(dto);
        return ApiResponse.success();
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Void> updateUser(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long adminId,
            @PathVariable Long id,
            @RequestBody AdminDtos.UserUpdateDTO dto) {
        adminService.updateUser(adminId, id, dto);
        return ApiResponse.success();
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> setUserStatus(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long adminId,
            @PathVariable Long id,
            @RequestBody @Valid AdminDtos.StatusDTO dto) {
        adminService.setUserStatus(adminId, id, dto.getStatus());
        return ApiResponse.success();
    }

    /* ============================== 绑定管理 ============================== */

    @GetMapping("/bindings")
    public ApiResponse<PageResult<AdminDtos.BindingVO>> listBindings(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listBindings(status, pageNum, pageSize));
    }

    @PostMapping("/bindings")
    public ApiResponse<Void> createBinding(@RequestBody @Valid AdminDtos.BindingCreateDTO dto) {
        adminService.createBinding(dto);
        return ApiResponse.success();
    }

    @DeleteMapping("/bindings/{id}")
    public ApiResponse<Void> unbind(@PathVariable Long id) {
        adminService.unbind(id);
        return ApiResponse.success();
    }

    /* ============================== 知识点管理 ============================== */

    @GetMapping("/knowledge-points")
    public ApiResponse<List<KPTreeNodeVO>> knowledgeTree(@RequestParam String subjectCode) {
        return ApiResponse.success(adminService.knowledgeTree(subjectCode));
    }

    @PostMapping("/knowledge-points")
    public ApiResponse<Void> createKnowledgePoint(
            @RequestBody @Valid AdminDtos.KnowledgePointDTO dto) {
        adminService.createKnowledgePoint(dto);
        return ApiResponse.success();
    }

    @PutMapping("/knowledge-points/{id}")
    public ApiResponse<Void> updateKnowledgePoint(
            @PathVariable Long id,
            @RequestBody @Valid AdminDtos.KnowledgePointDTO dto) {
        adminService.updateKnowledgePoint(id, dto);
        return ApiResponse.success();
    }

    @DeleteMapping("/knowledge-points/{id}")
    public ApiResponse<Void> deleteKnowledgePoint(@PathVariable Long id) {
        adminService.deleteKnowledgePoint(id);
        return ApiResponse.success();
    }

    /* ============================== 题库管理 ============================== */

    @GetMapping("/questions")
    public ApiResponse<PageResult<AdminDtos.QuestionVO>> listQuestions(
            @RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(
                adminService.listQuestions(subjectCode, type, keyword, pageNum, pageSize));
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<AdminDtos.QuestionVO> getQuestion(@PathVariable Long id) {
        return ApiResponse.success(adminService.getQuestion(id));
    }

    @PostMapping("/questions")
    public ApiResponse<Long> createQuestion(@RequestBody @Valid AdminDtos.QuestionDTO dto) {
        return ApiResponse.success(adminService.createQuestion(dto));
    }

    @PutMapping("/questions/{id}")
    public ApiResponse<Void> updateQuestion(
            @PathVariable Long id,
            @RequestBody @Valid AdminDtos.QuestionDTO dto) {
        adminService.updateQuestion(id, dto);
        return ApiResponse.success();
    }

    @PutMapping("/questions/{id}/status")
    public ApiResponse<Void> setQuestionStatus(
            @PathVariable Long id,
            @RequestBody @Valid AdminDtos.StatusDTO dto) {
        adminService.setQuestionStatus(id, dto.getStatus());
        return ApiResponse.success();
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long id) {
        adminService.deleteQuestion(id);
        return ApiResponse.success();
    }

    @GetMapping("/questions/template")
    public ResponseEntity<byte[]> questionTemplate() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"question-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelService.template());
    }

    @PostMapping(value = "/questions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminDtos.ImportResultVO> importQuestions(
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(excelService.importQuestions(file));
    }

    /* ============================== 试卷管理 ============================== */

    @GetMapping("/papers")
    public ApiResponse<PageResult<AdminDtos.PaperVO>> listPapers(
            @RequestParam(required = false) String subjectCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(adminService.listPapers(subjectCode, pageNum, pageSize));
    }

    @GetMapping("/papers/{id}")
    public ApiResponse<AdminDtos.PaperVO> getPaper(@PathVariable Long id) {
        return ApiResponse.success(adminService.getPaper(id));
    }

    @PostMapping("/papers")
    public ApiResponse<Long> createPaper(@RequestBody @Valid AdminDtos.PaperDTO dto) {
        return ApiResponse.success(adminService.createPaper(dto));
    }

    @PutMapping("/papers/{id}")
    public ApiResponse<Void> updatePaper(
            @PathVariable Long id,
            @RequestBody @Valid AdminDtos.PaperDTO dto) {
        adminService.updatePaper(id, dto);
        return ApiResponse.success();
    }

    @PutMapping("/papers/{id}/status")
    public ApiResponse<Void> setPaperStatus(
            @PathVariable Long id,
            @RequestBody @Valid AdminDtos.StatusDTO dto) {
        adminService.setPaperStatus(id, dto.getStatus());
        return ApiResponse.success();
    }
}
