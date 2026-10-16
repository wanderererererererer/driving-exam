package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.TrendPointVO;
import com.driving.exam.server.analysis.AnalysisService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员端 · 学情分析接口。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（{@code /api/student/**} 仅 STUDENT 可访问），
 * 本类不再重复判断。userId 一律取自 JWT 解析后写入的 request 属性。</p>
 *
 * <p>3 个接口对齐 {@code doc/api-design.md} §4.6 与 {@code doc/analysis-design.md} §三。</p>
 */
@RestController
@RequestMapping("/api/student/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 1. 学情总览。
     * GET /api/student/analysis/overview?subjectCode=SUBJECT_1
     *
     * <p>{@code correctRate} 在用户**一道都没答过**时为 null（前端显示"未开始"）；
     * {@code mockExam*} 三字段为 Day16~17 预留，当前恒为 null。</p>
     */
    @GetMapping("/overview")
    public ApiResponse<AnalysisOverviewVO> overview(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String subjectCode) {
        return ApiResponse.success(analysisService.overview(userId, subjectCode));
    }

    /**
     * 2. 知识点掌握度（含子孙聚合，按掌握度升序；未练习的知识点不返回）。
     * GET /api/student/analysis/knowledge?subjectCode=SUBJECT_1
     */
    @GetMapping("/knowledge")
    public ApiResponse<List<KPStatVO>> knowledge(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String subjectCode) {
        return ApiResponse.success(analysisService.knowledge(userId, subjectCode));
    }

    /**
     * 3. 成绩趋势（只含"有答题"的日期，不补空白日）。
     * GET /api/student/analysis/trend?subjectCode=SUBJECT_1&days=14
     *
     * <p>{@code days} 默认 14，后端上限 90。</p>
     */
    @GetMapping("/trend")
    public ApiResponse<List<TrendPointVO>> trend(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String subjectCode,
            @RequestParam(defaultValue = "14") int days) {
        return ApiResponse.success(analysisService.trend(userId, subjectCode, days));
    }
}
