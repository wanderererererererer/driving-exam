package com.driving.exam.server.rag;

/**
 * 知识文档来源类型。
 *
 * <p>用于区分“统一知识文档”来自哪类原始数据，并作为 {@code docId} 前缀、
 * 后续 Qdrant payload 的 {@code sourceType} 字段，保证不同来源的 ID 不会互相冲突。</p>
 *
 * <ul>
 *   <li>{@link #QUESTION_ANALYSIS} —— 题库解析（数据源 {@code question.analysis}）；</li>
 *   <li>{@link #KNOWLEDGE_POINT} —— 知识点描述（数据源 {@code knowledge_point.description}）。</li>
 * </ul>
 */
public enum KnowledgeDocType {

    /** 题库解析：来源 {@code question.analysis}。 */
    QUESTION_ANALYSIS,

    /** 知识点描述：来源 {@code knowledge_point.description}。 */
    KNOWLEDGE_POINT
}
