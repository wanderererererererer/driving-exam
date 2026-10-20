package com.driving.exam.server.rag.ingest;

import java.util.Map;

/**
 * 一个待写入向量库的“向量点”：稳定 point ID + 结构化 payload。
 *
 * <p>本 record 只承载数据，不含任何 Qdrant 客户端/网络逻辑；由 {@link ChunkPointMapper} 生成，
 * 供后续（R2.3+）提交给 Qdrant。{@code payload} 为有序不可变 Map，保持结构化字段（不压成字符串）。</p>
 *
 * @param pointId 确定性 UUID 字符串（同一逻辑分块恒等）
 * @param payload 结构化负载（chunkId/docId/sourceType/sourceId/text/subjectCode/metadata）
 */
public record ChunkPoint(String pointId, Map<String, Object> payload) {
}
