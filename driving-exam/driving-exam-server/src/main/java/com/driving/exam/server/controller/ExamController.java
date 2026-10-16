package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.ExamAnswerDTO;
import com.driving.exam.pojo.dto.ExamJudgeDTO;
import com.driving.exam.pojo.dto.ExamSubmitDTO;
import com.driving.exam.pojo.dto.StartExamDTO;
import com.driving.exam.pojo.vo.ExamJudgeVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.pojo.vo.ExamVO;
import com.driving.exam.pojo.vo.PaperVO;
import com.driving.exam.server.exam.ExamService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学员端 · 模拟考试接口。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（{@code /api/student/**} 仅 STUDENT 可访问），
 * 本类不再重复判断。userId 一律取自 JWT 解析后写入的 request 属性。</p>
 *
 * <p>7 个接口对齐 {@code doc/api-design.md} §4.5 与 {@code doc/exam-design.md}。</p>
 */
@RestController
@RequestMapping("/api/student/exam")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /**
     * 1. 可用试卷列表（含"是否已有进行中的考试"）。
     * GET /api/student/exam/papers?subjectCode=SUBJECT_1
     */
    @GetMapping("/papers")
    public ApiResponse<List<PaperVO>> papers(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String subjectCode) {
        return ApiResponse.success(examService.papers(userId, subjectCode));
    }

    /**
     * 2. 开始考试（或断点续考）。
     * POST /api/student/exam/start
     *
     * <p>若该试卷已有进行中的考试：未超时则返回该场；已超时则先收尾再新建一场。</p>
     */
    @PostMapping("/start")
    public ApiResponse<ExamVO> start(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody @Valid StartExamDTO dto) {
        return ApiResponse.success(examService.start(userId, dto.getPaperId()));
    }

    /**
     * 3. 断点续考：取某场考试的当前状态（题目 / 剩余时间 / 已答）。
     * GET /api/student/exam/{examId}
     *
     * <p><b>名义 GET，但"发现已超时"时会触发惰性收尾（写库）</b>，返回的 {@code status} 会是 TIMEOUT。</p>
     */
    @GetMapping("/{examId}")
    public ApiResponse<ExamVO> resume(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long examId) {
        return ApiResponse.success(examService.resume(userId, examId));
    }

    /**
     * 4. 考试中暂存单题答案（不判分、不返回对错）。
     * POST /api/student/exam/{examId}/answer
     */
    @PostMapping("/{examId}/answer")
    public ApiResponse<Void> answer(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long examId,
            @RequestBody @Valid ExamAnswerDTO dto) {
        examService.answer(userId, examId, dto);
        return ApiResponse.success();
    }

    /**
     * 4.5 单题判题（考试中即时反馈答题卡正误）。
     * POST /api/student/exam/{examId}/judge
     *
     * <p>取该题**服务器已保存**的答案判正误，用与交卷同一套判分规则；<b>只回 isCorrect</b>，
     * 不回标准答案 / 解析。</p>
     */
    @PostMapping("/{examId}/judge")
    public ApiResponse<ExamJudgeVO> judge(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long examId,
            @RequestBody @Valid ExamJudgeDTO dto) {
        return ApiResponse.success(examService.judge(userId, examId, dto.getQuestionId()));
    }

    /**
     * 5. 交卷判分。
     * POST /api/student/exam/{examId}/submit
     *
     * <p>{@code dto} <b>可为空</b>（{@code required = false}）：为空则从服务端暂存草稿读取答案。
     * 幂等——已交卷时直接返回既有报告。</p>
     */
    @PostMapping("/{examId}/submit")
    public ApiResponse<ExamReportVO> submit(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long examId,
            @RequestBody(required = false) ExamSubmitDTO dto) {
        return ApiResponse.success(examService.submit(userId, examId, dto));
    }

    /**
     * 6. 成绩报告（含逐题解析）。
     * GET /api/student/exam/{examId}/report
     *
     * <p>进行中的考试无报告，会返回 {@code EXAM_ONGOING(2105)}。</p>
     */
    @GetMapping("/{examId}/report")
    public ApiResponse<ExamReportVO> report(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long examId) {
        return ApiResponse.success(examService.report(userId, examId));
    }

    /**
     * 7. 考试历史（仅已结束的考试，按交卷时间倒序分页）。
     * GET /api/student/exam/history?subjectCode=SUBJECT_1&pageNum=1&pageSize=10
     */
    @GetMapping("/history")
    public ApiResponse<PageResult<ExamRecordVO>> history(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam String subjectCode,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(examService.history(userId, subjectCode, pageNum, pageSize));
    }
}
