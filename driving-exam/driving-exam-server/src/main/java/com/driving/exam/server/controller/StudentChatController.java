package com.driving.exam.server.controller;

import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.pojo.dto.ChatSendDTO;
import com.driving.exam.pojo.dto.StudentChatSessionCreateDTO;
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
 * 学员端 · 教练聊天接口。
 *
 * <p>身份由 JWT 拦截器写入 request，Controller 不接收 senderId/senderRole。
 * 角色鉴权由 {@code PathRoleRule} 在拦截器层完成（/api/student/** 仅 STUDENT）。</p>
 */
@RestController
@RequestMapping("/api/student/chat")
public class StudentChatController {

    private final ChatService chatService;

    public StudentChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取或创建与指定教练的会话。
     * POST /api/student/chat/sessions
     *
     * <p>必须存在有效绑定；无有效绑定返回 3102。重复调用返回同一会话。</p>
     */
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionVO> getOrCreateSession(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @RequestBody @Valid StudentChatSessionCreateDTO dto) {
        return ApiResponse.success(chatService.getOrCreateForStudent(studentId, dto.getCoachId()));
    }

    /**
     * 我的会话列表（按最后消息时间倒序）。
     * GET /api/student/chat/sessions
     *
     * <p>解绑后的历史会话仍返回，但 canSend=false。</p>
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionVO>> listSessions(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId) {
        return ApiResponse.success(chatService.listForStudent(studentId));
    }

    /**
     * 查询会话消息。
     * GET /api/student/chat/sessions/{id}/messages?lastId=&beforeId=&pageSize=20
     *
     * <p>均不传游标时取最近一页；lastId 用于轮询新消息，beforeId 用于加载更早消息；
     * 两者互斥。返回按 id 正序。</p>
     */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageVO>> listMessages(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @PathVariable Long id,
            @RequestParam(required = false) Long lastId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(
                chatService.messagesForStudent(studentId, id, lastId, beforeId, pageSize));
    }

    /**
     * 发送消息。
     * POST /api/student/chat/sessions/{id}/messages
     */
    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<ChatMessageVO> sendMessage(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @PathVariable Long id,
            @RequestBody @Valid ChatSendDTO dto) {
        return ApiResponse.success(chatService.sendFromStudent(studentId, id, dto));
    }

    /**
     * 将当前会话中教练发来的消息标记为已读。
     * PUT /api/student/chat/sessions/{id}/read
     */
    @PutMapping("/sessions/{id}/read")
    public ApiResponse<Void> markRead(
            @RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long studentId,
            @PathVariable Long id) {
        chatService.markReadForStudent(studentId, id);
        return ApiResponse.success();
    }
}
