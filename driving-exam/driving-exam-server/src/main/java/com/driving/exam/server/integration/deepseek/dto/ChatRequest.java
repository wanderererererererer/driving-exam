package com.driving.exam.server.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek 对话请求体（OpenAI 兼容格式）。
 *
 * <p><b>序列化提醒</b>：{@code top_p} 必须用 {@link JsonProperty} 显式映射——
 * 我们的 Jackson 命名策略是驼峰，若字段名直接叫 {@code topP}，
 * 序列化出去会变成 {@code "topP"}（服务端不识别，静默取默认值），
 * **不报错、最难查**。</p>
 */
@Data
public class ChatRequest {

    /** 模型 ID，如 deepseek-chat */
    private String model;

    /** 消息数组（支持多轮：按 system → user → assistant 顺序追加） */
    private List<ChatMessage> messages;

    /** 采样温度 */
    private Double temperature;

    /** 核采样（**必须显式映射为 top_p**） */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 是否流式输出。
     *
     * <p>第一版固定 {@code false}（非流式）；这是为**流式升级预留的开关**，
     * 详见 {@code doc/deepseek-integration.md} §七。</p>
     */
    private Boolean stream = false;

    /**
     * 构造一个非流式请求。
     */
    public static ChatRequest of(String model, List<ChatMessage> messages,
                                 Double temperature, Double topP) {
        ChatRequest request = new ChatRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setTemperature(temperature);
        request.setTopP(topP);
        request.setStream(false);
        return request;
    }
}
