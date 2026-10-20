package com.driving.exam.server.rag.ingest;

import com.driving.exam.server.rag.DocumentChunk;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link DocumentChunk} → 向量点 的纯逻辑映射器（R2.2）。
 *
 * <h3>职责（仅数据映射，无副作用）</h3>
 * <ul>
 *   <li>{@link #toEmbeddingInput}：把分块文本原样交给 Embedding（不重写/不裁剪/不拼接）；</li>
 *   <li>{@link #toPoint}：生成<b>确定性 point ID</b> 与<b>结构化 payload</b>。</li>
 * </ul>
 * <p>不调用 Embedding API、不连接 Qdrant、不访问数据库；不负责旧分块清理（部分更新/删除的清理策略另行设计）。</p>
 *
 * <h3>关于 subjectCode（依据仓库事实）</h3>
 * <p>{@code DocumentChunk} <b>没有</b> {@code subjectCode} 字段，其 {@code metadata} 也<b>不含</b>该键
 * （{@code KnowledgeDocumentConverter} 只写 {@code knowledgePointId/questionType/answer} 或 {@code parentId/level}；
 * {@code DocumentChunker} 仅透传 {@code document.metadata()}）。因此本 mapper <b>显式接收</b>
 * {@code subjectCode} 参数——<b>上游必须传入真实的 {@code KnowledgeDocument.subjectCode()}</b>，本类不做任何猜测。</p>
 *
 * <h3>Point ID 算法（确定性、无歧义）</h3>
 * <p>{@code UUID.nameUUIDFromBytes( (sourceType.name() + '\u001F' + sourceId + '\u001F' + chunkIndex).getBytes(UTF_8) )}。
 * 用单位分隔符 {@code \u001F} 明确分隔三个身份字段（枚举名与数值均不可能包含该字符），
 * 避免“裸拼接”导致的身份歧义（如 id=1,idx=23 与 id=12,idx=3）。<b>不使用随机 UUID。</b></p>
 *
 * <p>无状态纯逻辑，单测可直接 {@code new ChunkPointMapper()}。</p>
 */
@Component
public class ChunkPointMapper {

    /** 身份字段分隔符（Unit Separator，控制字符，不会出现在枚举名/数值中）。 */
    private static final char ID_SEPARATOR = '\u001F';

    /**
     * 取分块的 Embedding 输入文本：原样返回 {@code chunk.text()}（不做 trim/改写/拼接）。
     *
     * @throws IllegalArgumentException chunk 为 null 或文本为空/纯空白（分块器不会产出空文本分块，此为防御性契约）
     */
    public String toEmbeddingInput(DocumentChunk chunk) {
        requireChunk(chunk);
        requireNonBlankText(chunk);
        return chunk.text();
    }

    /**
     * 生成向量点：确定性 point ID + 结构化 payload。
     *
     * @param chunk       分块（非 null，且关键身份字段合法）
     * @param subjectCode 学科编码，必须为<b>真实</b>值（上游取 {@code KnowledgeDocument.subjectCode()}）；
     *                    null/空/纯空白均非法
     * @return 向量点
     * @throws IllegalArgumentException 输入非法（null chunk、身份字段缺失/非法、文本空、subjectCode 空）
     */
    public ChunkPoint toPoint(DocumentChunk chunk, String subjectCode) {
        requireChunk(chunk);
        requireIdentity(chunk);
        requireNonBlankText(chunk);
        if (subjectCode == null || subjectCode.isBlank()) {
            throw new IllegalArgumentException("subjectCode 不能为空（上游必须传入真实的 KnowledgeDocument.subjectCode）");
        }

        String pointId = pointIdFor(chunk);

        // metadata 契约：转换器恒产出非 null 的不可变 Map；此处对 null 做防御性处理（空 Map），不丢已有键值
        Map<String, Object> metadata = chunk.metadata() == null ? Collections.emptyMap() : chunk.metadata();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", chunk.chunkId());
        payload.put("docId", chunk.docId());
        payload.put("sourceType", chunk.sourceType().name());
        payload.put("sourceId", chunk.sourceId());
        payload.put("text", chunk.text());
        payload.put("subjectCode", subjectCode);
        payload.put("metadata", metadata);

        return new ChunkPoint(pointId, Collections.unmodifiableMap(payload));
    }

    /* ============================== 内部实现 ============================== */

    /** 确定性 UUID：基于 sourceType / sourceId / chunkIndex（无歧义分隔）。 */
    private static String pointIdFor(DocumentChunk chunk) {
        String canonical = chunk.sourceType().name()
                + ID_SEPARATOR + chunk.sourceId()
                + ID_SEPARATOR + chunk.chunkIndex();
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void requireChunk(DocumentChunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk 不能为空");
        }
    }

    private static void requireNonBlankText(DocumentChunk chunk) {
        if (chunk.text() == null || chunk.text().isBlank()) {
            throw new IllegalArgumentException("chunk.text 不能为空或纯空白");
        }
    }

    private static void requireIdentity(DocumentChunk chunk) {
        if (chunk.sourceType() == null) {
            throw new IllegalArgumentException("chunk.sourceType 不能为空（无法生成稳定 point ID）");
        }
        if (chunk.sourceId() == null) {
            throw new IllegalArgumentException("chunk.sourceId 不能为空（无法生成稳定 point ID）");
        }
        if (chunk.chunkIndex() < 0) {
            throw new IllegalArgumentException("chunk.chunkIndex 不能为负数");
        }
        if (chunk.chunkId() == null || chunk.chunkId().isBlank()) {
            throw new IllegalArgumentException("chunk.chunkId 不能为空");
        }
        if (chunk.docId() == null || chunk.docId().isBlank()) {
            throw new IllegalArgumentException("chunk.docId 不能为空");
        }
    }
}
