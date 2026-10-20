package com.driving.exam.server.integration.embedding;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SiliconFlow Embedding 客户端（本项目与 Embedding 服务交互的**唯一出口**）。
 *
 * <p>职责边界与 {@code DeepSeekClient} 一致：只负责“把文本发出去、把向量取回来”，不涉及切分/入库/检索。
 * <b>失败一律抛 {@link BusinessException}</b>（复用现有 {@code AI_CALL_FAILED / AI_TIMEOUT / AI_QUOTA_EXCEEDED}），
 * 不返回错误对象、不伪造向量。</p>
 *
 * <p><b>安全红线</b>：任何日志都<b>不记录请求头</b>（{@code Authorization: Bearer <API Key>} 在其中）。</p>
 *
 * <p><b>本阶段不调用真实 API</b>：单测用 {@code MockRestServiceServer} 桩断网络。</p>
 */
@Slf4j
@Component
public class EmbeddingClient {

    /** Embedding 端点（相对 base-url）。 */
    private static final String EMBEDDINGS_PATH = "/embeddings";

    /** 单次请求输入上限（SiliconFlow 对 input 数组的限制），超限直接拒绝，由上层编排分批。 */
    public static final int MAX_BATCH_SIZE = 32;

    /** 日志里响应体/异常体的最大打印长度，避免刷屏。 */
    private static final int LOG_BODY_MAX_LEN = 500;

    private final EmbeddingProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 生产构造器：按配置的超时构建专属 {@code RestTemplate}
     * （不注册全局 Bean，避免其它模块误用带长超时的实例——沿用 {@code DeepSeekClient} 的做法）。
     */
    @Autowired
    public EmbeddingClient(EmbeddingProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .build();
    }

    /**
     * 仅用于测试的构造器：直接注入 {@code RestTemplate}（便于 {@code MockRestServiceServer} 绑定）。
     */
    EmbeddingClient(EmbeddingProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /**
     * 将一批文本向量化。
     *
     * @param texts 待向量化文本（{@code null}/空 → 返回空列表，不发起请求；条数 > {@link #MAX_BATCH_SIZE} → 抛
     *              {@link IllegalArgumentException}）
     * @return 与输入<b>顺序一致</b>的向量列表（按响应 {@code index} 排序还原）
     * @throws BusinessException API Key 缺失、HTTP 错误、超时、空/异常响应
     */
    public List<double[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次 Embedding 输入不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            // 细节只进日志，不向调用方暴露部署配置
            log.error("SiliconFlow API Key 未配置，请设置环境变量 SILICONFLOW_API_KEY");
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }

        String url = buildUrl();
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(properties.getModel());
        request.setInput(texts);
        request.setEncodingFormat(properties.getEncodingFormat());

        if (log.isDebugEnabled()) {
            // 不打印 headers（Authorization 在其中）
            log.debug("Embedding 请求 url={} model={} inputSize={}", url, request.getModel(), texts.size());
        }

        long start = System.currentTimeMillis();
        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, buildHeaders()), EmbeddingResponse.class);
            List<double[]> vectors = extractVectors(response.getBody(), texts.size());
            log.info("Embedding 调用成功 耗时={}ms model={} count={}",
                    System.currentTimeMillis() - start, properties.getModel(), vectors.size());
            return vectors;

        } catch (ResourceAccessException e) {
            // 网络层异常：建连失败或读超时
            if (hasCause(e, SocketTimeoutException.class)) {
                log.warn("Embedding 读超时 耗时={}ms 读超时配置={}ms",
                        System.currentTimeMillis() - start, properties.getReadTimeout());
                throw new BusinessException(ErrorCode.AI_TIMEOUT);
            }
            log.warn("Embedding 连接失败 耗时={}ms msg={}", System.currentTimeMillis() - start, e.getMessage());
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);

        } catch (RestClientResponseException e) {
            throw handleHttpError(e);

        } catch (RestClientException e) {
            log.error("Embedding 调用异常（可能为响应反序列化失败）耗时={}ms msg={}",
                    System.currentTimeMillis() - start, e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }
    }

    /* ============================== 内部实现 ============================== */

    private String buildUrl() {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + EMBEDDINGS_PATH;
    }

    /** 构建请求头。⚠️ 产出的 headers 绝不可整体打进日志（含 API Key）。 */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }

    /** 按状态码分级处理 HTTP 错误（与 {@code DeepSeekClient} 口径一致）。 */
    private BusinessException handleHttpError(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String body = truncate(e.getResponseBodyAsString());

        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            log.warn("Embedding 调用被限流 status=429 body={}", body);
            return new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            log.error("Embedding 鉴权失败 status={} body={}（请检查 SILICONFLOW_API_KEY 是否正确/是否过期）",
                    status, body);
            return new BusinessException(ErrorCode.AI_CALL_FAILED);
        }
        log.warn("Embedding 调用失败 status={} body={}", status, body);
        return new BusinessException(ErrorCode.AI_CALL_FAILED);
    }

    /**
     * 从响应提取向量：校验非空、条数与输入一致、每条向量非空；按 {@code index} 升序还原顺序。
     * 任何结构异常都抛 {@link BusinessException}（{@code AI_CALL_FAILED}），<b>绝不伪造向量</b>。
     */
    private List<double[]> extractVectors(EmbeddingResponse body, int expectedCount) {
        if (body == null || body.getData() == null || body.getData().isEmpty()) {
            log.error("Embedding 返回空响应体或空 data（expected={}）", expectedCount);
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }
        List<EmbeddingResponse.EmbeddingData> data = new ArrayList<>(body.getData());
        data.sort(Comparator.comparingInt(d -> d.getIndex() == null ? Integer.MAX_VALUE : d.getIndex()));

        if (data.size() != expectedCount) {
            log.error("Embedding 返回条数与输入不一致 expected={} actual={}", expectedCount, data.size());
            throw new BusinessException(ErrorCode.AI_CALL_FAILED);
        }

        List<double[]> vectors = new ArrayList<>(data.size());
        for (EmbeddingResponse.EmbeddingData d : data) {
            List<Double> embedding = d.getEmbedding();
            if (embedding == null || embedding.isEmpty()) {
                log.error("Embedding 某条结果向量为空 index={}", d.getIndex());
                throw new BusinessException(ErrorCode.AI_CALL_FAILED);
            }
            double[] vector = new double[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.get(i);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        Throwable t = e;
        while (t != null) {
            if (type.isInstance(t)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= LOG_BODY_MAX_LEN ? s : s.substring(0, LOG_BODY_MAX_LEN) + "...(truncated)";
    }
}
