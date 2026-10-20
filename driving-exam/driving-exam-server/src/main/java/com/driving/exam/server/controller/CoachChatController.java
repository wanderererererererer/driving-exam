package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.pojo.dto.ChatSendDTO;
import com.driving.exam.pojo.dto.CoachChatSessionCreateDTO;
import com.driving.exam.pojo.vo.ChatMessageVO;
import com.driving.exam.pojo.vo.ChatSessionVO;
import com.driving.exam.server.chat.ChatService;
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
 * 教练端 · 学员聊天接口。
 *
 * <p>身份由 JWT 拦截器写入 request，Controller 不接收 senderId/senderRole。
 * 角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/coach/** 仅 COACH）。</p>
 */
@RestController
@RequestMapping("/api/coach/chat")
public class CoachChatController {

    private final ChatService chatService;

    public CoachChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取或创建与指定学员的会话。
     * POST /api/coach/chat/sessions
     *
     * <p>必须存在有效绑定；无有效绑定返回 3102。重复调用返回同一会话。</p>
     */
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionVO> getOrCreateSession(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @RequestBody @Valid CoachChatSessionCreateDTO dto) {
        return ApiResponse.success(chatService.getOrCreateForCoach(coachId, dto.getStudentId()));
    }

    /**
     * 我的会话列表（按最后消息时间倒序）。
     * GET /api/coach/chat/sessions
     *
     * <p>解绑后的历史会话仍返回，但 canSend=false。</p>
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionVO>> listSessions(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId) {
        return ApiResponse.success(chatService.listForCoach(coachId));
    }

    /**
     * 查询会话消息。
     * GET /api/coach/chat/sessions/{id}/messages?lastId=&beforeId=&pageSize=20
     *
     * <p>均不传游标时取最近一页；lastId 用于轮询新消息，beforeId 用于加载更早消息；
     * 两者互斥。返回按 id 正序。</p>
     */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageVO>> listMessages(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id,
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(
                chatService.messagesForCoach(coachId, id, lastId, beforeId, pageSize));
    }

    /**
     * 发送消息。
     * POST /api/coach/chat/sessions/{id}/messages
     */
    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<ChatMessageVO> sendMessage(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id,
            @RequestBody @Valid ChatSendDTO dto) {
        return ApiResponse.success(chatService.sendFromCoach(coachId, id, dto));
    }

    /**
     * 将当前会话中学员发来的消息标记为已读。
     * PUT /api/coach/chat/sessions/{id}/read
     */
    @PutMapping("/sessions/{id}/read")
    public ApiResponse<Void> markRead(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long coachId,
            @PathVariable Long id) {
        chatService.markReadForCoach(coachId, id);
        return ApiResponse.success();
    }
}
