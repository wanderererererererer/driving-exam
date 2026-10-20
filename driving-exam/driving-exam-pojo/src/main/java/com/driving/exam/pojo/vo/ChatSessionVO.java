package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 教练-学员会话列表项。
 *
 * <p>{@code peerUserId} 是对方用户 ID；{@code canSend} 由后端按当前
 * {@code coach_student.status = 1} 计算，仅用于前端显示。</p>
 */
@Data
public class ChatSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long peerUserId;

    private String peerName;

    private String peerPhone;

    private String lastMessage;

    private LocalDateTime lastMessageTime;

    /** 当前登录角色自己的未读数 */
    private Integer unreadCount;

    private Boolean canSend;
}
