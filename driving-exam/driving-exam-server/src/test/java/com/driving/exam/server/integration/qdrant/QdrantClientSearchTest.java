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

import java.util.List;

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
 * {@link QdrantClient#search(float[], int, String)} 的离线单元测试——{@link MockRestServiceServer}
 * 桩断网络，<b>不访问真实 Qdrant / Embedding API / 数据库</b>。
 *
 * <p>本测试只证明客户端<b>逻辑</b>（Query API 请求构造、过滤结构、响应解析、错误处理）正确；
 * <b>不等于</b>真实 Qdrant 集成验证（真实端点行为已由用户本机只读验证，另行记录）。</p>
 */
@DisplayName("QdrantClient Query API（Mock，不联网）")
class QdrantClientSearchTest {

    private static final String BASE = "http://localhost:6333";
    private static final String COLLECTION = "kb";
    private static final String QUERY_URL = BASE + "/collections/" + COLLECTION + "/points/query";
    private static final int DIM = 3;

    private static final String FULL_RESPONSE = """
            {"result":{"points":[
              {"id":"a59fc217-8e4e-3c1c-942a-bf3dadcf8ffb","version":3,"score":0.93,
               "payload":{"chunkId":"QUESTION_ANALYSIS:1#0","docId":"QUESTION_ANALYSIS:1",
                          "sourceType":"QUESTION_ANALYSIS","sourceId":1,
                          "text":"红灯亮时禁止通行。","subjectCode":"SUBJECT_1"}},
              {"id":"550e8400-e29b-41d4-a716-446655440000","version":1,"score":0.77,
               "payload":{"chunkId":"KNOWLEDGE_POINT:2#0","docId":"KNOWLEDGE_POINT:2",
                          "sourceType":"KNOWLEDGE_POINT","sourceId":2,
                          "text":"红绿灯含义。","subjectCode":"SUBJECT_1"}}
            ]},"status":"ok","time":0.002}
            """;

    private static final String EMPTY_RESPONSE = """
            {"result":{"points":[]},"status":"ok","time":0.001}
            """;

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

    /* ==================== 1. 请求结构与参数 ==================== */

    @Test
    @DisplayName("query：POST 到 /collections/{c}/points/query，body 含 query/limit/with_payload/with_vector，无过滤时不带 filter")
    void queryRequestStructure() {
        server.expect(requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query.length()").value(3))
                .andExpect(jsonPath("$.query[0]").value(0.5))
                .andExpect(jsonPath("$.query[2]").value(2.5))
                .andExpect(jsonPath("$.limit").value(5))
                .andExpect(jsonPath("$.with_payload").value(true))
                .andExpect(jsonPath("$.with_vector").value(false))
                .andExpect(jsonPath("$.filter").doesNotExist())
                .andRespond(withSuccess(FULL_RESPONSE, MediaType.APPLICATION_JSON));

        List<ScoredPoint> result = client.search(vec(), 5, null);

        server.verify();
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("query：subjectCode 有值时 filter.must[0] = {key:subjectCode, match:{value:...}}")
    void subjectFilterApplied() {
        server.expect(requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.filter.must.length()").value(1))
                .andExpect(jsonPath("$.filter.must[0].key").value("subjectCode"))
                .andExpect(jsonPath("$.filter.must[0].match.value").value("SUBJECT_1"))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        client.search(vec(), 5, "SUBJECT_1");

        server.verify();
    }

    @Test
    @DisplayName("query：subjectCode 为 null/空串/纯空白 → 视为未提供，不带 filter")
    void blankSubjectCodeNoFilter() {
        String[] blanks = {null, "", "   "};
        // MockRestServiceServer 要求：所有 expectation 必须在任何真实请求发出之前注册（先注册、后请求）
        for (String blank : blanks) {
            server.expect(requestTo(QUERY_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(jsonPath("$.filter").doesNotExist())
                    .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        }

        for (String blank : blanks) {
            client.search(vec(), 5, blank);
        }
        server.verify();
    }

    @Test
    @DisplayName("query：配置 api-key 时发送 api-key 头（非 Bearer）；未配置时不发送")
    void apiKeyHeader() {
        // 两个 expectation 先全部注册，再按顺序发出两次请求（禁止请求后再 expect）
        server.expect(requestTo(QUERY_URL))
                .andExpect(header("api-key", "dummy"))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(QUERY_URL))
                .andExpect(headerDoesNotExist("api-key"))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        properties.setApiKey("dummy");
        client.search(vec(), 5, null);

        properties.setApiKey(null);
        client.search(vec(), 5, null);

        server.verify();
    }

    /* ==================== 2. 响应解析 ==================== */

    @Test
    @DisplayName("解析：result.points 的 id/version/score/payload 正确，顺序与响应一致")
    void parseFullResponse() {
        server.expect(requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(FULL_RESPONSE, MediaType.APPLICATION_JSON));

        List<ScoredPoint> result = client.search(vec(), 5, null);

        server.verify();
        assertThat(result).extracting(ScoredPoint::id).containsExactly(
                "a59fc217-8e4e-3c1c-942a-bf3dadcf8ffb",
                "550e8400-e29b-41d4-a716-446655440000");
        ScoredPoint first = result.get(0);
        assertThat(first.version()).isEqualTo(3L);
        assertThat(first.score()).isEqualTo(0.93);
        assertThat(first.payload())
                .containsEntry("chunkId", "QUESTION_ANALYSIS:1#0")
                .containsEntry("docId", "QUESTION_ANALYSIS:1")
                .containsEntry("sourceType", "QUESTION_ANALYSIS")
                .containsEntry("sourceId", 1)
                .containsEntry("subjectCode", "SUBJECT_1");
    }

    @Test
    @DisplayName("解析：version 缺失 → 非法响应，BusinessException（1.19.2 中 version 为必需字段）")
    void missingVersionRejected() {
        String body = """
                {"result":{"points":[
                  {"id":"550e8400-e29b-41d4-a716-446655440000","score":0.5}
                ]},"status":"ok"}
                """;
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search(vec(), 5, null))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("解析：version 合法但 payload 缺失 → payload 为 null（Qdrant schema 允许 payload 为空）")
    void missingPayloadAllowed() {
        String body = """
                {"result":{"points":[
                  {"id":"550e8400-e29b-41d4-a716-446655440000","version":7,"score":0.5}
                ]},"status":"ok"}
                """;
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<ScoredPoint> result = client.search(vec(), 5, null);

        server.verify();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).version()).isEqualTo(7L);
        assertThat(result.get(0).payload()).isNull();
    }

    @Test
    @DisplayName("解析：真实空结果（points: []）返回空列表，不抛异常")
    void emptyResultReturnsEmptyList() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        List<ScoredPoint> result = client.search(vec(), 5, null);

        server.verify();
        assertThat(result).isNotNull().isEmpty();
    }

    /* ==================== 3. 错误与非法响应（不得伪装为空结果） ==================== */

    @Test
    @DisplayName("HTTP 500 → BusinessException（SYSTEM_ERROR）")
    void httpErrorTranslated() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.search(vec(), 5, null))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("读超时（SocketTimeoutException）→ BusinessException")
    void timeoutTranslated() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("simulated timeout");
                });

        assertThatThrownBy(() -> client.search(vec(), 5, null))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("响应体无法解析（非 JSON）→ BusinessException")
    void malformedBody() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search(vec(), 5, null))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("响应缺少 result.points 数组 → BusinessException（不返回空列表）")
    void missingPointsArray() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess("{\"result\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.search(vec(), 5, null))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("结果项缺 id / id 为数字 / score 为字符串 / version 非整数、字符串、null、布尔 / payload 非对象 → 均失败")
    void invalidItemFields() {
        String[] badBodies = {
                """
                {"result":{"points":[{"version":1,"score":0.5}]}}
                """,
                """
                {"result":{"points":[{"id":42,"version":1,"score":0.5}]}}
                """,
                """
                {"result":{"points":[{"id":"550e8400-e29b-41d4-a716-446655440000","version":1,"score":"high"}]}}
                """,
                """
                {"result":{"points":[{"id":"550e8400-e29b-41d4-a716-446655440000","version":1.5,"score":0.5}]}}
                """,
                """
                {"result":{"points":[{"id":"550e8400-e29b-41d4-a716-446655440000","version":"1","score":0.5}]}}
                """,
                """
                {"result":{"points":[{"id":"550e8400-e29b-41d4-a716-446655440000","version":null,"score":0.5}]}}
                """,
                """
                {"result":{"points":[{"id":"550e8400-e29b-41d4-a716-446655440000","version":true,"score":0.5}]}}
                """,
                """
                {"result":{"points":[{"id":"550e8400-e29b-41d4-a716-446655440000","version":1,"score":0.5,"payload":[]}]}}
                """
        };
        for (String bad : badBodies) {
            server.reset();
            server.expect(requestTo(QUERY_URL))
                    .andRespond(withSuccess(bad, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> client.search(vec(), 5, null))
                    .as("非法响应体应失败：%s", bad.trim())
                    .isInstanceOf(BusinessException.class);
            server.verify();
        }
    }

    /* ==================== 4. 发送前校验（不发请求） ==================== */

    @Test
    @DisplayName("vector-size 未配置 → IllegalStateException，不发请求")
    void vectorSizeNotConfigured() {
        properties.setVectorSize(null);

        assertThatThrownBy(() -> client.search(vec(), 5, null))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }

    @Test
    @DisplayName("query 向量为 null/空/维度不符/NaN → IllegalArgumentException，不发请求")
    void invalidQueryVector() {
        assertThatThrownBy(() -> client.search(null, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.search(new float[0], 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.search(new float[]{0.1f, 0.2f}, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.search(new float[]{0.1f, Float.NaN, 0.3f}, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    @DisplayName("limit < 1 或超过防御性上限 → IllegalArgumentException，不发请求")
    void invalidLimit() {
        assertThatThrownBy(() -> client.search(vec(), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.search(vec(), -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.search(vec(), 101, null))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    /* ============================== 辅助 ============================== */

    private static float[] vec() {
        return new float[]{0.5f, 1.5f, 2.5f};
    }
}
