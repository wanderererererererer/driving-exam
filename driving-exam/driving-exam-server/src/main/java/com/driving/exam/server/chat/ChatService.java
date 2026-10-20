package com.driving.exam.server.chat;

import com.driving.exam.pojo.dto.ChatSendDTO;
import com.driving.exam.pojo.vo.ChatMessageVO;
import com.driving.exam.pojo.vo.ChatSessionVO;

import java.util.List;

/**
 * 学员-教练聊天服务。
 *
 * <p>身份参数（studentId / coachId）必须来自 JWT，由 Controller 传入；
 * 请求体中的 senderId / senderRole 一律不采信。</p>
 */
public interface ChatService {

    /**
     * 学员获取或创建与指定教练的会话；必须存在有效绑定。
     */
    ChatSessionVO getOrCreateForStudent(Long studentId, Long coachId);

    /**
     * 教练获取或创建与指定学员的会话；必须存在有效绑定。
     */
    ChatSessionVO getOrCreateForCoach(Long coachId, Long studentId);

    /**
     * 学员会话列表（含解绑后的历史会话，canSend=false）。
     */
    List<ChatSessionVO> listForStudent(Long studentId);

    /**
     * 教练会话列表（含解绑后的历史会话，canSend=false）。
     */
    List<ChatSessionVO> listForCoach(Long coachId);

    /**
     * 学员查询会话消息。lastId 与 beforeId 互斥；均不传时返回最近一页。
     */
    List<ChatMessageVO> messagesForStudent(Long studentId, Long sessionId,
                                           Long lastId, Long beforeId, Integer pageSize);

    /**
     * 教练查询会话消息。lastId 与 beforeId 互斥；均不传时返回最近一页。
     */
    List<ChatMessageVO> messagesForCoach(Long coachId, Long sessionId,
                                         Long lastId, Long beforeId, Integer pageSize);

    /**
     * 学员发送消息；必须存在有效绑定。
     */
    ChatMessageVO sendFromStudent(Long studentId, Long sessionId, ChatSendDTO dto);

    /**
     * 教练发送消息；必须存在有效绑定。
     */
    ChatMessageVO sendFromCoach(Long coachId, Long sessionId, ChatSendDTO dto);

    /**
     * 学员将当前会话中教练发来的消息标记为已读，并清零自己的未读数。
     */
    void markReadForStudent(Long studentId, Long sessionId);

    /**
     * 教练将当前会话中学员发来的消息标记为已读，并清零自己的未读数。
     */
    void markReadForCoach(Long coachId, Long sessionId);
}
