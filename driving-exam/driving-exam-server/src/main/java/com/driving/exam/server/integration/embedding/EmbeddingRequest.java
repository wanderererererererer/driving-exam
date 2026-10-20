package com.driving.exam.server.integration.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * SiliconFlow {@code /embeddings} 请求体。
 *
 * <p>字段命名与官方 OpenAI 兼容协议一致：{@code model / input / encoding_format}。
 * snake_case 用 {@link JsonProperty} 显式映射（与 {@code ChatRequest.top_p} 同一写法）。</p>
 *
 * <p><b>注意</b>：{@code input} 使用<b>字符串数组</b>以支持批量（单次上限由客户端 {@code MAX_BATCH_SIZE} 控制）；
 * {@code bge-m3} <b>不支持</b> {@code dimensions} 参数，故请求体不包含该字段。</p>
 */
@Data
public class EmbeddingRequest {

    /** 模型 ID，如 {@code BAAI/bge-m3}。 */
    private String model;

    /** 待向量化的文本列表。 */
    private List<String> input;

    /** 编码格式：{@code float} / {@code base64}。 */
    @JsonProperty("encoding_format")
    private String encodingFormat;
}
