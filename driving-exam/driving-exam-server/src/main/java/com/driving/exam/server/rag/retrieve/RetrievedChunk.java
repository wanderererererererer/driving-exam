package com.driving.exam.server.rag.retrieve;

import com.driving.exam.server.rag.KnowledgeDocType;

/**
 * 一次知识检索命中的<b>分块结果</b>（检索层载体，R3）。
 *
 * <p>由 {@link KnowledgeRetriever} 从 Qdrant Query API 的 {@code ScoredPoint} 映射而来；
 * 所有字段均经过严格校验后才填充：point ID 为规范 UUID、payload 六字段存在且类型正确、
 * {@code sourceId} 为正整数。任一条不合法时整次检索失败，不会产生"部分合法"的结果。</p>
 *
 * <p><b>不含</b> {@code metadata} 与 {@code version}（既定决策：首版不需要）；
 * 后续写入 {@code references_json} 时如需更多字段再另行扩展。</p>
 *
 * @param pointId     Qdrant 点 ID（规范 UUID 字符串；写入侧由 ChunkPointMapper 确定性生成）
 * @param score       相似度分数（由 Qdrant 计算并按其排序返回；本层不重排）
 * @param chunkId     分块 ID（{@code docId#chunkIndex}）
 * @param docId       知识文档 ID（{@code 来源类型:原始主键}）
 * @param sourceType  来源类型（题库解析 / 知识点描述）
 * @param sourceId    原始业务主键（正整数，对应 question.id 或 knowledge_point.id）
 * @param text        分块正文（非空白；供后续上下文组装）
 * @param subjectCode 科目编码（非空白；payload 原值）
 */
public record RetrievedChunk(
        String pointId,
        double score,
        String chunkId,
        String docId,
        KnowledgeDocType sourceType,
        Long sourceId,
        String text,
        String subjectCode) {
}
