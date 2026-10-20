package com.driving.exam.server.integration.embedding;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * SiliconFlow（硅基流动）Embedding 配置绑定（对应 {@code application.yml} 的 {@code embedding.*}）。
 *
 * <p><b>校验策略</b>（沿用 {@code DeepSeekProperties} 的约定）：</p>
 * <ul>
 *   <li>{@code base-url} / {@code model} 用 {@code @NotBlank} 在<b>启动时</b>校验——写错即配置错误，早失败；</li>
 *   <li>{@code api-key} <b>不做启动校验</b>：它来自环境变量 {@code SILICONFLOW_API_KEY}（部署相关），
 *       若缺 Key 就让整个应用起不来，会连累与 RAG 无关的功能。改由 {@code EmbeddingClient} 在<b>调用前</b>判空，
 *       缺失则抛业务异常并记 ERROR 日志；</li>
 *   <li>数值项（超时）给了<b>代码默认值</b>，yml 可覆盖——缺配置也能跑。</li>
 * </ul>
 *
 * <p><b>环境变量读取方式</b>：与项目 {@code DEEPSEEK_API_KEY} 一致——在配置里用
 * {@code embedding.api-key: ${SILICONFLOW_API_KEY:}} 占位（见 {@code application.yml.example}），
 * 真实 Key 只置于本机环境变量，<b>不写入源码/测试/提交模板</b>。</p>
 *
 * <p><b>安全红线</b>：{@code apiKey} 用 {@code @ToString.Exclude} 排除，防止 Lombok 生成的
 * {@code toString()} 把密钥打进日志。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /** API Key（来自环境变量 SILICONFLOW_API_KEY；**不参与启动校验**）。 */
    @ToString.Exclude
    private String apiKey;

    /** 接口基址（客户端会追加 {@code /embeddings}），默认 SiliconFlow v1。 */
    @NotBlank(message = "embedding.base-url 不能为空")
    private String baseUrl = "https://api.siliconflow.cn/v1";

    /** 模型 ID（bge-m3）。 */
    @NotBlank(message = "embedding.model 不能为空")
    private String model = "BAAI/bge-m3";

    /** 向量返回编码格式：{@code float}（默认）或 {@code base64}。 */
    private String encodingFormat = "float";

    /** 建连超时（毫秒）。 */
    private int connectTimeout = 5000;

    /** 读超时（毫秒）；Embedding 为批量同步调用，给 30s 余量。 */
    private int readTimeout = 30000;
}
