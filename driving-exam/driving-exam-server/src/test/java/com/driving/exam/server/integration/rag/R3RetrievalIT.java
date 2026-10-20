package com.driving.exam.server.integration.rag;

import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.rag.ingest.KbDevIngestGuard;
import com.driving.exam.server.rag.retrieve.KnowledgeRetriever;
import com.driving.exam.server.rag.retrieve.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R3 知识检索<b>只读</b>真实集成测试（手动运行、默认跳过、不随普通构建执行）。
 *
 * <h3>双重门禁</h3>
 * <ol>
 *   <li>类名以 {@code IT} 结尾——Surefire 默认规则（{@code *Test}/{@code Test*}/{@code *Tests}/
 *       {@code *TestCase}）不会发现本类；只能显式指定运行：
 *       {@code mvn -pl driving-exam-server -am test -Dtest=R3RetrievalIT "-Dsurefire.failIfNoSpecifiedTests=false"}；</li>
 *   <li>{@link EnabledIfEnvironmentVariable} {@code RAG_DEV_INGEST=true}——未显式设置时在条件阶段跳过，
 *       Spring 上下文不会创建。</li>
 * </ol>
 *
 * <h3>集合隔离与只读保证</h3>
 * <ul>
 *   <li>{@code @SpringBootTest(properties=...)} 固定 {@code qdrant.collection=driving_exam_kb_dev} 与
 *       {@code vector-size=1024}（内联测试属性优先级高于 application.yml）；</li>
 *   <li>{@link KbDevIngestGuard} 在<b>任何网络调用之前</b>校验集合名与向量配置（配置覆盖失效时
 *       会在首个请求前失败）；</li>
 *   <li>随后用只读 GET 在线核验 {@code status=green / size=1024 / distance=Cosine / points_count>0}；</li>
 *   <li>本测试只执行检索（embedding 1 次 + Qdrant query 若干次）：不创建集合、不 upsert、
 *       不删除/清空任何点；检索前后 {@code points_count} 必须相等。</li>
 * </ul>
 *
 * <h3>边界说明</h3>
 * <p>单次检索只证明"当前环境下检索调用与结果解析可运行"，<b>不</b>证明召回率、排序质量或回答质量。
 * 查询文本与语料强相关，但不断言命中任何指定文档；{@code SUBJECT_4} 当前无数据，只验证
 * "若有结果则均符合过滤条件"，不把空结果固化为业务规则。</p>
 *
 * <p><b>运行前置</b>：本机 Qdrant 可连（{@code http://localhost:6333}）、环境变量
 * {@code SILICONFLOW_API_KEY} 与 {@code RAG_DEV_INGEST=true} 已设置、开发集合已入库（DevKbIngestIT）。</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "qdrant.base-url=http://localhost:6333",
                "qdrant.collection=driving_exam_kb_dev",
                "qdrant.vector-size=1024",
                "qdrant.distance=Cosine",
                "embedding.api-key=${SILICONFLOW_API_KEY:}"
        })
@EnabledIfEnvironmentVariable(named = "RAG_DEV_INGEST", matches = "true")
class R3RetrievalIT {

    private static final String COLLECTION = "driving_exam_kb_dev";
    private static final int DIM = 1024;
    private static final String DISTANCE = "Cosine";
    private static final int TOP_K = KnowledgeRetriever.DEFAULT_TOP_K;
    private static final String QUERY = "红灯亮时应当怎样通行";
    private static final int LOG_BODY_MAX_LEN = 500;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private KnowledgeRetriever retriever;

    @Autowired
    private QdrantProperties qdrantProperties;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Test
    @DisplayName("普通/SUBJECT_1/SUBJECT_4 检索 → 只读，检索前后 points_count 不变（手动运行）")
    void retrievalIsReadOnly() throws Exception {
        // ===== 0. 守卫：必须发生在任何 Embedding / Qdrant 网络调用之前 =====
        KbDevIngestGuard.requireDevCollection(qdrantProperties.getCollection());
        KbDevIngestGuard.requireValidatedVectorConfig(qdrantProperties);
        assertThat(qdrantProperties.getCollection()).isEqualTo(COLLECTION);
        System.out.printf("守卫通过：collection=%s vectorSize=%s distance=%s%n",
                qdrantProperties.getCollection(), qdrantProperties.getVectorSize(), qdrantProperties.getDistance());

        RestTemplate http = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();

        // ===== 1. 集合只读核验 + 记录检索前点数 =====
        JsonNode beforeInfo = requireCollectionInfo(http);
        verifyCollection(beforeInfo, "检索前");
        long pointsBefore = requirePointsCount(beforeInfo, "检索前");
        assertThat(pointsBefore)
                .as("开发集合为空（points_count=0）：请先执行 DevKbIngestIT 完成真实入库")
                .isPositive();
        System.out.printf("集合核验通过：status=green size=%d distance=%s points_count=%d%n",
                DIM, DISTANCE, pointsBefore);

        // ===== 2. 普通检索（无 subjectCode 过滤） =====
        List<RetrievedChunk> hits = retriever.retrieve(QUERY, TOP_K, null);
        hits.forEach(R3RetrievalIT::verifyChunk);
        assertThat(hits).as("检索结果为空：请检查开发集合数据与查询文本是否相关").isNotEmpty();
        assertThat(hits.size()).as("返回数量不得超过 topK").isLessThanOrEqualTo(TOP_K);
        assertScoresNonIncreasing(hits);
        System.out.printf("普通检索：hits=%d topScore=%.4f%n", hits.size(), hits.get(0).score());

        // ===== 3. SUBJECT_1 过滤：所有返回结果必须满足过滤条件 =====
        List<RetrievedChunk> subject1Hits = retriever.retrieve(QUERY, TOP_K, "SUBJECT_1");
        subject1Hits.forEach(chunk -> {
            verifyChunk(chunk);
            assertThat(chunk.subjectCode()).as("SUBJECT_1 过滤后仍出现其他科目").isEqualTo("SUBJECT_1");
        });
        System.out.printf("SUBJECT_1 过滤：hits=%d%n", subject1Hits.size());

        // ===== 4. SUBJECT_4 过滤：若有结果则均符合；不把当前空结果固化为业务规则 =====
        List<RetrievedChunk> subject4Hits = retriever.retrieve(QUERY, TOP_K, "SUBJECT_4");
        subject4Hits.forEach(chunk -> {
            verifyChunk(chunk);
            assertThat(chunk.subjectCode()).as("SUBJECT_4 过滤后仍出现其他科目").isEqualTo("SUBJECT_4");
        });
        System.out.printf("SUBJECT_4 过滤：hits=%d（当前集合无该科目数据时为空属预期，不代表业务规则）%n",
                subject4Hits.size());

        // ===== 5. 检索后再次核验：点数必须不变（只读保证） =====
        JsonNode afterInfo = requireCollectionInfo(http);
        verifyCollection(afterInfo, "检索后");
        long pointsAfter = requirePointsCount(afterInfo, "检索后");
        System.out.printf("points_count：检索前=%d 检索后=%d%n", pointsBefore, pointsAfter);
        assertThat(pointsAfter)
                .as("检索前后 points_count 必须不变（本测试不得写入/删除任何点）")
                .isEqualTo(pointsBefore);

        System.out.println("R3RetrievalIT 完成：检索与过滤均只读，点数未变化。");
    }

    /* ============================== 集合只读核验 ============================== */

    private JsonNode requireCollectionInfo(RestTemplate http) throws Exception {
        ResponseEntity<String> resp;
        try {
            resp = http.exchange(collectionUrl(), HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
        } catch (HttpStatusCodeException e) {
            throw new AssertionError("读取集合信息失败：status=" + e.getStatusCode().value()
                    + " body=" + truncate(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            throw new AssertionError("连接 Qdrant 失败（立即停止）：" + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new AssertionError("Qdrant 请求异常（立即停止）：" + e.getMessage(), e);
        }
        assertThat(resp.getStatusCode().value()).as("读取集合信息 HTTP 状态").isEqualTo(200);
        return parseResult(resp, "读取集合信息");
    }

    private void verifyCollection(JsonNode result, String stage) {
        assertThat(result.path("status").isTextual())
                .as(stage + "：集合响应缺少 status 字段").isTrue();
        assertThat(result.path("status").asText())
                .as(stage + "：集合状态必须为 green").isEqualTo("green");

        JsonNode vectors = result.path("config").path("params").path("vectors");
        assertThat(vectors.isObject())
                .as(stage + "：缺少 config.params.vectors").isTrue();
        assertThat(vectors.path("size").isIntegralNumber())
                .as(stage + "：vectors.size 缺失或类型不符").isTrue();
        assertThat(vectors.path("size").asInt())
                .as(stage + "：集合向量维度必须为 " + DIM).isEqualTo(DIM);
        assertThat(vectors.path("distance").isTextual())
                .as(stage + "：vectors.distance 缺失或类型不符").isTrue();
        assertThat(vectors.path("distance").asText())
                .as(stage + "：集合距离类型必须为 " + DISTANCE)
                .isEqualToIgnoringCase(DISTANCE);
    }

    private long requirePointsCount(JsonNode result, String stage) {
        JsonNode pointsCount = result.path("points_count");
        assertThat(pointsCount.isIntegralNumber())
                .as(stage + "：points_count 缺失或非整数").isTrue();
        return pointsCount.asLong();
    }

    /* ============================== 结果字段复核 ============================== */

    /** 按 Retriever 的返回契约复核每条结果（防止映射层意外放松校验）。 */
    private static void verifyChunk(RetrievedChunk chunk) {
        UUID uuid;
        try {
            uuid = UUID.fromString(chunk.pointId());
        } catch (IllegalArgumentException e) {
            throw new AssertionError("返回结果的 pointId 非 UUID：" + chunk.pointId(), e);
        }
        assertThat(uuid.toString())
                .as("pointId 必须为规范 UUID 形式")
                .isEqualToIgnoringCase(chunk.pointId());
        assertThat(chunk.chunkId()).as("chunkId 非空白").isNotBlank();
        assertThat(chunk.docId()).as("docId 非空白").isNotBlank();
        assertThat(chunk.sourceType()).as("sourceType 不得为空").isNotNull();
        assertThat(chunk.sourceId()).as("sourceId 必须为正整数").isPositive();
        assertThat(chunk.text()).as("text 非空白").isNotBlank();
        assertThat(chunk.subjectCode()).as("subjectCode 非空白").isNotBlank();
    }

    private static void assertScoresNonIncreasing(List<RetrievedChunk> chunks) {
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i - 1).score())
                    .as("结果必须按 score 非递增排列（第 %d 位）", i)
                    .isGreaterThanOrEqualTo(chunks.get(i).score());
        }
    }

    /* ============================== 辅助 ============================== */

    private String collectionUrl() {
        String base = qdrantProperties.getBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/collections/" + qdrantProperties.getCollection();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(qdrantProperties.getApiKey())) {
            headers.set("api-key", qdrantProperties.getApiKey());
        }
        return headers;
    }

    private JsonNode parseResult(ResponseEntity<String> resp, String stage) throws Exception {
        assertThat(resp.getBody()).as(stage + "：响应体为空").isNotBlank();
        JsonNode root = json.readTree(resp.getBody());
        JsonNode result = root.path("result");
        assertThat(result.isMissingNode() || result.isNull())
                .as(stage + "：响应缺少 result 字段，立即停止").isFalse();
        return result;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= LOG_BODY_MAX_LEN ? s : s.substring(0, LOG_BODY_MAX_LEN) + "...(truncated)";
    }
}
