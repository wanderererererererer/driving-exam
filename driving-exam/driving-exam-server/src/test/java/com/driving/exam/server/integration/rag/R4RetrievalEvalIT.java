package com.driving.exam.server.integration.rag;

import com.driving.exam.server.integration.embedding.EmbeddingClient;
import com.driving.exam.server.integration.embedding.EmbeddingProperties;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.rag.ingest.KbDevIngestGuard;
import com.driving.exam.server.rag.ingest.VectorConverter;
import com.driving.exam.server.rag.retrieve.KnowledgeRetriever;
import com.driving.exam.server.rag.retrieve.RetrievedChunk;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索质量评估入口（手动运行、默认跳过、<b>非 Spring</b>，参照 {@code R24QdrantIntegrationIT} 范式）。
 *
 * <p>读取冻结评估集 {@code /rag-eval/eval-set-v1.json}（27 条 = 26 gold + 1 负样本），逐条调用
 * {@link KnowledgeRetriever}（topK 使用 {@code DEFAULT_TOP_K=5}），输出逐条结果与汇总指标。</p>
 *
 * <h3>安全边界</h3>
 * <ul>
 *   <li><b>不启动 Spring</b>：手工构造 {@link EmbeddingClient}/{@link QdrantClient} → 不创建 DataSource、
 *       不连业务数据库、不写任何表；</li>
 *   <li><b>只读</b>：检索仅调用 Embedding 与 Qdrant {@code POST /points/query}；不调用 DeepSeek、
 *       不调用入库服务、不创建/更新/删除集合或点；</li>
 *   <li>运行前断言：集合名严格等于 {@code driving_exam_kb_dev}（复用 {@link KbDevIngestGuard}），
 *       并只读 GET 核对 {@code green/1024/Cosine/points_count>0}；<b>不</b>调用 ensureCollection；</li>
 *   <li>双重门禁：类名 {@code *IT}（默认 Surefire 不收集）+ 环境变量 {@code RAG_DEV_INGEST=true}；</li>
 *   <li>不打印密钥；错误信息仅打印异常类型与截断后的消息。</li>
 * </ul>
 *
 * <h3>指标口径（与评估方案一致）</h3>
 * <ul>
 *   <li>主指标按<b>来源 ID</b>：Hit@1 / Hit@5 / MRR，分母 = <b>gold 且查询成功</b>的样本数（记 M）；
 *       空结果计为未命中；<b>ERROR 样本不计入分母</b>，单独报告（错误与未命中严格区分）；</li>
 *   <li>补充指标 1：内容命中@1/@5——返回文本（trim 后）与任一 gold 解析原文逐字相同即算命中（忽略来源 ID）；</li>
 *   <li>补充指标 2：重复率——（a）微平均：重复条目数 / 实际返回条目数（仅统计查询成功的 gold 样本；
 *       分母用各次实际返回数）；（b）查询级：出现逐字重复的查询数 / 返回 ≥2 条的查询数；</li>
 *   <li>负样本（E27）只输出返回条数与分数观察，不进入任何命中/重复率分母。</li>
 * </ul>
 *
 * <p><b>运行前置</b>：Qdrant 在线；{@code SILICONFLOW_API_KEY} 与 {@code RAG_DEV_INGEST=true} 已设置
 * （同一 PowerShell 会话）；评估集已冻结，不得按结果反向修改。</p>
 *
 * <p><b>成本</b>：每条查询 1 次 Embedding（短文本）+ 1 次只读 Qdrant 查询；无 DeepSeek 费用、无数据库写入。</p>
 */
@EnabledIfEnvironmentVariable(named = "RAG_DEV_INGEST", matches = "true")
class R4RetrievalEvalIT {

    private static final String QDRANT_URL = "http://localhost:6333";
    private static final String COLLECTION = "driving_exam_kb_dev";
    private static final int DIM = 1024;
    private static final String DISTANCE = "Cosine";
    private static final String EVAL_RESOURCE = "/rag-eval/eval-set-v1.json";
    private static final int LOG_BODY_MAX_LEN = 500;
    private static final int ERROR_MSG_MAX_LEN = 200;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("检索质量评估（gold=26、负样本=1；只读，手动运行）")
    void evaluateRetrieval() throws Exception {
        // ===== 0. 环境与配置断言（任何网络调用之前） =====
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        assertThat(apiKey != null && !apiKey.isBlank())
                .as("环境变量 SILICONFLOW_API_KEY 未设置，无法执行检索评估（请先在同一会话设置）")
                .isTrue();

        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setApiKey(apiKey); // 不记录/不输出

        QdrantProperties qdrantProperties = new QdrantProperties();
        qdrantProperties.setBaseUrl(QDRANT_URL);
        qdrantProperties.setCollection(COLLECTION);
        qdrantProperties.setVectorSize(DIM);
        qdrantProperties.setDistance(DISTANCE);

        KbDevIngestGuard.requireDevCollection(qdrantProperties.getCollection());
        KbDevIngestGuard.requireValidatedVectorConfig(qdrantProperties);
        assertThat(qdrantProperties.getBaseUrl()).as("Qdrant 地址必须是固定评估地址").isEqualTo(QDRANT_URL);
        assertThat(qdrantProperties.getCollection()).as("目标集合必须严格为开发集合").isEqualTo(COLLECTION);

        EmbeddingClient embeddingClient = new EmbeddingClient(embeddingProperties, new RestTemplateBuilder());
        QdrantClient qdrantClient = new QdrantClient(qdrantProperties, new RestTemplateBuilder());
        KnowledgeRetriever retriever = new KnowledgeRetriever(
                embeddingClient, qdrantClient, qdrantProperties, new VectorConverter());

        RestTemplate http = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();

        // ===== 1. 集合只读核验（不创建） =====
        JsonNode info = requireCollectionInfo(http);
        long pointsCount = verifyCollection(info);
        System.out.printf("集合核验通过：collection=%s status=green size=%d distance=%s points_count=%d%n",
                COLLECTION, DIM, DISTANCE, pointsCount);

        // ===== 2. 读取冻结评估集并做完整性断言 =====
        List<EvalSample> samples = loadSamples();
        long goldCount = samples.stream()
                .filter(s -> !"negative".equals(s.goldType()) && !s.expectedSourceIds().isEmpty())
                .count();
        long negativeCount = samples.stream().filter(s -> "negative".equals(s.goldType())).count();
        assertThat(samples).as("评估集样本数应为 27（冻结版 v1）").hasSize(27);
        assertThat(goldCount).as("gold 样本数应为 26").isEqualTo(26);
        assertThat(negativeCount).as("负样本数应为 1").isEqualTo(1);
        assertThat(new HashSet<>(samples.stream().map(EvalSample::evalId).toList()))
                .as("evalId 必须唯一").hasSize(samples.size());
        System.out.printf("评估集加载：total=%d gold=%d negative=%d%n", samples.size(), goldCount, negativeCount);

        // ===== 3. 逐条执行（单条 ERROR 记录后继续；ERROR 不计为普通未命中） =====
        int successQueries = 0;
        int errorQueries = 0;
        int goldSuccess = 0;
        int hit1 = 0;
        int hit5 = 0;
        double mrrSum = 0.0;
        int contentHit1 = 0;
        int contentHit5 = 0;
        int emptyResults = 0;
        int fewerThan5Results = 0;
        long duplicateItems = 0;
        long returnedItems = 0;
        int queriesWithAtLeast2 = 0;
        int queriesWithDuplicates = 0;
        Map<String, CategoryStat> categoryStats = new LinkedHashMap<>();
        for (EvalSample s : samples) {
            if (!"negative".equals(s.goldType()) && !s.expectedSourceIds().isEmpty()) {
                categoryStats.computeIfAbsent(s.category(), k -> new CategoryStat()).total++;
            }
        }

        for (EvalSample sample : samples) {
            boolean isGold = !"negative".equals(sample.goldType()) && !sample.expectedSourceIds().isEmpty();
            List<RetrievedChunk> chunks;
            try {
                chunks = retriever.retrieve(sample.query(), KnowledgeRetriever.DEFAULT_TOP_K, sample.subjectCode());
                successQueries++;
            } catch (RuntimeException e) {
                errorQueries++;
                System.out.printf("[%s] ERROR %s: %s%n", sample.evalId(),
                        e.getClass().getSimpleName(), truncate(e.getMessage(), ERROR_MSG_MAX_LEN));
                continue;
            }

            // 逐条输出 top5：来源 ID、分数、重复标记
            Set<String> seenTexts = new HashSet<>();
            int dupInQuery = 0;
            StringBuilder line = new StringBuilder("[" + sample.evalId() + "] " + sample.category()
                    + " | query=" + sample.query() + " | gold=" + sample.expectedSourceIds() + " | results:");
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk c = chunks.get(i);
                String textKey = c.text().trim();
                boolean dup = !seenTexts.add(textKey);
                if (dup) {
                    dupInQuery++;
                }
                line.append(String.format(" #%d id=%d score=%.4f%s",
                        i + 1, c.sourceId(), c.score(), dup ? " DUP" : ""));
            }
            if (chunks.isEmpty()) {
                line.append(" <空结果>");
            }
            System.out.println(line);

            if (isGold) {
                goldSuccess++;
                if (chunks.isEmpty()) {
                    emptyResults++;
                } else if (chunks.size() < KnowledgeRetriever.DEFAULT_TOP_K) {
                    fewerThan5Results++;
                }

                // 主指标（按来源 ID）
                Set<Long> goldIds = new LinkedHashSet<>(sample.expectedSourceIds());
                if (!chunks.isEmpty() && goldIds.contains(chunks.get(0).sourceId())) {
                    hit1++;
                }
                int firstHitRank = 0;
                for (int i = 0; i < chunks.size(); i++) {
                    if (goldIds.contains(chunks.get(i).sourceId())) {
                        hit5++;
                        firstHitRank = i + 1;
                        break;
                    }
                }
                if (firstHitRank > 0) {
                    mrrSum += 1.0 / firstHitRank;
                }

                // 补充指标 1：内容命中（忽略来源 ID，按解析原文比对）
                Set<String> goldTexts = new HashSet<>();
                for (String t : sample.expectedTexts().values()) {
                    goldTexts.add(t.trim());
                }
                if (!chunks.isEmpty() && goldTexts.contains(chunks.get(0).text().trim())) {
                    contentHit1++;
                }
                for (RetrievedChunk c : chunks) {
                    if (goldTexts.contains(c.text().trim())) {
                        contentHit5++;
                        break;
                    }
                }

                // 补充指标 2：重复率（金标样本、成功查询）
                if (!chunks.isEmpty()) {
                    duplicateItems += dupInQuery;
                    returnedItems += chunks.size();
                }
                if (chunks.size() >= 2) {
                    queriesWithAtLeast2++;
                    if (dupInQuery > 0) {
                        queriesWithDuplicates++;
                    }
                }

                CategoryStat stat = categoryStats.get(sample.category());
                stat.success++;
                stat.hit1 += (firstHitRank == 1) ? 1 : 0;
                stat.hit5 += (firstHitRank > 0) ? 1 : 0;
                stat.mrrSum += (firstHitRank > 0) ? 1.0 / firstHitRank : 0.0;
            } else {
                // 负样本：仅观察
                System.out.printf("[%s] 负样本观察：returned=%d scores=%s%n", sample.evalId(), chunks.size(),
                        chunks.stream().map(c -> String.format("%.4f", c.score())).toList());
            }
        }

        // ===== 4. 汇总输出 =====
        System.out.println("===== 汇总 =====");
        System.out.printf("样本总数=%d gold样本=%d 负样本=%d%n", samples.size(), goldCount, negativeCount);
        System.out.printf("查询成功=%d 查询失败(ERROR)=%d（ERROR 不计入命中指标分母，与未命中严格区分）%n",
                successQueries, errorQueries);
        System.out.printf("gold 成功样本 M=%d（其中空结果=%d，返回 1~4 条=%d）%n",
                goldSuccess, emptyResults, fewerThan5Results);
        if (goldSuccess > 0) {
            System.out.printf("Hit@1=%d/%d=%.3f%n", hit1, goldSuccess, (double) hit1 / goldSuccess);
            System.out.printf("Hit@5=%d/%d=%.3f%n", hit5, goldSuccess, (double) hit5 / goldSuccess);
            System.out.printf("MRR=%.4f（分母 M=%d）%n", mrrSum / goldSuccess, goldSuccess);
            System.out.printf("内容命中@1=%d/%d=%.3f；内容命中@5=%d/%d=%.3f%n",
                    contentHit1, goldSuccess, (double) contentHit1 / goldSuccess,
                    contentHit5, goldSuccess, (double) contentHit5 / goldSuccess);
        } else {
            System.out.println("无可用的 gold 成功样本，无法计算命中类指标");
        }
        if (returnedItems > 0) {
            System.out.printf("重复（微平均）：重复条目=%d / 返回条目=%d = %.3f%n",
                    duplicateItems, returnedItems, (double) duplicateItems / returnedItems);
        } else {
            System.out.println("重复（微平均）：无返回条目，无法计算");
        }
        System.out.printf("重复（查询级）：出现重复的查询=%d / 返回≥2条的查询=%d%n",
                queriesWithDuplicates, queriesWithAtLeast2);

        System.out.println("===== 分类统计（gold 样本） =====");
        for (Map.Entry<String, CategoryStat> entry : categoryStats.entrySet()) {
            CategoryStat s = entry.getValue();
            System.out.printf("%s：total=%d success=%d hit@1=%d hit@5=%d MRR=%.4f%n",
                    entry.getKey(), s.total, s.success, s.hit1, s.hit5,
                    s.success > 0 ? s.mrrSum / s.success : 0.0);
        }

        System.out.println("R4RetrievalEvalIT 完成（本输出为评估基线，不预设通过线）。");
    }

    /* ============================== 评估集加载与校验 ============================== */

    private List<EvalSample> loadSamples() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(EVAL_RESOURCE)) {
            assertThat(in).as("评估集资源缺失：" + EVAL_RESOURCE).isNotNull();
            JsonNode root = json.readTree(in);
            JsonNode samplesNode = root.path("samples");
            assertThat(samplesNode.isArray()).as("评估集缺少 samples 数组").isTrue();
            List<EvalSample> samples = json.convertValue(samplesNode,
                    new TypeReference<List<EvalSample>>() {
                    });
            for (EvalSample sample : samples) {
                assertThat(sample.evalId()).isNotBlank();
                assertThat(sample.query()).isNotBlank();
                assertThat(sample.subjectCode()).isNotBlank();
                assertThat(sample.expectedSourceIds()).isNotNull();
                if (!"negative".equals(sample.goldType())) {
                    assertThat(sample.expectedSourceIds()).as(sample.evalId() + " gold 不能为空").isNotEmpty();
                    assertThat(sample.expectedTexts()).as(sample.evalId() + " 缺少 expectedTexts").isNotEmpty();
                }
            }
            return samples;
        }
    }

    /* ============================== 集合只读核验 ============================== */

    private JsonNode requireCollectionInfo(RestTemplate http) throws Exception {
        ResponseEntity<String> resp;
        try {
            resp = http.exchange(QDRANT_URL + "/collections/" + COLLECTION, HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders()), String.class);
        } catch (RuntimeException e) {
            throw new AssertionError("读取集合信息失败（立即停止，不创建集合）：" + e.getMessage(), e);
        }
        assertThat(resp.getStatusCode().value()).as("读取集合信息 HTTP 状态").isEqualTo(200);
        assertThat(resp.getBody()).as("集合信息响应体为空").isNotBlank();
        JsonNode result = json.readTree(resp.getBody()).path("result");
        assertThat(result.isMissingNode() || result.isNull()).as("集合信息缺少 result 字段").isFalse();
        return result;
    }

    private long verifyCollection(JsonNode result) {
        assertThat(result.path("status").asText()).as("集合状态必须为 green").isEqualTo("green");
        JsonNode vectors = result.path("config").path("params").path("vectors");
        assertThat(vectors.path("size").asInt()).as("集合维度必须为 1024").isEqualTo(DIM);
        assertThat(vectors.path("distance").asText()).as("集合距离必须为 Cosine")
                .isEqualToIgnoringCase(DISTANCE);
        JsonNode points = result.path("points_count");
        assertThat(points.isIntegralNumber()).as("points_count 缺失或非整数").isTrue();
        long count = points.asLong();
        assertThat(count).as("开发集合为空：请先执行 DevKbIngestIT 完成入库").isPositive();
        return count;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /* ============================== 辅助 ============================== */

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "null";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...(truncated)";
    }

    /** 分类统计累计器（仅 gold 样本）。 */
    private static final class CategoryStat {
        int total;
        int success;
        int hit1;
        int hit5;
        double mrrSum;
    }

    /**
     * 评估样本（对应 eval-set-v1.json 的字段；Jackson 通过 record 组件反序列化）。
     */
    record EvalSample(
            String evalId,
            String query,
            String subjectCode,
            String category,
            List<Long> expectedSourceIds,
            String goldType,
            Map<String, String> standardAnswers,
            String rationale,
            String confusionNotes,
            String duplicateNotes,
            Map<String, String> expectedTexts) {
    }
}
