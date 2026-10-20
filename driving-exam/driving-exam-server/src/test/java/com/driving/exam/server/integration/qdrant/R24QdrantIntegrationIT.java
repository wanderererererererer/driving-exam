package com.driving.exam.server.integration.qdrant;

import com.driving.exam.server.integration.embedding.EmbeddingClient;
import com.driving.exam.server.integration.embedding.EmbeddingProperties;
import com.driving.exam.server.rag.ingest.VectorConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2.4 真实集成测试入口（**临时、手动运行、不启动 Spring 上下文、不访问数据库**）。
 *
 * <p><b>类名以 {@code IT} 结尾</b>：项目未自定义 surefire，默认只跑 {@code *Test}/{@code Test*}/{@code *Tests}/{@code *TestCase}，
 * 因此本类<b>不会被普通 {@code mvn test} 执行</b>；只能显式指定运行：
 * {@code mvn -pl driving-exam-server -am test -Dtest=R24QdrantIntegrationIT "-Dsurefire.failIfNoSpecifiedTests=false"}。</p>
 *
 * <h3>安全边界</h3>
 * <ul>
 *   <li><b>不启动 Spring</b>：全部客户端用现有公开构造器 + {@code new RestTemplateBuilder()} 独立实例化 → 不创建 DataSource/MyBatis/不连数据库。</li>
 *   <li><b>集合/地址显式固定</b>：{@code http://localhost:6333} + {@code driving_exam_kb_it}；写入前断言二者严格相等（不依赖默认配置）。</li>
 *   <li><b>写前只读核验</b>：GET 集合信息，确认 {@code status=green}、{@code size=1024}、{@code distance=Cosine}；任一不符立即中止。</li>
 *   <li><b>不调用</b> {@code ensureCollection()}（不建集合）、{@code KnowledgeIngestService.ingest()}（不触业务数据）。</li>
 *   <li><b>真实调用受控</b>：Embedding 仅 1 次、upsert 仅 1 次；失败不重试、不清理、不删除。</li>
 *   <li><b>不输出密钥</b>：Key 仅从环境变量读取用于鉴权，不打印其内容/长度。</li>
 * </ul>
 *
 * <p><b>写后回查</b>：Qdrant「按 ID 取点」的真实 REST 端点是 {@code POST /collections/{c}/points}（body: ids/with_vector/with_payload），
 * 而非 {@code /points/retrieve}；本测试用前者做只读回查。</p>
 */
@DisplayName("R2.4 真实集成（Embedding → Qdrant，手动运行）")
class R24QdrantIntegrationIT {

    private static final String TEXT = "R2.4 Qdrant integration test point";
    private static final String QDRANT_URL = "http://localhost:6333";
    private static final String COLLECTION = "driving_exam_kb_it";
    private static final int DIM = 1024;
    private static final String SOURCE = "R2.4-integration-test";
    /** 可识别标记：放在 payload，不拼进 point ID（Qdrant 只接受 unsigned int 或合法 UUID）。 */
    private static final String MARKER = "r24-it";

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("固定文本 → 真实 Embedding(1024) → 写入 driving_exam_kb_it → 只读回查")
    void realEmbeddingWriteThenReadBack() throws Exception {
        // ===== 0. 环境前置：Key 必须存在（明确失败，不跳过；仅判存在，不打印值/长度）=====
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        assertThat(apiKey != null && !apiKey.isBlank())
                .as("环境变量 SILICONFLOW_API_KEY 未设置，无法进行真实 Embedding 集成验证（请先在本机设置该环境变量）")
                .isTrue();

        // ===== 1. 独立实例化（不启动 Spring）=====
        EmbeddingProperties ep = new EmbeddingProperties();
        ep.setApiKey(apiKey); // 不记录/不输出
        EmbeddingClient embedding = new EmbeddingClient(ep, new RestTemplateBuilder());

        QdrantProperties qp = new QdrantProperties();
        qp.setBaseUrl(QDRANT_URL);
        qp.setCollection(COLLECTION);
        qp.setVectorSize(DIM);
        qp.setDistance("Cosine");
        QdrantClient qdrant = new QdrantClient(qp, new RestTemplateBuilder());

        VectorConverter converter = new VectorConverter();

        RestTemplate http = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();

        // ===== 2. 集合隔离断言（写入/Embedding 之前，不依赖默认配置）=====
        assertThat(qp.getBaseUrl()).as("Qdrant 地址必须是固定测试地址").isEqualTo("http://localhost:6333");
        assertThat(qp.getCollection()).as("目标集合必须严格为测试集合").isEqualTo("driving_exam_kb_it");
        assertThat(qp.getVectorSize()).as("维度必须为 1024").isEqualTo(1024);

        // ===== 3. 写前只读核验集合（存在/green/1024/Cosine）——不符即中止 =====
        String infoUrl = QDRANT_URL + "/collections/" + COLLECTION;
        ResponseEntity<String> info = http.getForEntity(infoUrl, String.class);
        assertThat(info.getStatusCode().value()).as("集合查询 HTTP 状态").isEqualTo(200);
        JsonNode infoResult = json.readTree(info.getBody()).path("result");
        assertThat(infoResult.path("status").asText()).as("集合状态应为 green").isEqualTo("green");
        JsonNode vectorsCfg = infoResult.path("config").path("params").path("vectors");
        assertThat(vectorsCfg.path("size").asInt()).as("集合向量维度应为 1024").isEqualTo(1024);
        assertThat(vectorsCfg.path("distance").asText())
                .as("距离类型应为 Cosine").isEqualToIgnoringCase("Cosine");

        // ===== 3b. 首次受控写入前置：集合必须为空（points_count 存在、为整数、== 0）=====
        // 该检查在真实 Embedding 与 upsert 之前；不符即中止，不写、不删、不清理。
        JsonNode pointsCount = infoResult.path("points_count");
        assertThat(pointsCount.isIntegralNumber())
                .as("集合信息应包含整数型 points_count（缺失/非数字/小数则中止）").isTrue();
        assertThat(pointsCount.asLong())
                .as("目标集合 driving_exam_kb_it 必须为空（points_count == 0）；若非空请人工核查后再决定").isZero();

        // ===== 4. 真实 Embedding：仅 1 次 =====
        List<double[]> vectors = embedding.embed(List.of(TEXT));
        assertThat(vectors).as("应返回 1 条向量").hasSize(1);
        assertThat(vectors.get(0)).as("向量维度应为 1024").hasSize(DIM);

        // ===== 5. 转换 + 组点 =====
        float[] fv = converter.toFloatVector(vectors.get(0), DIM);
        // Qdrant point ID 只接受 unsigned int 或合法 UUID；使用标准 UUID（不拼自定义前缀）
        String testId = UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("testOnly", true);
        payload.put("source", SOURCE);
        payload.put("text", TEXT);
        payload.put("marker", MARKER);
        VectorPoint point = new VectorPoint(testId, fv, payload);

        // ===== 6. 写入：仅 1 次（失败不重试、不清理）=====
        qdrant.upsertPoints(List.of(point));

        // ===== 7. 只读回查：POST /collections/{c}/points（按 id 取点）=====
        String retrieveUrl = QDRANT_URL + "/collections/" + COLLECTION + "/points";
        Map<String, Object> retrieveBody = new LinkedHashMap<>();
        retrieveBody.put("ids", List.of(testId));
        retrieveBody.put("with_payload", true);
        retrieveBody.put("with_vector", true);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resp = http.postForEntity(retrieveUrl, new HttpEntity<>(retrieveBody, headers), String.class);
        assertThat(resp.getStatusCode().value()).as("回查 HTTP 状态").isEqualTo(200);

        JsonNode result = json.readTree(resp.getBody()).path("result");
        assertThat(result.isArray()).as("retrieve 返回 result 数组").isTrue();
        assertThat(result.size()).as("应回查到本次写入的 1 个点").isEqualTo(1);

        JsonNode p = result.get(0);
        assertThat(p.path("id").asText()).as("回查点 ID 必须与写入一致").isEqualTo(testId);
        assertThat(p.path("payload").path("testOnly").asBoolean()).as("payload.testOnly").isTrue();
        assertThat(p.path("payload").path("source").asText()).as("payload.source").isEqualTo(SOURCE);
        assertThat(p.path("payload").path("text").asText()).as("payload.text").isEqualTo(TEXT);
        assertThat(p.path("payload").path("marker").asText()).as("payload.marker").isEqualTo(MARKER);

        JsonNode vec = p.path("vector");
        assertThat(vec.isArray()).as("回查应包含向量").isTrue();
        assertThat(vec.size()).as("回查向量维度应为 1024").isEqualTo(DIM);
    }
}
