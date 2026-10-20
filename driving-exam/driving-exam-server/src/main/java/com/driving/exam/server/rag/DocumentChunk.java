package com.driving.exam.server.rag;

import java.util.Map;

/**
 * 知识文档的一个分块（RAG 第一层的分块产物）。
 *
 * <p>每个分块保留对原始文档的可追溯信息：{@code docId / sourceType / sourceId / chunkIndex}，
 * 便于后续“向量 → Qdrant → 检索命中 → 还原真实来源 → {@code references_json}”全链路回溯。</p>
 *
 * @param chunkId    分块稳定标识，形如 {@code <docId>#<chunkIndex>}
 * @param docId      所属文档 ID
 * @param sourceType 来源类型
 * @param sourceId   原始记录主键
 * @param chunkIndex 该文档内的分块序号（从 0 开始）
 * @param text       分块文本
 * @param metadata   来源元数据（继承自 {@link KnowledgeDocument#metadata()}）
 */
public record DocumentChunk(
        String chunkId,
        String docId,
        KnowledgeDocType sourceType,
        Long sourceId,
        int chunkIndex,
        String text,
        Map<String, Object> metadata) {
}
