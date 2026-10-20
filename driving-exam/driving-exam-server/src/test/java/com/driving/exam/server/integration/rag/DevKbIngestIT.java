package com.driving.exam.server.integration.rag;

import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.rag.DocumentChunk;
import com.driving.exam.server.rag.KnowledgeDocument;
import com.driving.exam.server.rag.assemble.KnowledgeDocumentAssembler;
import com.driving.exam.server.rag.ingest.ChunkPointMapper;
import com.driving.exam.server.rag.ingest.IngestException;
import com.driving.exam.server.rag.ingest.IngestResult;
import com.driving.exam.server.rag.ingest.KbDevIngestGuard;
import com.driving.exam.server.rag.ingest.KnowledgeIngestService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 开发知识库入库集成入口（**手动运行、默认跳过、不随普通构建执行**）。
 *
 * <p>受控验证完整链路：数据库读取 → 文档装配 → 分块 → Embedding → 写入 Qdrant
 * {@code driving_exam_kb_dev} → 写后只读回查。</p>
 *
 * <h3>双重门禁</h3>
 * <ol>
 *   <li>类名以 {@code IT} 结尾——项目 surefire 使用默认规则（{@code *Test}/{@code Test*}/{@code *Tests}/{@code *TestCase}），
 *       本类<b>不会</b>被普通 {@code mvn test} 执行；只能显式指定：
 *       {@code mvn -pl driving-exam-server -am test -Dtest=DevKbIngestIT "-Dsurefire.failIfNoSpecifiedTests=false"}；</li>
 *   <li>{@link EnabledIfEnvironmentVariable} {@code RAG_DEV_INGEST=true}——未显式设置环境变量时，
 *       JUnit 在执行条件阶段即跳过，<b>Spring 上下文不会被创建</b>，绑定/装配/入库均不会发生。</li>
 * </ol>
 *
 * <h3>目标集合与向量配置（不依赖外部参数）</h3>
 * <p>通过 {@code @SpringBootTest(properties=...)} 内联属性固定
 * {@code qdrant.collection=driving_exam_kb_dev} 与 {@code qdrant.vector-size=1024}
 * （内联测试属性优先级高于 application.yml；不假定 Maven {@code -D} 会绑定到 Spring 属性）。
 * 随后由 {@link KbDevIngestGuard} 对<b>实际绑定后的 Bean</b>再次复核，任何不符在任何网络调用之前失败。</p>
 *
 * <h3>严格执行顺序（任何一步失败立即中断，不降级、不回退）</h3>
 * <ol>
 *   <li>守卫：集合名逐字符等于 {@code driving_exam_kb_dev}；vectorSize=1024、distance=Cosine；</li>
 *   <li>只读 GET 集合信息：{@code status=green}、{@code size=1024}、{@code distance=Cosine}、
 *       {@code points_count} 为整数；记录写前点数；</li>
 *   <li><b>仅当明确收到 HTTP 404</b> 时，才允许调用 {@code QdrantClient.ensureCollection()} 创建开发集合，
 *       随后立即重新 GET 并重新验证；连接失败/认证错误/其他 4xx/5xx/字段缺失或类型不符一律停止；</li>
 *   <li>装配（只读数据库）：输出完整统计并断言数量不变式；空文档集立即停止；</li>
 *   <li>{@code KnowledgeIngestService.ingest(...)}：内部完成分块与 Embedding（≤32/批）与 upsert；</li>
 *   <li>写后重新 GET：输出写前/写后 {@code points_count} 及变化量，说明「本次 upsert 点数」与「点数变化」不一定相等；</li>
 *   <li>抽样只读回查：用生产 {@link ChunkPointMapper} 计算首文档 chunk#0 的确定性 pointId，
 *       按 ID 取点，核对 id / payload（docId、sourceType、sourceId、chunkId）与向量维度 1024。</li>
 * </ol>
 *
 * <h3>安全红线</h3>
 * <ul>
 *   <li>不删除、不清空、不重建任何集合或点；唯一可能发生的写操作是守卫通过后的开发集合创建（仅 404 分支）
 *       与业务 upsert；</li>
 *   <li>生产集合 {@code driving_exam_kb}、集成测试集合 {@code driving_exam_kb_it} 绝不触碰；</li>
 *   <li>任何日志/断言消息<b>不打印</b> API Key、数据库口令或访问令牌；</li>
 *   <li>入库不是事务性操作：批次失败时报告已写入批次/点数后原样抛出，<b>不</b>声称回滚。</li>
 * </ul>
 *
 * <h3>运行前置条件（仅在实际执行时）</h3>
 * <p>本机 MySQL（运行配置指向的业务库）可连且<b>只读使用</b>；Qdrant 可连；环境变量
 * {@code SILICONFLOW_API_KEY} 已设置；shell 中设置 {@code RAG_DEV_INGEST=true}。</p>
 *
 * <p><b>上下文初始化阶段不会触网</b>：仓库内无 {@code CommandLineRunner}/{@code ApplicationRunner}/
 * {@code @PostConstruct} 或监听器发起 Qdrant/Embedding 调用；客户端 Bean 仅构建 RestTemplate。
 * 守卫因此先于本类的任何外部调用执行（见类内流程）。</p>
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
class DevKbIngestIT {

    private static final String COLLECTION = "driving_exam_kb_dev";
    private static final int DIM = 1024;
    private static final String DISTANCE = "Cosine";
    private static final int LOG_BODY_MAX_LEN = 500;

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private KnowledgeDocumentAssembler assembler;

    @Autowired
    private KnowledgeIngestService ingestService;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private QdrantProperties qdrantProperties;

    @Autowired
    private ChunkPointMapper chunkPointMapper;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Test
    @DisplayName("装配 → Embedding → 写入 driving_exam_kb_dev → 写后只读回查（手动运行）")
    void devIngestEndToEnd() throws Exception {
        // ===== 0. 守卫：必须发生在任何 Qdrant / Embedding 调用之前 =====
        KbDevIngestGuard.requireDevCollection(qdrantProperties.getCollection());
        KbDevIngestGuard.requireValidatedVectorConfig(qdrantProperties);
        System.out.printf("守卫通过：collection=%s vectorSize=%s distance=%s%n",
                qdrantProperties.getCollection(), qdrantProperties.getVectorSize(), qdrantProperties.getDistance());

        RestTemplate http = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();

        // ===== 1. 只读集合信息 / 明确 404 时才允许创建，随后重新读取验证 =====
        JsonNode info = loadOrCreateDevCollection(http);
        verifyCollectionConfig(info, "写入前");
        long pointsBefore = requirePointsCount(info, "写入前");
        System.out.printf("集合核验通过：status=green size=%d distance=%s points_count=%d%n",
                DIM, DISTANCE, pointsBefore);

        // ===== 2. 装配（只读数据库） =====
        KnowledgeDocumentAssembler.AssemblyResult assembly = assembler.assemble();
        printAssemblyStats(assembly);

        assertThat(assembly.questionReadCount())
                .as("装配统计不变式：题目读取数 = 状态过滤 + 科目过滤 + 空白解析跳过 + 题目文档数")
                .isEqualTo(assembly.questionSkippedByStatusCount()
                        + assembly.questionSkippedBySubjectCount()
                        + assembly.questionSkippedByBlankAnalysisCount()
                        + assembly.questionDocumentCount());
        assertThat(assembly.knowledgePointReadCount())
                .as("装配统计不变式：知识点读取数 = 空白描述跳过 + 知识点文档数")
                .isEqualTo(assembly.knowledgePointSkippedByBlankDescriptionCount()
                        + assembly.knowledgePointDocumentCount());
        assertThat(assembly.totalDocumentCount())
                .as("总文档数必须等于 documents().size()")
                .isEqualTo(assembly.documents().size());
        assertThat(assembly.documents())
                .as("装配结果为空：停止，不得调用 Embedding 或写入")
                .isNotEmpty();

        // ===== 3. 入库（分块 → Embedding ≤32/批 → upsert；失败如实报告部分进度后抛出） =====
        IngestResult ingestResult;
        try {
            ingestResult = ingestService.ingest(assembly.documents());
        } catch (IngestException e) {
            System.out.println("========== 入库失败（非事务性；已写入批次不回滚；失败批次远端状态未知） ==========");
            System.out.printf("失败批次=%d（0-based） 已写入批次=%d 已写入点数=%d 总块数=%d%n",
                    e.getFailedBatchIndex(), e.getWrittenBatchCount(),
                    e.getWrittenPointCount(), e.getTotalChunkCount());
            throw e;
        }
        System.out.printf("入库完成：documents=%d chunks=%d batches=%d embeddedVectors=%d writtenPoints=%d%n",
                ingestResult.documentCount(), ingestResult.chunkCount(), ingestResult.batchCount(),
                ingestResult.embeddedVectorCount(), ingestResult.writtenPointCount());

        // ===== 4. 写后只读核验 points_count =====
        JsonNode afterInfo = requireCollectionInfo(http);
        verifyCollectionConfig(afterInfo, "写入后");
        long pointsAfter = requirePointsCount(afterInfo, "写入后");
        long delta = pointsAfter - pointsBefore;
        System.out.printf("集合 points_count：写前=%d 写后=%d 变化=%d；本次 upsert 点数=%d%n",
                pointsBefore, pointsAfter, delta, ingestResult.writtenPointCount());
        System.out.println("注意：确定性 pointId 覆盖旧点时，点数变化量会小于本次 upsert 点数；"
                + "首次写入空集合时二者应相等。");
        assertThat(pointsAfter)
                .as("points_count 不应减少（本入口不删除任何点）")
                .isGreaterThanOrEqualTo(pointsBefore);

        // ===== 5. 写后抽样回查（只用生产 ChunkPointMapper 计算 pointId，不复制算法） =====
        KnowledgeDocument sample = assembly.documents().get(0);
        String pointId = pointIdOfFirstChunk(sample);
        System.out.printf("抽样回查：docId=%s pointId=%s%n", sample.docId(), pointId);
        verifyPointById(http, pointId, sample);

        System.out.println("DevKbIngestIT 完成：装配 → 入库 → 写后核验全部通过。");
    }

    /* ============================== 集合读取 ============================== */

    /** 只读 GET；明确 404 → 守卫已通过，允许创建开发集合并重新读取验证；其他任何错误立即停止。 */
    private JsonNode loadOrCreateDevCollection(RestTemplate http) throws Exception {
        try {
            ResponseEntity<String> resp = http.exchange(collectionUrl(), HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()), String.class);
            assertThat(resp.getStatusCode().value()).as("读取集合信息 HTTP 状态").isEqualTo(200);
            return parseResult(resp, "读取集合信息");
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() != 404) {
                throw new AssertionError("读取目标集合失败：status=" + e.getStatusCode().value()
                        + "（仅 404 才允许创建，其他一律停止）body=" + truncate(e.getResponseBodyAsString()), e);
            }
            System.out.println("目标集合不存在（明确 404）：守卫已通过，按批准策略创建开发集合 " + COLLECTION);
            qdrantClient.ensureCollection();
            // 创建后必须重新读取并验证（任何不符合立即失败）
            return requireCollectionInfo(http);
        } catch (ResourceAccessException e) {
            throw new AssertionError("连接 Qdrant 失败（无法确认集合状态，立即停止）：" + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new AssertionError("Qdrant 请求异常（立即停止）：" + e.getMessage(), e);
        }
    }

    /** 只读 GET（集合此时必须已存在；失败即抛，不降级）。 */
    private JsonNode requireCollectionInfo(RestTemplate http) throws Exception {
        ResponseEntity<String> resp;
        try {
            resp = http.exchange(collectionUrl(), HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()), String.class);
        } catch (HttpStatusCodeException e) {
            throw new AssertionError("重新读取集合信息失败：status=" + e.getStatusCode().value()
                    + " body=" + truncate(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            throw new AssertionError("重新读取集合信息时连接失败：" + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new AssertionError("重新读取集合信息请求异常：" + e.getMessage(), e);
        }
        assertThat(resp.getStatusCode().value()).as("重新读取集合信息 HTTP 状态").isEqualTo(200);
        return parseResult(resp, "重新读取集合信息");
    }

    /** 核验集合参数；字段缺失/类型不符/取值不符一律立即失败。 */
    private void verifyCollectionConfig(JsonNode result, String stage) {
        assertThat(result.path("status").isTextual())
                .as(stage + "：集合响应缺少 status 字段").isTrue();
        assertThat(result.path("status").asText())
                .as(stage + "：集合状态必须为 green").isEqualTo("green");

        JsonNode vectors = result.path("config").path("params").path("vectors");
        assertThat(vectors.isObject())
                .as(stage + "：缺少 config.params.vectors（字段缺失或类型不符，立即停止）").isTrue();
        assertThat(vectors.path("size").isIntegralNumber())
                .as(stage + "：vectors.size 缺失或类型不符，立即停止").isTrue();
        assertThat(vectors.path("size").asInt())
                .as(stage + "：集合向量维度必须为 " + DIM).isEqualTo(DIM);
        assertThat(vectors.path("distance").isTextual())
                .as(stage + "：vectors.distance 缺失或类型不符，立即停止").isTrue();
        assertThat(vectors.path("distance").asText())
                .as(stage + "：集合距离类型必须为 " + DISTANCE)
                .isEqualToIgnoringCase(DISTANCE);
    }

    private long requirePointsCount(JsonNode result, String stage) {
        JsonNode pointsCount = result.path("points_count");
        assertThat(pointsCount.isIntegralNumber())
                .as(stage + "：points_count 缺失或非整数，立即停止").isTrue();
        return pointsCount.asLong();
    }

    /* ============================== 抽样回查 ============================== */

    /** 用生产 ChunkPointMapper 计算首文档 chunk#0 的确定性 pointId（不复制哈希算法）。 */
    private String pointIdOfFirstChunk(KnowledgeDocument document) {
        DocumentChunk probe = new DocumentChunk(
                document.docId() + "#0", document.docId(), document.sourceType(),
                document.sourceId(), 0, "probe", document.metadata());
        return chunkPointMapper.toPoint(probe, document.subjectCode()).pointId();
    }

    /** 按 ID 只读取点，核对 pointId / 必要 payload 字段 / 向量维度。 */
    private void verifyPointById(RestTemplate http, String pointId, KnowledgeDocument document) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", List.of(pointId));
        body.put("with_payload", true);
        body.put("with_vector", true);

        ResponseEntity<String> resp;
        try {
            resp = http.exchange(collectionUrl() + "/points", HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), String.class);
        } catch (HttpStatusCodeException e) {
            throw new AssertionError("retrieve 回查失败：status=" + e.getStatusCode().value()
                    + " body=" + truncate(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new AssertionError("retrieve 回查请求异常：" + e.getMessage(), e);
        }
        assertThat(resp.getStatusCode().value()).as("retrieve 回查 HTTP 状态").isEqualTo(200);

        JsonNode result = parseResult(resp, "retrieve 回查");
        assertThat(result.isArray()).as("retrieve result 应为数组").isTrue();
        assertThat(result.size()).as("应回查到本次写入的 1 个点 pointId=" + pointId).isEqualTo(1);

        JsonNode point = result.get(0);
        assertThat(point.path("id").asText()).as("回查点 ID 与写入一致").isEqualTo(pointId);
        assertThat(point.path("payload").path("docId").asText())
                .as("payload.docId 与装配文档一致").isEqualTo(document.docId());
        assertThat(point.path("payload").path("sourceType").asText())
                .as("payload.sourceType 与装配文档一致").isEqualTo(document.sourceType().name());
        assertThat(point.path("payload").path("sourceId").asLong())
                .as("payload.sourceId 与装配文档一致").isEqualTo(document.sourceId());
        assertThat(point.path("payload").path("chunkId").asText())
                .as("payload.chunkId 应为首块 " + document.docId() + "#0")
                .isEqualTo(document.docId() + "#0");

        JsonNode vector = point.path("vector");
        assertThat(vector.isArray()).as("回查应包含向量").isTrue();
        assertThat(vector.size()).as("回查向量维度必须为 " + DIM).isEqualTo(DIM);
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

    private void printAssemblyStats(KnowledgeDocumentAssembler.AssemblyResult r) {
        System.out.println("========== 装配统计（数据库只读） ==========");
        System.out.printf("题目：读取=%d 状态过滤=%d 科目过滤=%d 空白解析跳过=%d 生成文档=%d%n",
                r.questionReadCount(), r.questionSkippedByStatusCount(), r.questionSkippedBySubjectCount(),
                r.questionSkippedByBlankAnalysisCount(), r.questionDocumentCount());
        System.out.printf("知识点：读取=%d 空白描述跳过=%d 生成文档=%d%n",
                r.knowledgePointReadCount(), r.knowledgePointSkippedByBlankDescriptionCount(),
                r.knowledgePointDocumentCount());
        System.out.printf("文档总数=%d（documents().size()=%d）%n",
                r.totalDocumentCount(), r.documents().size());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= LOG_BODY_MAX_LEN ? s : s.substring(0, LOG_BODY_MAX_LEN) + "...(truncated)";
    }
}
