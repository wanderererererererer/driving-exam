package com.driving.exam.server.integration.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * SiliconFlow {@code /embeddings} 响应体（OpenAI 兼容）。
 *
 * <p>关键字段：{@code data[].embedding}（向量）与 {@code data[].index}（对应输入下标）；
 * 另含 {@code model} 与 {@code usage}。snake_case 用 {@link JsonProperty} 显式映射。</p>
 */
@Data
public class EmbeddingResponse {

    /** 服务端实际返回的模型名。 */
    private String model;

    /** 向量结果列表（每条对应一个输入）。 */
    private List<EmbeddingData> data;

    /** token 用量（可能为空）。 */
    private Usage usage;

    /** 单条向量结果。 */
    @Data
    public static class EmbeddingData {

        /** 对应输入的下标（用于恢复顺序）。 */
        private Integer index;

        /** 向量（浮点数组）。 */
        private List<Double> embedding;
    }

    /** token 用量。 */
    @Data
    public static class Usage {

        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
