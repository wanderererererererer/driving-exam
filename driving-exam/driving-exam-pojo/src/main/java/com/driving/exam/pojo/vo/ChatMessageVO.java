package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 聊天消息项。
 */
@Data
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long sessionId;

    private Long senderId;

    private String senderRole;

    private String content;

    /** 接收方是否已读（1/0 对应 Boolean） */
    private Boolean isRead;

    private LocalDateTime createTime;
}
