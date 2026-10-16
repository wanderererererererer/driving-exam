package com.driving.exam.server.integration.deepseek;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.server.integration.deepseek.dto.ChatMessage;
import com.driving.exam.server.integration.deepseek.dto.ChatRequest;
import com.driving.exam.server.integration.deepseek.dto.ChatResponse;
import com.driving.exam.server.integration.deepseek.dto.ChatResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * DeepSeek 大模型调用客户端 —— 本项目与 AI 服务交互的**唯一出口**。
 *
 * <p><b>职责边界</b>：只负责"把消息发出去、把回答取回来"，
 * 不涉及落库、限额、提示词拼装等业务逻辑（那些在 Day 13 的 Service 层）。</p>
 *
 * <p><b>失败一律抛 {@link BusinessException}</b>，不返回"错误对象"——
 * 与全项目 {@code ErrorCode + BusinessException + 全局异常处理器} 三件套保持一致，
 * 调用方无需写任何 {@code if (success)} 判断。</p>
 *
 * <p><b>安全红线</b>：任何日志都**不记录请求头**（{@code Authorization: Bearer <API Key>} 在其中）。</p>
 */
@Slf4j
@Component
public class DeepSeekClient {

    /** 对话补全端点（OpenAI 兼容） */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /** 日志里响应体/异常体的最大打印长度，避免刷屏 */
    private static final int LOG_BODY_MAX_LEN = 500;

    private final DeepSeekProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 构造时构建专属 RestTemplate。
     *
     * <p><b>为什么不注册成全局 Bean</b>：这个 RestTemplate 的超时是**为 LLM 长耗时调用定制的**
     * （读超时 60s），若放进全局容器，其它模块可能误注入它、带着 60s 超时去调普通接口。</p>
     *
     * <p>用 {@link RestTemplateBuilder} 而非直接 {@code new RestTemplate()}：
     * 将来若引入连接池实现（如 httpclient5），Builder 会自动切换底层工厂，**此处代码无需改动**。</p>
     */
    public DeepSeekClient(DeepSeekProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .build();
    }

    /* ============================== 对外方法 ============================== */

    /**
     * 单轮问答（Day 13 学员智能问答的主入口）。
     *
     * @param userMessage 用户问题
     * @return AI 回答与用量
     */
    public ChatResult chat(String userMessage) {
        return chat(List.of(ChatMessage.user(userMessage)));
    }

    /**
     * 通用多轮对话（为 Day 13 多轮 / Day 18 教练建议预留）。
     *
     * <p>{@code messages} 按 {@code system → user → assistant → user …} 顺序传入即可。</p>
     */
    public ChatResult chat(List<ChatMessage> messages) {
        // 1. 调用前校验 Key（放在这里而不是启动校验：见 DeepSeekProperties 的类注释）
        if (!StringUtils.hasText(properties.getApiKey())) {
            // 细节只进日志，不告诉用户（避免向调用方暴露部署配置）
            log.error("DeepSeek API Key 未配置，请设置环境变量 DEEPSEEK_API_KEY");
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }

        String url = buildUrl();
        ChatRequest request = ChatRequest.of(
                properties.getModel(), messages, properties.getTemperature(), properties.getTopP());

        if (log.isDebugEnabled()) {
            // 不打印 headers（Authorization 在其中）
            log.debug("DeepSeek 请求 url={} model={} messages={}", url, request.getModel(), messages.size());
        }

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<ChatResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, buildHeaders()), ChatResponse.class);

            ChatResult result = extractResult(response.getBody());
            long cost = System.currentTimeMillis() - start;
            log.info("DeepSeek 调用成功 耗时={}ms model={} promptTokens={} completionTokens={}",
                    cost, result.getModel(), result.getPromptTokens(), result.getCompletionTokens());
            return result;

        } catch (ResourceAccessException e) {
            // 网络层异常：可能是建连失败，也可能是读超时（非流式下 = 整段生成超时）
            long cost = System.currentTimeMillis() - start;
            if (hasCause(e, SocketTimeoutException.class)) {
                log.warn("DeepSeek 读超时 耗时={}ms 读超时配置={}ms", cost, properties.getReadTimeout());
                throw new BusinessException(ErrorCode.AI_TIMEOUT);
            }
            log.warn("DeepSeek 连接失败 耗时={}ms msg={}", cost, e.getMessage());
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);

        } catch (RestClientResponseException e) {
            // 服务端返回了非 2xx：带状态码与响应体
            throw handleHttpError(e);

        } catch (RestClientException e) {
            // 其余（含反序列化失败 HttpMessageNotReadableException）
            long cost = System.currentTimeMillis() - start;
            log.error("DeepSeek 调用异常（可能为响应反序列化失败）耗时={}ms msg={}", cost, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }
    }

    /* ============================== 内部实现 ============================== */

    /** 拼接完整 URL，容忍 base-url 末尾有斜杠 */
    private String buildUrl() {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + CHAT_COMPLETIONS_PATH;
    }

    /**
     * 构建请求头。
     *
     * <p>⚠️ 本方法产出的 headers **绝不可整体打进日志**（含 API Key）。</p>
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }

    /** 按状态码分级处理 HTTP 错误 */
    private BusinessException handleHttpError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = truncate(e.getResponseBodyAsString());

        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            // 429：限流或额度不足
            log.warn("DeepSeek 调用被限流 status=429 body={}", body);
            return new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            // 401/403：**我们自己的配置问题**（Key 失效或错填），必须让人立刻看到 → ERROR
            log.error("DeepSeek 鉴权失败 status={} body={}（请检查 DEEPSEEK_API_KEY 是否正确/是否过期）",
                    status, body);
            return new BusinessException(ErrorCode.AI_CALL_FAILED);
        }
        // 其余 4xx / 5xx：外部可预期故障 → WARN
        log.warn("DeepSeek 调用失败 status={} body={}", status, body);
        return new BusinessException(ErrorCode.AI_CALL_FAILED);
    }

    /** 从响应中提取业务所需字段；结构异常（无 choices / 无正文）按调用失败处理 */
    private ChatResult extractResult(ChatResponse body) {
        if (body == null) {
            log.error("DeepSeek 返回空响应体");
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }
        String content = body.firstContent();
        if (!StringUtils.hasText(content)) {
            log.error("DeepSeek 返回不含有效回答 id={} model={}", body.getId(), body.getModel());
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }

        ChatResult result = new ChatResult();
        result.setContent(content);
        result.setModel(body.getModel());
        if (body.getUsage() != null) {
            result.setPromptTokens(body.getUsage().getPromptTokens());
            result.setCompletionTokens(body.getUsage().getCompletionTokens());
        }
        return result;
    }

    /** 沿异常链查找指定类型的 cause */
    private boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        Throwable t = e;
        while (t != null) {
            if (type.isInstance(t)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /** 截断过长文本，避免日志刷屏 */
    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= LOG_BODY_MAX_LEN ? s : s.substring(0, LOG_BODY_MAX_LEN) + "...(truncated)";
    }
}
