package com.driving.exam.server.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * DeepSeek 对话响应体（对应实测返回结构）。
 *
 * <p><b>两个必须注意的反序列化细节</b>：</p>
 * <ol>
 *   <li>所有下划线字段（{@code finish_reason} / {@code prompt_tokens} / {@code completion_tokens} /
 *       {@code total_tokens}）都要 {@link JsonProperty} 显式映射，否则**静默为 null**；</li>
 *   <li>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 必须加——
 *       服务端可能返回我们没声明的字段，不加会在反序列化时抛
 *       {@code UnrecognizedPropertyException}，**把一次成功的调用变成失败**。</li>
 * </ol>
 *
 * <p>⚠️ 注意：类级的 {@code ignoreUnknown} **不会传递到嵌套类型**，
 * 所以 {@link Choice} 与 {@link Usage} 各自也要加一份。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatResponse {

    private String id;

    private String object;

    /** 服务端返回的**实际**模型名（实测为 deepseek-flash，与请求的别名不同） */
    private String model;

    private List<Choice> choices;

    private Usage usage;

    /**
     * 取第一条回答的正文；无 choices 或消息为空时返回 {@code null}。
     * 便于调用方不必自己判空层级。
     */
    public String firstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice first = choices.get(0);
        return first.getMessage() == null ? null : first.getMessage().getContent();
    }

    /** 单条候选（非流式下通常只有一条） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {

        private Integer index;

        private ChatMessage message;

        /** 结束原因，如 stop / length（**下划线，必须显式映射**） */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /** Token 用量（供 Day 13 落库） */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
