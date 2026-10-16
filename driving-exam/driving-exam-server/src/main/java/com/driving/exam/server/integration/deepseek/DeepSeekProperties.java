package com.driving.exam.server.integration.deepseek;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * DeepSeek 配置绑定（对应 {@code application.yml} 的 {@code deepseek.*}）。
 *
 * <p><b>校验策略（有意为之）</b>：</p>
 * <ul>
 *   <li>{@code base-url} / {@code model} 用 {@code @NotBlank} 在**启动时**校验——
 *       这两项写错一定是配置错误，早失败早发现；</li>
 *   <li>{@code api-key} <b>刻意不做启动校验</b>：它来源于环境变量（部署相关），
 *       若因缺 Key 就让整个应用起不来，会连累与 AI 无关的功能（登录 / 刷题 / 错题本）。
 *       改由 {@code DeepSeekClient} 在**调用前**判空，缺失则抛业务异常并记 ERROR 日志；</li>
 *   <li>数值项（温度 / top_p / 超时）给了**代码默认值**，yml 可覆盖——缺配置也能跑。</li>
 * </ul>
 *
 * <p><b>安全红线</b>：{@code apiKey} 用 {@code @ToString.Exclude} 排除，
 * 防止 Lombok 生成的 {@code toString()} 把密钥打进日志。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    /** API Key（来自环境变量 DEEPSEEK_API_KEY；**不参与启动校验**） */
    @ToString.Exclude
    private String apiKey;

    /** 接口基址，如 https://api.deepseek.com */
    @NotBlank(message = "deepseek.base-url 不能为空")
    private String baseUrl;

    /** 模型 ID（用官方通用别名 deepseek-chat） */
    @NotBlank(message = "deepseek.model 不能为空")
    private String model;

    /** 采样温度 */
    private Double temperature = 1.0;

    /** 核采样 */
    private Double topP = 1.0;

    /** 建连超时（毫秒） */
    private int connectTimeout = 5000;

    /** 读超时（毫秒）：非流式下需覆盖整段生成时间，故默认 60s */
    private int readTimeout = 60000;
}
