package com.driving.exam.server.rag;

import java.util.Map;

/**
 * 统一知识文档（RAG 第一层的标准化载体）。
 *
 * <p>把来自不同表/字段的原始内容（题库解析、知识点描述）归一成同一结构，
 * 供后续“分块 → Embedding → 写入 Qdrant”使用。<b>本记录只承载真实存在的数据</b>，
 * 不虚构知识点关联、法规编号或来源信息。</p>
 *
 * @param docId       稳定且跨类型唯一的文档 ID，形如 {@code <来源类型>:<原始ID>}
 *                    （例：{@code QUESTION_ANALYSIS:12}、{@code KNOWLEDGE_POINT:3}），
 *                    保证题目 ID 与知识点 ID 重号时也不冲突
 * @param sourceType  来源类型
 * @param sourceId    原始数据记录的真实主键（{@code question.id} 或 {@code knowledge_point.id}）
 * @param subjectCode 科目代码（来自原始记录，如 {@code SUBJECT_1}）
 * @param title       可读标识（题目用题干，知识点用名称；均为真实字段）
 * @param text        用于向量化的实际文本（题目=解析，知识点=描述）；可能为空串
 * @param metadata    来源元数据（仅放真实字段，供检索命中后还原来源与引用）
 */
public record KnowledgeDocument(
        String docId,
        KnowledgeDocType sourceType,
        Long sourceId,
        String subjectCode,
        String title,
        String text,
        Map<String, Object> metadata) {
}
