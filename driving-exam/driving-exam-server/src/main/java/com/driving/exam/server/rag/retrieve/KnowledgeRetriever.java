package com.driving.exam.server.rag.retrieve;

import com.driving.exam.server.integration.embedding.EmbeddingClient;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.integration.qdrant.ScoredPoint;
import com.driving.exam.server.rag.KnowledgeDocType;
import com.driving.exam.server.rag.ingest.VectorConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识检索服务（R3，<b>只读</b>：不修改任何数据）。
 *
 * <p>把一段文本查询转换为"经严格校验的检索结果列表"：</p>
 * <ol>
 *   <li>参数校验：query 非空白、topK∈[1, {@link #MAX_TOP_K}]、{@code qdrant.vector-size} 已配置；</li>
 *   <li>Embedding：复用 {@link EmbeddingClient#embed(List)}，每次仅提交一个 query，须恰好返回 1 条向量；</li>
 *   <li>维度/有限值校验：复用 {@link VectorConverter#toFloatVector(double[], int)}；</li>
 *   <li>检索：复用 {@link QdrantClient#search(float[], int, String)}，subjectCode 原样透传
 *       （null/空白由客户端语义处理为"不过滤"）；不重复实现过滤、不重排结果；</li>
 *   <li>响应严格校验：任一条结果不合法 → <b>整次失败</b>，绝不返回部分结果。</li>
 * </ol>
 *
 * <h3>边界与异常约定</h3>
 * <ul>
 *   <li>本地入参错误（query/topK）→ {@link IllegalArgumentException}；</li>
 *   <li>配置或返回结构违反内部契约（vector-size 未配置、向量数量不符、point ID 非规范 UUID、
 *       payload 缺失/字段非法）→ {@link IllegalStateException}；</li>
 *   <li>Embedding/Qdrant 的下游异常（含 {@code BusinessException}）<b>原样传播</b>，不吞掉、不转换为空列表；</li>
 *   <li>检索成功但无命中 → 返回空列表（与"调用失败"严格区分）；</li>
 *   <li>不新增错误码；不硬编码集合名（由 {@link QdrantProperties} 决定）；不访问数据库。</li>
 * </ul>
 *
 * <h3>payload 校验契约（与写入侧 ChunkPointMapper 对应）</h3>
 * <p>{@code chunkId / docId / sourceType / sourceId / text / subjectCode} 六字段必须齐全且类型正确：
 * 字符串字段非空白；{@code sourceType} 必须为 {@link KnowledgeDocType} 枚举名；{@code sourceId}
 * 必须为正整数 JSON 数值——仅接受 Jackson 反序列化后的 {@link Integer}/{@link Long}，拒绝小数
 * （{@code Double}/{@code BigDecimal}）、字符串、布尔、null、零/负数及超出 long 范围的大整数
 * （{@link java.math.BigInteger}），不做任何截断或宽松转换。</p>
 */
@Slf4j
@Component
public class KnowledgeRetriever {

    /** 默认返回条数。 */
    public static final int DEFAULT_TOP_K = 5;

    /** 业务层允许的最大返回条数。 */
    public static final int MAX_TOP_K = 20;

    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;
    private final VectorConverter vectorConverter;

    public KnowledgeRetriever(EmbeddingClient embeddingClient,
                              QdrantClient qdrantClient,
                              QdrantProperties qdrantProperties,
                              VectorConverter vectorConverter) {
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
        this.vectorConverter = vectorConverter;
    }

    /**
     * 执行一次相似度检索。
     *
     * @param query       查询文本（不能为 null/空白）
     * @param topK        返回条数（1–{@link #MAX_TOP_K}）
     * @param subjectCode 可选科目过滤（null/空白 = 不过滤；非空时精确匹配，由 QdrantClient 构造 filter）
     * @return 按 Qdrant 返回顺序（Cosine 下 score 非递增）的结果列表；无命中时为 {@code List.of()}
     * @throws IllegalArgumentException query 空白或 topK 非法
     * @throws IllegalStateException    vector-size 未配置、Embedding 返回数量不符、检索结果结构非法
     */
    public List<RetrievedChunk> retrieve(String query, int topK, String subjectCode) {
        requireQuery(query);
        requireTopK(topK);
        int dimension = requireVectorSize();

        List<double[]> vectors = embeddingClient.embed(List.of(query));
        if (vectors == null || vectors.size() != 1) {
            throw new IllegalStateException("Embedding 返回向量数量与输入不一致 expected=1 actual="
                    + (vectors == null ? "null" : vectors.size()));
        }
        float[] queryVector = vectorConverter.toFloatVector(vectors.get(0), dimension);

        List<ScoredPoint> points = qdrantClient.search(queryVector, topK, subjectCode);
        if (points == null) {
            throw new IllegalStateException("QdrantClient.search 返回 null（违反内部契约）");
        }

        List<RetrievedChunk> chunks = new ArrayList<>(points.size());
        for (ScoredPoint point : points) {
            chunks.add(toRetrievedChunk(point));
        }
        if (log.isDebugEnabled()) {
            log.debug("知识检索完成 topK={} filtered={} hits={}",
                    topK, StringUtils.hasText(subjectCode), chunks.size());
        }
        return chunks;
    }

    /* ============================== 入参/配置校验 ============================== */

    private static void requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为 null 或空白");
        }
    }

    private static void requireTopK(int topK) {
        if (topK < 1 || topK > MAX_TOP_K) {
            throw new IllegalArgumentException("topK 必须在 1-" + MAX_TOP_K + " 之间，实际=" + topK);
        }
    }

    /** 维度唯一来源：QdrantProperties.vectorSize，未配置/非法即失败（与入库侧同语义，不猜默认值）。 */
    private int requireVectorSize() {
        Integer size = qdrantProperties.getVectorSize();
        if (size == null || size < 1) {
            throw new IllegalStateException("qdrant.vector-size 未配置或非法，禁止检索（不得猜测向量维度）");
        }
        return size;
    }

    /* ============================== 结果严格校验与映射 ============================== */

    /** 单条结果的严格校验 → 映射；任一项不合法抛 {@link IllegalStateException}（整次失败）。 */
    private static RetrievedChunk toRetrievedChunk(ScoredPoint point) {
        if (point == null) {
            throw new IllegalStateException("检索结果包含 null 元素（违反内部契约）");
        }
        String pointId = requireCanonicalUuid(point.id());
        Map<String, Object> payload = point.payload();
        if (payload == null) {
            throw new IllegalStateException("检索结果 payload 为 null pointId=" + pointId);
        }
        String chunkId = requireNonBlankString(payload, "chunkId", pointId);
        String docId = requireNonBlankString(payload, "docId", pointId);
        KnowledgeDocType sourceType = requireSourceType(payload, pointId);
        Long sourceId = requirePositiveInteger(payload, "sourceId", pointId);
        String text = requireNonBlankString(payload, "text", pointId);
        String subjectCode = requireNonBlankString(payload, "subjectCode", pointId);
        return new RetrievedChunk(pointId, point.score(), chunkId, docId, sourceType, sourceId, text, subjectCode);
    }

    /** 规范 UUID：先解析，再与规范形式比较（忽略大小写），不单纯依赖 fromString 解析成功。 */
    private static String requireCanonicalUuid(String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalStateException("检索结果 point id 缺失或空白");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("检索结果 point id 非 UUID 格式 id=" + id, e);
        }
        if (!uuid.toString().equalsIgnoreCase(id)) {
            throw new IllegalStateException("检索结果 point id 非规范 UUID 形式 id=" + id);
        }
        return id;
    }

    private static String requireNonBlankString(Map<String, Object> payload, String key, String pointId) {
        Object value = payload.get(key);
        if (!(value instanceof String) || ((String) value).isBlank()) {
            throw new IllegalStateException(
                    "检索结果 payload." + key + " 缺失、类型非法或为空白 pointId=" + pointId);
        }
        return (String) value;
    }

    private static KnowledgeDocType requireSourceType(Map<String, Object> payload, String pointId) {
        String name = requireNonBlankString(payload, "sourceType", pointId);
        try {
            return KnowledgeDocType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "检索结果 payload.sourceType 未知枚举值 [" + name + "] pointId=" + pointId, e);
        }
    }

    /**
     * 正整数 JSON 数值：仅接受 Jackson Map 转换后的 {@link Integer}/{@link Long} 且 &gt; 0。
     * 小数（Double/BigDecimal）、字符串、布尔、null、零/负数、超 long 大整数（BigInteger）一律拒绝，
     * 不做截断或宽松转换。
     */
    private static Long requirePositiveInteger(Map<String, Object> payload, String key, String pointId) {
        Object value = payload.get(key);
        long number;
        if (value instanceof Integer) {
            number = ((Integer) value).longValue();
        } else if (value instanceof Long) {
            number = (Long) value;
        } else {
            throw new IllegalStateException("检索结果 payload." + key
                    + " 必须是整数 JSON 数值（拒绝小数/字符串/布尔/null/超范围整数） pointId=" + pointId);
        }
        if (number <= 0) {
            throw new IllegalStateException("检索结果 payload." + key + " 必须为正整数 pointId=" + pointId);
        }
        return number;
    }
}
