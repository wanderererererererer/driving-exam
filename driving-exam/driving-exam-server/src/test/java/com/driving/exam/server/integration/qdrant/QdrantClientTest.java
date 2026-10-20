package com.driving.exam.server.integration.qdrant;

import com.driving.exam.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link QdrantClient} 离线单元测试——{@link MockRestServiceServer} 桩断网络，
 * <b>不访问真实 Qdrant / 数据库 / Embedding API / Docker</b>。
 *
 * <p>本测试只证明客户端<b>逻辑</b>（请求构造、校验、认证头、错误/超时、维度缺失保护）正确；
 * <b>不等于</b>真实 Qdrant 兼容性验证。</p>
 */
@DisplayName("QdrantClient（Mock，不联网）")
class QdrantClientTest {

    private static final String BASE = "http://localhost:6333";
    private static final String COLLECTION = "kb";
    private static final String COLLECTIONS_URL = BASE + "/collections/" + COLLECTION;
    private static final String POINTS_URL = COLLECTIONS_URL + "/points";
    private static final int DIM = 3;

    private QdrantProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private QdrantClient client;

    @BeforeEach
    void setUp() {
        properties = new QdrantProperties();
        properties.setBaseUrl(BASE);
        properties.setCollection(COLLECTION);
        properties.setVectorSize(DIM);
        properties.setDistance("Cosine");
        properties.setConnectTimeout(2000);
        properties.setReadTimeout(5000);
        // apiKey 默认 null（本地无鉴权）

        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new QdrantClient(properties, restTemplate);
    }

    /* ==================== 1. upsert 请求结构 ==================== */

    @Test
    @DisplayName("upsert：PUT 到 /collections/{c}/points，JSON 结构为 {points:[{id,vector,payload}]}")
    void upsertRequestStructure() throws Exception {
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.points[0].id").value("p1"))
                .andExpect(jsonPath("$.points[0].vector[0]").value(0.5))
                .andExpect(jsonPath("$.points[0].vector[2]").value(1.5))
                .andExpect(jsonPath("$.points[0].payload.chunkId").value("doc#0"))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

        client.upsertPoints(List.of(point("p1", "doc#0", 7L, Map.of("level", 2))));

        server.verify();
    }

    @Test
    @DisplayName("upsert：多点的 id/vector/payload 一一对应不串位")
    void idVectorPayloadCorrespondence() throws Exception {
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.points[0].id").value("a"))
                .andExpect(jsonPath("$.points[0].payload.chunkId").value("ca"))
                .andExpect(jsonPath("$.points[1].id").value("b"))
                .andExpect(jsonPath("$.points[1].payload.chunkId").value("cb"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.upsertPoints(List.of(
                point("a", "ca", 1L, Map.of()),
                point("b", "cb", 2L, Map.of())));

        server.verify();
    }

    @Test
    @DisplayName("payload 序列化：String / Long / Map / 空 Map 均正确")
    void payloadSerialization() throws Exception {
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.points[0].payload.chunkId").value("doc#0"))
                .andExpect(jsonPath("$.points[0].payload.sourceId").value(7))
                .andExpect(jsonPath("$.points[0].payload.metadata.level").value(2))
                .andExpect(jsonPath("$.points[1].payload.metadata").isEmpty())
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.upsertPoints(List.of(
                point("p0", "doc#0", 7L, Map.of("level", 2)),
                point("p1", "doc#1", 8L, Map.of())));

        server.verify();
    }

    /* ==================== 2. 认证头（可选） ==================== */

    @Test
    @DisplayName("无 api-key：不发送 api-key 头")
    void noApiKey_noAuthHeader() throws Exception {
        properties.setApiKey(null);
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(headerDoesNotExist("api-key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.upsertPoints(List.of(point("p", "c", 1L, Map.of())));
        server.verify();
    }

    @Test
    @DisplayName("配置 api-key：发送 Qdrant 约定的 api-key 头（非 Bearer）")
    void withApiKey_sendsApiKeyHeader() throws Exception {
        properties.setApiKey("secret-qdrant-key");
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("api-key", "secret-qdrant-key"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.upsertPoints(List.of(point("p", "c", 1L, Map.of())));
        server.verify();
    }

    /* ==================== 3. 发送前校验（均不得发请求） ==================== */

    @Test
    @DisplayName("空批次 → IllegalArgumentException，不发请求")
    void emptyBatchRejected() {
        assertThatThrownBy(() -> client.upsertPoints(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.upsertPoints(null)).isInstanceOf(IllegalArgumentException.class);
        server.verify(); // 无请求
    }

    @Test
    @DisplayName("重复 point id → IllegalArgumentException，不发请求")
    void duplicateIdsRejected() {
        assertThatThrownBy(() -> client.upsertPoints(List.of(
                point("dup", "c1", 1L, Map.of()),
                point("dup", "c2", 2L, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    @DisplayName("空向量 / 维度不符 / NaN / Infinity → IllegalArgumentException，不发请求")
    void invalidVectorRejected() {
        assertThatThrownBy(() -> client.upsertPoints(List.of(
                new VectorPoint("e", new float[]{}, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.upsertPoints(List.of(
                new VectorPoint("w", new float[]{1f, 2f}, Map.of())))) // 维度 2 != 3
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.upsertPoints(List.of(
                new VectorPoint("nan", new float[]{Float.NaN, 1f, 2f}, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.upsertPoints(List.of(
                new VectorPoint("inf", new float[]{Float.POSITIVE_INFINITY, 1f, 2f}, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class);

        server.verify();
    }

    @Test
    @DisplayName("payload 为 null / id 为空 → IllegalArgumentException，不发请求")
    void nullPayloadOrIdRejected() {
        assertThatThrownBy(() -> client.upsertPoints(List.of(
                new VectorPoint("p", new float[]{1f, 2f, 3f}, null))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.upsertPoints(List.of(
                new VectorPoint("  ", new float[]{1f, 2f, 3f}, Map.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    /* ==================== 4. 维度未配置保护 ==================== */

    @Test
    @DisplayName("vector-size 未配置 → upsert/ensureCollection 均抛 IllegalStateException，不发请求")
    void vectorSizeMissingBlocksOperations() {
        properties.setVectorSize(null);
        assertThatThrownBy(() -> client.upsertPoints(List.of(point("p", "c", 1L, Map.of()))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> client.ensureCollection())
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    /* ==================== 5. HTTP 成功 / 错误 / 超时 ==================== */

    @Test
    @DisplayName("HTTP 500 → BusinessException（SYSTEM_ERROR）")
    void httpErrorTranslated() {
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.upsertPoints(List.of(point("p", "c", 1L, Map.of()))))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("读超时（SocketTimeoutException）→ BusinessException（SYSTEM_ERROR）")
    void timeoutTranslated() {
        server.expect(requestTo(POINTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("simulated timeout");
                });

        assertThatThrownBy(() -> client.upsertPoints(List.of(point("p", "c", 1L, Map.of()))))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    /* ==================== 6. ensureCollection 流程 ==================== */

    @Test
    @DisplayName("collection 不存在（GET 404）→ PUT 创建，body 含 size/distance")
    void ensureCollection_createsWhenMissing() throws Exception {
        server.expect(requestTo(COLLECTIONS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(COLLECTIONS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.vectors.size").value(DIM))
                .andExpect(jsonPath("$.vectors.distance").value("Cosine"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.ensureCollection();
        server.verify();
    }

    @Test
    @DisplayName("collection 已存在（GET 200）→ 不创建")
    void ensureCollection_noopWhenExists() {
        server.expect(requestTo(COLLECTIONS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.ensureCollection();
        server.verify();
    }

    /* ==================== 辅助 ==================== */

    private static VectorPoint point(String id, String chunkId, long sourceId, Map<String, Object> metadata) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", chunkId);
        payload.put("sourceId", sourceId);
        payload.put("metadata", metadata);
        return new VectorPoint(id, new float[]{0.5f, 1.0f, 1.5f}, payload);
    }
}
