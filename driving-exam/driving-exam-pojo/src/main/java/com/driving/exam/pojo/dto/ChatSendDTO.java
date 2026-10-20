package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 发送聊天消息请求。发送者身份只从 JWT 获取，请求体不包含 senderId/senderRole。
 */
@Data
public class ChatSendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "消息内容不能为空")
    private String content;
}
