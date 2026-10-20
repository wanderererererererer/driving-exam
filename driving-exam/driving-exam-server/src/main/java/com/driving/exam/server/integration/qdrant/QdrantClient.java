package com.driving.exam.server.integration.qdrant;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Qdrant REST 客户端（向量库写入基础设施）。
 *
 * <p><b>通信方式</b>：Qdrant REST + 项目现有 Spring {@code RestTemplate}（与 DeepSeek/Embedding 客户端同范式），
 * <b>不引入</b>官方 Java 客户端依赖。</p>
 *
 * <p><b>API Key 可选</b>：本地无鉴权时留空、不发送认证头；配置了才发送 Qdrant 约定的
 * {@code api-key: <key>} 头（<b>不是</b> Bearer）。<b>任何日志都不记录请求头/Key，也不打印整段用户文本。</b></p>
 *
 * <p><b>维度硬约束</b>：{@code qdrant.vector-size} 未配置（{@code null}）时，{@link #ensureCollection()} 与
 * {@link #upsertPoints(List)} 都会被<b>拒绝</b>（{@link IllegalStateException}），绝不默认成某个猜测维度。
 * <b>应用启动不会隐式建 collection</b>——只有显式调用 {@code ensureCollection()} 才会。</p>
 *
 * <p><b>检索（Query API，只读）</b>：{@link #search(float[], int, String)} 使用
 * {@code POST /collections/{name}/points/query}（Qdrant 1.10+ 官方检索接口；旧的 {@code /points/search}
 * 已弃用且不在 1.19 规范中，本项目不使用）。远端/响应解析失败统一 {@link BusinessException}
 * （{@link ErrorCode#SYSTEM_ERROR}），真实空结果返回空列表，二者不混淆。</p>
 *
 * <p><b>错误语义</b>：本类不新增公共错误码，也不复用 AI 专属错误码；远端/网络失败统一抛
 * {@link BusinessException}（{@link ErrorCode#SYSTEM_ERROR}，“系统异常”）；发送前的参数校验失败抛
 * {@link IllegalArgumentException}/{@link IllegalStateException}。</p>
 */
@Slf4j
@Component
public class QdrantClient {

    /** 日志里响应体的最大打印长度。 */
    private static final int LOG_BODY_MAX_LEN = 500;

    /** 检索请求 limit 的<b>客户端防御性上限</b>（防止误传超大值；业务层 topK 上限另行约束）。 */
    private static final int MAX_SEARCH_LIMIT = 100;

    private final QdrantProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 生产构造器：按配置超时构建专属 RestTemplate。 */
    @Autowired
    public QdrantClient(QdrantProperties properties, RestTemplateBuilder builder) {
        this.properties = properties;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .build();
    }

    /** 仅用于测试：直接注入 RestTemplate（便于 MockRestServiceServer 绑定）。 */
    QdrantClient(QdrantProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /* ============================== collection ============================== */

    /**
     * 确保 collection 存在：{@code GET /collections/{name}}；404 时 {@code PUT} 创建（用配置的维度/距离）。
     *
     * <p><b>不</b>在应用启动时自动调用；且 {@code vector-size} 未配置时直接拒绝，绝不猜测维度。</p>
     *
     * @throws IllegalStateException {@code vector-size} 未配置
     * @throws BusinessException     远端/网络失败
     */
    public void ensureCollection() {
        int size = requireVectorSize();
        String url = collectionsUrl();

        try {
            restTemplate.getForEntity(url, String.class);
            log.info("Qdrant collection 已存在 name={}", properties.getCollection());
            return; // 已存在
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                createCollection(size);
                return;
            }
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw translate(e);
        } catch (RestClientException e) {
            log.error("Qdrant 查询 collection 异常 msg={}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /* ============================== upsert ============================== */

    /**
     * 批量写入/覆盖向量点（{@code PUT /collections/{name}/points}，幂等 upsert 语义）。
     *
     * <p>发送前完成全部校验（维度、数量、有限数值、重复 ID 等）；任一失败都在**发送前**抛出，
     * 不发送请求、不静默修正。</p>
     *
     * @throws IllegalArgumentException 参数非法（列表空/null、点/ID/vector/payload 非法、维度不符、NaN/Infinity、重复 ID）
     * @throws IllegalStateException    {@code vector-size} 未配置（无法校验维度）
     * @throws BusinessException        远端/网络失败
     */
    public void upsertPoints(List<VectorPoint> points) {
        int size = requireVectorSize();
        validatePoints(points, size);

        List<Map<String, Object>> encoded = new ArrayList<>(points.size());
        for (VectorPoint p : points) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.id());
            item.put("vector", p.vector());
            item.put("payload", p.payload());
            encoded.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("points", encoded);

        String url = collectionsUrl() + "/points";
        if (log.isDebugEnabled()) {
            log.debug("Qdrant upsert url={} collection={} points={}",
                    url, properties.getCollection(), points.size());
        }
        long start = System.currentTimeMillis();
        try {
            restTemplate.put(url, new HttpEntity<>(body, buildHeaders()));
            log.info("Qdrant upsert 成功 collection={} points={} 耗时={}ms",
                    properties.getCollection(), points.size(), System.currentTimeMillis() - start);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw translate(e);
        } catch (RestClientException e) {
            log.error("Qdrant upsert 异常 耗时={}ms msg={}", System.currentTimeMillis() - start, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /* ============================== query（只读检索） ============================== */

    /**
     * Query API 相似度检索（{@code POST /collections/{name}/points/query}，<b>只读</b>，不修改任何数据）。
     *
     * <p>请求体：{@code {query: 向量, limit, with_payload: true, with_vector: false, filter?}}；
     * {@code subjectCode} 为 null/空白时<b>不</b>附加过滤，有实际值时才用
     * {@code filter.must[0] = {key:"subjectCode", match:{value:<subjectCode>}}} 做精确匹配。
     * 集合名来自配置（{@link QdrantProperties#getCollection()}），本类不硬编码任何业务集合名。</p>
     *
     * <p>响应解析只认 {@code result.points} 数组（不是旧 search 接口的裸数组）；每个元素解析
     * {@code id / version / score / payload}。真实空结果（{@code points: []}）返回空列表；
     * HTTP 错误、响应缺失/格式非法一律抛 {@link BusinessException}，<b>不</b>伪装为空结果。</p>
     *
     * @param queryVector 查询向量（维度必须等于已配置的 {@code qdrant.vector-size}）
     * @param limit       返回条数（≥1，且不超过客户端防御性上限 {@value #MAX_SEARCH_LIMIT}）
     * @param subjectCode 可选科目过滤（null/空白 = 不过滤）
     * @return 按 Qdrant 返回顺序（Cosine 下分数非递增）的结果列表；不重排
     * @throws IllegalArgumentException 向量为空/维度不符/含非有限值，或 limit 非法
     * @throws IllegalStateException    {@code vector-size} 未配置（无法校验维度）
     * @throws BusinessException        远端/网络失败，或响应体缺失/无法解析/缺关键字段
     */
    public List<ScoredPoint> search(float[] queryVector, int limit, String subjectCode) {
        int size = requireVectorSize();
        validateSearchInput(queryVector, limit, size);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", queryVector);
        body.put("limit", limit);
        body.put("with_payload", true);
        body.put("with_vector", false);
        if (StringUtils.hasText(subjectCode)) {
            body.put("filter", subjectFilter(subjectCode));
        }

        String url = collectionsUrl() + "/points/query";
        if (log.isDebugEnabled()) {
            log.debug("Qdrant query url={} collection={} limit={} filtered={}",
                    url, properties.getCollection(), limit, StringUtils.hasText(subjectCode));
        }
        long start = System.currentTimeMillis();
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(url, new HttpEntity<>(body, buildHeaders()), String.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw translate(e);
        } catch (RestClientException e) {
            log.error("Qdrant query 异常 耗时={}ms msg={}", System.currentTimeMillis() - start, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        List<ScoredPoint> points = parseSearchResponse(response.getBody());
        log.info("Qdrant query 成功 collection={} points={} 耗时={}ms",
                properties.getCollection(), points.size(), System.currentTimeMillis() - start);
        return points;
    }

    /* ============================== 内部实现 ============================== */

    private void createCollection(int size) {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> vectors = new LinkedHashMap<>();
        vectors.put("size", size);
        vectors.put("distance", properties.getDistance());
        body.put("vectors", vectors);

        String url = collectionsUrl();
        try {
            restTemplate.put(url, new HttpEntity<>(body, buildHeaders()));
            log.info("Qdrant collection 创建成功 name={} size={} distance={}",
                    properties.getCollection(), size, properties.getDistance());
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw translate(e);
        } catch (RestClientException e) {
            log.error("Qdrant 创建 collection 异常 msg={}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 维度必须已配置（否则无法校验/建集合）。绝不猜测默认维度。 */
    private int requireVectorSize() {
        Integer size = properties.getVectorSize();
        if (size == null || size < 1) {
            throw new IllegalStateException(
                    "qdrant.vector-size 未配置（维度未确认），禁止建集合或写入向量");
        }
        return size;
    }

    /** 发送前的参数校验（全部在 HTTP 之前完成）。 */
    private void validatePoints(List<VectorPoint> points, int expectedSize) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("points 不能为空");
        }
        Set<String> seenIds = new HashSet<>();
        for (VectorPoint p : points) {
            if (p == null) {
                throw new IllegalArgumentException("points 中存在 null 元素");
            }
            if (!StringUtils.hasText(p.id())) {
                throw new IllegalArgumentException("point id 不能为空");
            }
            if (!seenIds.add(p.id())) {
                throw new IllegalArgumentException("同一批次内 point id 重复：" + p.id());
            }
            float[] vector = p.vector();
            if (vector == null || vector.length == 0) {
                throw new IllegalArgumentException("point 向量不能为空 id=" + p.id());
            }
            if (vector.length != expectedSize) {
                throw new IllegalArgumentException(
                        "向量维度不符 id=" + p.id() + " expected=" + expectedSize + " actual=" + vector.length);
            }
            for (float v : vector) {
                if (!Float.isFinite(v)) {
                    throw new IllegalArgumentException("向量含非有限数值（NaN/Infinity）id=" + p.id());
                }
            }
            if (p.payload() == null) {
                throw new IllegalArgumentException("point payload 不能为空 id=" + p.id());
            }
        }
    }

    /** 检索请求的发送前校验（全部在 HTTP 之前完成，沿用 upsert 的校验风格）。 */
    private static void validateSearchInput(float[] queryVector, int limit, int expectedSize) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("query 向量不能为空");
        }
        if (queryVector.length != expectedSize) {
            throw new IllegalArgumentException(
                    "query 向量维度不符 expected=" + expectedSize + " actual=" + queryVector.length);
        }
        for (float v : queryVector) {
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("query 向量含非有限数值（NaN/Infinity）");
            }
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须 ≥ 1，实际=" + limit);
        }
        if (limit > MAX_SEARCH_LIMIT) {
            throw new IllegalArgumentException("limit 超过防御性上限 " + MAX_SEARCH_LIMIT + "，实际=" + limit);
        }
    }

    /** subjectCode 精确匹配过滤（Qdrant filter.must[0] = {key, match:{value}}）。 */
    private static Map<String, Object> subjectFilter(String subjectCode) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("value", subjectCode);

        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("key", "subjectCode");
        condition.put("match", match);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("must", List.of(condition));
        return filter;
    }

    /** 解析 Query API 响应：必须是 {@code result.points} 数组；任何结构非法即失败，绝不返回空列表冒充。 */
    private List<ScoredPoint> parseSearchResponse(String body) {
        if (!StringUtils.hasText(body)) {
            log.warn("Qdrant query 响应体为空，按异常处理");
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            log.warn("Qdrant query 响应无法解析 msg={} body={}", e.getMessage(), truncate(body));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        JsonNode points = root.path("result").path("points");
        if (!points.isArray()) {
            log.warn("Qdrant query 响应缺少 result.points 数组 body={}", truncate(body));
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        List<ScoredPoint> out = new ArrayList<>(points.size());
        for (JsonNode item : points) {
            JsonNode idNode = item.path("id");
            if (!idNode.isTextual() || idNode.asText().isBlank()) {
                log.warn("Qdrant query 结果项缺少合法 id body={}", truncate(body));
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            String id = idNode.asText();

            JsonNode scoreNode = item.path("score");
            if (!scoreNode.isNumber() || !Double.isFinite(scoreNode.asDouble())) {
                log.warn("Qdrant query 结果项 score 缺失或非法 id={}", id);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }

            // Qdrant 1.19.2 OpenAPI：ScoredPoint.version 为必需字段（uint64）；缺失/null/非整数一律视为非法响应
            JsonNode versionNode = item.path("version");
            if (!versionNode.isIntegralNumber()) {
                log.warn("Qdrant query 结果项 version 缺失或类型非法 id={}", id);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
            long version = versionNode.asLong();

            Map<String, Object> payload = null;
            JsonNode payloadNode = item.path("payload");
            if (payloadNode.isObject()) {
                payload = objectMapper.convertValue(payloadNode, new TypeReference<Map<String, Object>>() {
                });
            } else if (!payloadNode.isMissingNode() && !payloadNode.isNull()) {
                log.warn("Qdrant query 结果项 payload 类型非法 id={}", id);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }

            out.add(new ScoredPoint(id, version, scoreNode.asDouble(), payload));
        }
        return out;
    }

    private String collectionsUrl() {
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/collections/" + properties.getCollection();
    }

    /** 构建请求头：JSON；仅在配置了 api-key 时才加 Qdrant 约定的 {@code api-key} 头（非 Bearer）。 */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(properties.getApiKey())) {
            headers.set("api-key", properties.getApiKey());
        }
        return headers;
    }

    private BusinessException translate(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            if (hasCause(e, SocketTimeoutException.class)) {
                log.warn("Qdrant 读超时 读超时配置={}ms", properties.getReadTimeout());
            } else {
                log.warn("Qdrant 连接失败 msg={}", e.getMessage());
            }
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        if (e instanceof RestClientResponseException) {
            RestClientResponseException re = (RestClientResponseException) e;
            log.warn("Qdrant 调用失败 status={} body={}",
                    re.getStatusCode().value(), truncate(re.getResponseBodyAsString()));
            return new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        return new BusinessException(ErrorCode.SYSTEM_ERROR);
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
