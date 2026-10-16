package com.driving.exam.server.integration.deepseek.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息（{@code messages} 数组的成员）。
 *
 * <p>请求与响应共用：请求里我们构造它，响应里从 {@code choices[].message} 反序列化回来。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 角色：system / user / assistant */
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** 角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /* ---------- 便捷构造（让 Day 13 组装请求时可读性更好） ---------- */

    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content);
    }
}
