package com.driving.exam.server.integration.embedding;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link EmbeddingClient} 单元测试——使用 {@link MockRestServiceServer} 桩断网络，<b>不访问真实 API</b>。
 *
 * <p>本测试只证明<b>客户端逻辑</b>（请求构造、鉴权头、响应解析、错误处理）正确；
 * <b>不等于</b>真实 SiliconFlow Embedding 服务验证通过。</p>
 */
@DisplayName("EmbeddingClient（Mock，不联网）")
class EmbeddingClientTest {

    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String ENDPOINT = BASE_URL + "/embeddings";
    /** 假 Key，仅用于断言 Bearer 头；非真实凭据。 */
    private static final String FAKE_KEY = "test-key-not-real";

    private EmbeddingProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private EmbeddingClient client;

    @BeforeEach
    void setUp() {
        properties = new EmbeddingProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setModel("BAAI/bge-m3");
        properties.setApiKey(FAKE_KEY);
        properties.setEncodingFormat("float");
        properties.setConnectTimeout(2000);
        properties.setReadTimeout(5000);

        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new EmbeddingClient(properties, restTemplate);
    }

    @Test
    @DisplayName("请求：POST 到 /embeddings、Bearer 头、JSON 字段 model/input/encoding_format 正确")
    void requestFieldsAndHeaders() throws Exception {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + FAKE_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("BAAI/bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("文本一"))
                .andExpect(jsonPath("$.input[1]").value("文本二"))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                .andRespond(withSuccess(successJson(new int[]{0, 1}), MediaType.APPLICATION_JSON));

        List<double[]> vectors = client.embed(List.of("文本一", "文本二"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.10, 0.11);
        assertThat(vectors.get(1)).containsExactly(0.20, 0.21);
        server.verify();
    }

    @Test
    @DisplayName("响应：data 乱序时按 index 升序还原，向量与下标对应正确")
    void responseSortedByIndex() throws Exception {
        // 故意让 index=1 在前、index=0 在后
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(successJson(new int[]{1, 0}), MediaType.APPLICATION_JSON));

        List<double[]> vectors = client.embed(List.of("甲", "乙"));

        assertThat(vectors).hasSize(2);
        // 第一段向量应来自 index=0，第二段来自 index=1
        assertThat(vectors.get(0)).containsExactly(0.10, 0.11);
        assertThat(vectors.get(1)).containsExactly(0.20, 0.21);
        server.verify();
    }

    @Test
    @DisplayName("HTTP 500 → 抛 BusinessException（AI_CALL_FAILED）")
    void httpErrorPropagates() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.AI_CALL_FAILED.getCode()));
        server.verify();
    }

    @Test
    @DisplayName("空 data → 抛 BusinessException（AI_CALL_FAILED），不返回假向量")
    void emptyDataThrows() {
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"model\":\"BAAI/bge-m3\",\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.AI_CALL_FAILED.getCode()));
        server.verify();
    }

    @Test
    @DisplayName("返回条数与输入不一致 → 抛 BusinessException")
    void sizeMismatchThrows() throws Exception {
        // 输入 2 条，但响应只有 1 条
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(oneVectorJson(0), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of("甲", "乙")))
                .isInstanceOf(BusinessException.class);
        server.verify();
    }

    @Test
    @DisplayName("API Key 缺失 → 直接抛 BusinessException，且不发起任何 HTTP 请求")
    void blankApiKey_noHttpRequest() {
        properties.setApiKey(null);
        // 未设置任何期望；若客户端发起请求，MockRestServiceServer 会因“未预期的请求”而失败
        assertThatThrownBy(() -> client.embed(List.of("x")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.AI_CALL_FAILED.getCode()));
        server.verify();
    }

    @Test
    @DisplayName("空输入 → 返回空列表，不发起请求")
    void emptyInputs_returnsEmpty() {
        assertThat(client.embed(List.of())).isEmpty();
        assertThat(client.embed(null)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("输入超过单次上限（>32）→ 抛 IllegalArgumentException，不发起请求")
    void tooManyInputs_rejected() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i <= EmbeddingClient.MAX_BATCH_SIZE; i++) {
            tooMany.add("t" + i);
        }
        assertThatThrownBy(() -> client.embed(tooMany))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    /* ============================== JSON 构造辅助 ============================== */

    /**
     * 生成成功响应 JSON。{@code order} 指定 data 中 index 的出现顺序；
     * index=0 的向量为 [0.10, 0.11]，index=1 的向量为 [0.20, 0.21]（与测试断言对应）。
     */
    private static String successJson(int[] order) throws Exception {
        ObjectMapper om = new ObjectMapper();
        ObjectNode root = om.createObjectNode();
        root.put("model", "BAAI/bge-m3");
        ArrayNode data = root.putArray("data");
        for (int idx : order) {
            ObjectNode d = data.addObject();
            d.put("index", idx);
            ArrayNode emb = d.putArray("embedding");
            if (idx == 0) {
                emb.add(0.10);
                emb.add(0.11);
            } else {
                emb.add(0.20);
                emb.add(0.21);
            }
        }
        ObjectNode usage = root.putObject("usage");
        usage.put("prompt_tokens", 4);
        usage.put("total_tokens", 4);
        return om.writeValueAsString(root);
    }

    private static String oneVectorJson(int idx) throws Exception {
        ObjectMapper om = new ObjectMapper();
        ObjectNode root = om.createObjectNode();
        root.put("model", "BAAI/bge-m3");
        ArrayNode data = root.putArray("data");
        ObjectNode d = data.addObject();
        d.put("index", idx);
        ArrayNode emb = d.putArray("embedding");
        emb.add(0.10);
        return om.writeValueAsString(root);
    }
}
