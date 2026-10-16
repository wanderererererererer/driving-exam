package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.AiChatRequestDTO;
import com.driving.exam.pojo.vo.AiAnswerVO;
import com.driving.exam.pojo.vo.AiRecordVO;
import com.driving.exam.pojo.vo.AiSessionVO;
import com.driving.exam.server.ai.AiChatService;
import com.driving.exam.server.interceptor.JwtInterceptor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 学员端 · 智能问答接口。
 *
 * <p><b>会话模型</b>：一个 {@code session_id} 聚合若干轮问答；历史按<b>会话</b>组织
 * （列表 → 进入某会话看全部消息 → 可整会话删除），而非逐条记录。</p>
 *
 * <p>角色鉴权由 {@code PathRoleRule} 在拦截器层完成（{@code /api/student/**} 仅 STUDENT 可访问），
 * 本类不再重复判断。userId 一律取自 JWT 解析后写入的 request 属性。</p>
 */
@RestController
@RequestMapping("/api/student/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 1. 提问（闭环：限额 → 调 AI → 落库 → 返回）。
     * POST /api/student/ai/chat
     *
     * <p>{@code sessionId} 由前端传入（同一次对话持续复用），落库后即归属该会话。
     * AI 调用失败（4001/4002）与超限（4003）由全局异常处理器统一返回。</p>
     */
    @PostMapping("/chat")
    public ApiResponse<AiAnswerVO> chat(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody @Valid AiChatRequestDTO dto) {
        return ApiResponse.success(aiChatService.chat(userId, dto));
    }

    /**
     * 2. 会话列表（分页，按最后消息时间倒序）。
     * GET /api/student/ai/sessions?pageNum=1&pageSize=10
     *
     * <p>{@code session_id} 为空的遗留记录不出现在列表中；{@code pageSize} 后端强制上限 20。</p>
     */
    @GetMapping("/sessions")
    public ApiResponse<PageResult<AiSessionVO>> sessions(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(aiChatService.sessions(userId, pageNum, pageSize));
    }

    /**
     * 3. 某个会话的全部消息（按时间升序，供主区顺序回显）。
     * GET /api/student/ai/sessions/{sessionId}
     *
     * <p>{@code user_id} 参与 WHERE；会话不存在或不属于当前用户时返回空数组。</p>
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<List<AiRecordVO>> sessionMessages(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable String sessionId) {
        return ApiResponse.success(aiChatService.sessionMessages(userId, sessionId));
    }

    /**
     * 4. 删除整个会话（该 session_id 下的全部记录，物理删除）。
     * DELETE /api/student/ai/sessions/{sessionId}
     *
     * <p>"不存在"与"不是自己的"都返回 4004，不泄露存在性。</p>
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
            @PathVariable String sessionId) {
        aiChatService.deleteSession(userId, sessionId);
        return ApiResponse.success();
    }
}
