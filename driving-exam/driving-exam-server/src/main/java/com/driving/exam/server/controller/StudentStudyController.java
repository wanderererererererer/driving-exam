package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AnswerSubmitDTO;
import com.driving.exam.pojo.vo.AnswerResultVO;
import com.driving.exam.pojo.vo.KPTreeNodeVO;
import com.driving.exam.pojo.vo.QuestionDetailVO;
import com.driving.exam.pojo.vo.QuestionVO;
import com.driving.exam.server.enums.AnswerSource;
import com.driving.exam.server.interceptor.JwtInterceptor;
import com.driving.exam.server.service.KnowledgePointService;
import com.driving.exam.server.service.PracticeService;
import com.driving.exam.server.service.QuestionService;
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
 * 学员端 · 刷题接口（顺序 / 章节 / 随机练习 + 判分）。
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/student/** 仅 STUDENT 可访问），
 * 因此本类不再重复做权限判断。所有返回统一包在 {@code ApiResponse} 中。</p>
 */
@RestController
@RequestMapping("/api/student/study")
public class StudentStudyController {

    private final KnowledgePointService knowledgePointService;
    private final QuestionService questionService;
    private final PracticeService practiceService;

    public StudentStudyController(KnowledgePointService knowledgePointService,
                                  QuestionService questionService,
                                  PracticeService practiceService) {
        this.knowledgePointService = knowledgePointService;
        this.questionService = questionService;
        this.practiceService = practiceService;
    }

    /**
     * 1. 知识点树（章节练习导航）。
     * GET /api/student/study/knowledge-points?subjectCode=SUBJECT_1
     */
    @GetMapping("/knowledge-points")
    public ApiResponse<List<KPTreeNodeVO>> getKnowledgePoints(@RequestParam String subjectCode) {
        return ApiResponse.success(knowledgePointService.getKnowledgeTree(subjectCode));
    }

    /**
     * 2. 顺序练习：按题目 id 升序分页。
     * GET /api/student/study/questions/sequence?subjectCode=SUBJECT_1&pageNum=1&pageSize=10
     */
    @GetMapping("/questions/sequence")
    public ApiResponse<PageResult<QuestionVO>> getSequenceQuestions(@RequestParam String subjectCode,
                                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(questionService.getSequenceQuestions(subjectCode, pageNum, pageSize));
    }

    /**
     * 3. 章节练习：按知识点（含全部子孙知识点）分页。
     * GET /api/student/study/questions/chapter?knowledgePointId=1&pageNum=1&pageSize=10
     */
    @GetMapping("/questions/chapter")
    public ApiResponse<PageResult<QuestionVO>> getChapterQuestions(@RequestParam Long knowledgePointId,
                                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                                   @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(questionService.getChapterQuestions(knowledgePointId, pageNum, pageSize));
    }

    /**
     * 4. 随机练习：从该科目随机抽取 count 道题。
     * GET /api/student/study/questions/random?subjectCode=SUBJECT_1&count=10
     */
    @GetMapping("/questions/random")
    public ApiResponse<List<QuestionVO>> getRandomQuestions(@RequestParam String subjectCode,
                                                            @RequestParam int count) {
        return ApiResponse.success(questionService.getRandomQuestions(subjectCode, count));
    }

    /**
     * 5. 题目详情（单题，含选项与知识点名称；不含答案与解析）。
     * GET /api/student/study/questions/{id}
     *
     * <p>注意：Spring MVC 下字面路径（sequence / chapter / random）优先于路径变量 {id} 匹配，
     * 因此两者不会冲突。</p>
     */
    @GetMapping("/questions/{id}")
    public ApiResponse<QuestionDetailVO> getQuestionDetail(@PathVariable Long id) {
        return ApiResponse.success(questionService.getQuestionDetail(id));
    }

    /**
     * 6. 提交作答：判分 → 写答题记录 → 答错归集错题本。
     * POST /api/student/study/answer
     *
     * <p>userId 取自 JWT 解析后写入的 request 属性，不采信前端传参。</p>
     */
    @PostMapping("/answer")
    public ApiResponse<AnswerResultVO> submitAnswer(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody @Valid AnswerSubmitDTO dto) {
        return ApiResponse.success(practiceService.submitAnswer(userId, dto, AnswerSource.PRACTICE));
    }
}
