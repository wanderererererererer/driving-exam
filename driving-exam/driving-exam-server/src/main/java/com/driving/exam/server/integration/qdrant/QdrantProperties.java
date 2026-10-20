package com.driving.exam.server.integration.qdrant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Qdrant 向量库配置绑定（对应 {@code application.yml} 的 {@code qdrant.*}）。
 *
 * <p><b>校验策略</b>（沿用 {@code DeepSeekProperties}/{@code EmbeddingProperties} 约定）：</p>
 * <ul>
 *   <li>{@code base-url} / {@code collection} 用 {@code @NotBlank} 在启动时校验；</li>
 *   <li>{@code api-key} <b>可选</b>：本地 Qdrant 可无鉴权，故不做启动校验；配置时才发送认证头；</li>
 *   <li>{@code vector-size} <b>没有代码默认值</b>（{@code null} 表示“尚未配置”）——<b>不得</b>用猜测值
 *       （如 1024）冒充已验证维度。若提供，则必须 ≥ 1。未配置时客户端<b>可正常加载</b>，但
 *       {@code QdrantClient} 的<b>建集合与写入都会被拒绝</b>（见客户端约束）。</li>
 * </ul>
 *
 * <p><b>安全红线</b>：{@code apiKey} 用 {@code @ToString.Exclude} 排除，防止 {@code toString()} 泄漏。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {

    /** Qdrant REST 基址（默认本地）。 */
    @NotBlank(message = "qdrant.base-url 不能为空")
    private String baseUrl = "http://localhost:6333";

    /** API Key（可选；本地无鉴权时留空）。真实值仅置于环境变量 QDRANT_API_KEY。 */
    @ToString.Exclude
    private String apiKey;

    /** collection 名称。 */
    @NotBlank(message = "qdrant.collection 不能为空")
    private String collection = "driving_exam_kb";

    /**
     * 向量维度（<b>无默认值</b>；{@code null} = 未配置）。
     *
     * <p>⚠️ <b>不得</b>把文档建议值（如 1024）当作已验证维度写死。真实维度须由 Embedding 实测确认后再配置。</p>
     */
    @Min(value = 1, message = "qdrant.vector-size 必须 ≥ 1")
    private Integer vectorSize;

    /** 距离度量（Cosine / Dot / Euclid）。 */
    private String distance = "Cosine";

    /** 建连超时（毫秒）。 */
    private int connectTimeout = 5000;

    /** 读超时（毫秒）。 */
    private int readTimeout = 30000;
}
