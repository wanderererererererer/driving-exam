package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AnswerSubmitDTO;
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.AnswerResultVO;
import com.driving.exam.pojo.vo.QuestionVO;
import com.driving.exam.pojo.vo.WrongBookItemVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import com.driving.exam.server.enums.AnswerSource;
import com.driving.exam.server.interceptor.JwtInterceptor;
import com.driving.exam.server.service.PracticeService;
import com.driving.exam.server.service.WrongBookService;
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
 * 学员端 · 错题本接口。
 *
 * <p>分类口径见 {@code doc/data-conventions.md} §8（由后端推导，前端只渲染 tab）。</p>
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/student/** 仅 STUDENT 可访问），
 * 本类不再重复判断。userId 一律取自 JWT 解析后写入的 request 属性。</p>
 */
@RestController
@RequestMapping("/api/student/wrong-book")
public class StudentWrongBookController {

    private final WrongBookService wrongBookService;
    private final PracticeService practiceService;

    public StudentWrongBookController(WrongBookService wrongBookService,
                                      PracticeService practiceService) {
        this.wrongBookService = wrongBookService;
        this.practiceService = practiceService;
    }

    /**
     * 1. 错题列表（分页，可按科目与分类过滤）。
     * GET /api/student/wrong-book?subjectCode=&category=&pageNum=1&pageSize=10
     *
     * <p>{@code category} 取值为枚举名（大小写敏感）：PENDING / HIGH_FREQUENCY / MASTERED；
     * <b>不传 = 全部</b>。传非法值会被全局异常处理器拦下，返回 1001 参数类型不匹配。</p>
     */
    @GetMapping
    public ApiResponse<PageResult<WrongBookItemVO>> list(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(required = false) String subjectCode,
            @RequestParam(required = false) WrongBookCategory category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(wrongBookService.list(userId, subjectCode, category, pageNum, pageSize));
    }

    /**
     * 2. 各分类数量统计（前端 tab 角标）。
     * GET /api/student/wrong-book/summary?subjectCode=
     */
    @GetMapping("/summary")
    public ApiResponse<WrongBookSummaryVO> summary(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(required = false) String subjectCode) {
        return ApiResponse.success(wrongBookService.summary(userId, subjectCode));
    }

    /**
     * 3. 抽取错题重练题集（优先高频错，不够再补待攻克）。
     * GET /api/student/wrong-book/retry?subjectCode=&count=10
     *
     * <p>返回的是刷题模块的 {@code QuestionVO}（含选项，<b>不含答案与解析</b>）。
     * 注意：重练作答走**独立入口**（source=RETRY），不经过 {@code /api/student/study/answer}。</p>
     */
    @GetMapping("/retry")
    public ApiResponse<List<QuestionVO>> retry(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(required = false) String subjectCode,
            @RequestParam(defaultValue = "10") int count) {
        return ApiResponse.success(wrongBookService.retry(userId, subjectCode, count));
    }

    /**
     * 4. 错题重练作答（与普通练习**唯一**的区别：答对会把该题标记为已掌握）。
     * POST /api/student/wrong-book/retry/answer
     *
     * <p>Body 同刷题作答（不含 source）。服务端强制以 {@code AnswerSource.RETRY} 调用判分编排：
     * 答对 → {@code mastered=1}；答错 → {@code wrong_count+1} 且 {@code mastered} 保持 0。</p>
     */
    @PostMapping("/retry/answer")
    public ApiResponse<AnswerResultVO> submitRetryAnswer(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody @Valid AnswerSubmitDTO dto) {
        return ApiResponse.success(practiceService.submitAnswer(userId, dto, AnswerSource.RETRY));
    }

    /**
     * 5. 标记某条错题已掌握（前端按钮文案"我掌握了"）。
     * PUT /api/student/wrong-book/{id}/mastered
     *
     * <p>不删除记录，只把 {@code mastered} 置 1，条目转入「已攻克」tab 仍可回看。
     * {@code user_id} 参与 WHERE，非法/他人记录统一返回 2003。</p>
     */
    @PutMapping("/{id}/mastered")
    public ApiResponse<Void> markMastered(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable Long id) {
        wrongBookService.markMastered(userId, id);
        return ApiResponse.success();
    }
}
