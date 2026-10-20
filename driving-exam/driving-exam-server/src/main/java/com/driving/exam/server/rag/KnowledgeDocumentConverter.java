package com.driving.exam.server.rag;

import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 原始实体 → 统一知识文档 的转换器（RAG 第一层）。
 *
 * <p>只使用<b>真实存在的字段</b>：</p>
 * <ul>
 *   <li>题目：{@code id / subjectCode / knowledgePointId / questionType / content / answer / analysis}；</li>
 *   <li>知识点：{@code id / subjectCode / parentId / name / level / description}。</li>
 * </ul>
 * <p><b>不虚构</b>知识点关联、法规编号或来源信息；缺失字段保持真实（如 {@code analysis} 为空则为空串）。</p>
 *
 * <p>该类为无状态纯逻辑（参照 {@code AnswerGrader} 的做法声明为 {@code @Component}），
 * 单测可直接 {@code new KnowledgeDocumentConverter()}，无需 Spring 容器 / 数据库。</p>
 */
@Component
public class KnowledgeDocumentConverter {

    /**
     * 题目（题库解析）→ 知识文档。文本取 {@code question.analysis}（解析）。
     *
     * @throws NullPointerException {@code question} 为 null
     */
    public KnowledgeDocument fromQuestion(Question question) {
        Objects.requireNonNull(question, "question 不能为空");
        Long id = question.getId();
        String docId = KnowledgeDocType.QUESTION_ANALYSIS.name() + ":" + id;
        // 标题用题干（真实字段）；题干缺失时退化为可读标识
        String title = (question.getContent() == null || question.getContent().isBlank())
                ? ("题库解析#" + id)
                : question.getContent();
        String text = question.getAnalysis() == null ? "" : question.getAnalysis();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledgePointId", question.getKnowledgePointId());
        metadata.put("questionType", question.getQuestionType());
        metadata.put("answer", question.getAnswer());

        return new KnowledgeDocument(
                docId,
                KnowledgeDocType.QUESTION_ANALYSIS,
                id,
                question.getSubjectCode(),
                title,
                text,
                Collections.unmodifiableMap(metadata));
    }

    /**
     * 知识点 → 知识文档。文本取 {@code knowledge_point.description}（描述）。
     *
     * @throws NullPointerException {@code knowledgePoint} 为 null
     */
    public KnowledgeDocument fromKnowledgePoint(KnowledgePoint knowledgePoint) {
        Objects.requireNonNull(knowledgePoint, "knowledgePoint 不能为空");
        Long id = knowledgePoint.getId();
        String docId = KnowledgeDocType.KNOWLEDGE_POINT.name() + ":" + id;
        String title = (knowledgePoint.getName() == null || knowledgePoint.getName().isBlank())
                ? ("知识点#" + id)
                : knowledgePoint.getName();
        String text = knowledgePoint.getDescription() == null ? "" : knowledgePoint.getDescription();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("parentId", knowledgePoint.getParentId());
        metadata.put("level", knowledgePoint.getLevel());

        return new KnowledgeDocument(
                docId,
                KnowledgeDocType.KNOWLEDGE_POINT,
                id,
                knowledgePoint.getSubjectCode(),
                title,
                text,
                Collections.unmodifiableMap(metadata));
    }
}
