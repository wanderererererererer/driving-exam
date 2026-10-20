package com.driving.exam.server.rag;

import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KnowledgeDocumentConverter} 单元测试（纯逻辑，无 Spring / 无数据库 / 无外部服务）。
 */
@DisplayName("RAG 知识文档转换")
class KnowledgeDocumentConverterTest {

    private final KnowledgeDocumentConverter converter = new KnowledgeDocumentConverter();

    /* ===================== 题库解析 ===================== */

    @Test
    @DisplayName("题目 → 文档：来源类型/真实ID/科目/文本/元数据均正确")
    void fromQuestion() {
        Question q = new Question();
        q.setId(12L);
        q.setSubjectCode("SUBJECT_1");
        q.setKnowledgePointId(3L);
        q.setQuestionType("SINGLE");
        q.setContent("遇到红灯应当停车等待。");
        q.setAnswer("A");
        q.setAnalysis("红灯表示禁止通行，应停车等待。");

        KnowledgeDocument doc = converter.fromQuestion(q);

        assertThat(doc.docId()).isEqualTo("QUESTION_ANALYSIS:12");
        assertThat(doc.sourceType()).isEqualTo(KnowledgeDocType.QUESTION_ANALYSIS);
        assertThat(doc.sourceId()).isEqualTo(12L);
        assertThat(doc.subjectCode()).isEqualTo("SUBJECT_1");
        assertThat(doc.title()).isEqualTo("遇到红灯应当停车等待。");
        assertThat(doc.text()).isEqualTo("红灯表示禁止通行，应停车等待。");
        assertThat(doc.metadata()).containsEntry("knowledgePointId", 3L)
                .containsEntry("questionType", "SINGLE")
                .containsEntry("answer", "A");
    }

    @Test
    @DisplayName("题目解析为空 → 文本为空串（不编造内容）")
    void fromQuestion_withNullAnalysis() {
        Question q = new Question();
        q.setId(99L);
        q.setSubjectCode("SUBJECT_4");
        q.setContent("题干");
        q.setAnalysis(null);

        KnowledgeDocument doc = converter.fromQuestion(q);

        assertThat(doc.text()).isEmpty();
        assertThat(doc.docId()).isEqualTo("QUESTION_ANALYSIS:99");
    }

    /* ===================== 知识点 ===================== */

    @Test
    @DisplayName("知识点 → 文档：来源类型/真实ID/名称/描述/层级元数据正确")
    void fromKnowledgePoint() {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(3L);
        kp.setSubjectCode("SUBJECT_1");
        kp.setParentId(1L);
        kp.setName("交通标志标线");
        kp.setLevel(2);
        kp.setDescription("警告、禁令、指示标志与标线");

        KnowledgeDocument doc = converter.fromKnowledgePoint(kp);

        assertThat(doc.docId()).isEqualTo("KNOWLEDGE_POINT:3");
        assertThat(doc.sourceType()).isEqualTo(KnowledgeDocType.KNOWLEDGE_POINT);
        assertThat(doc.sourceId()).isEqualTo(3L);
        assertThat(doc.subjectCode()).isEqualTo("SUBJECT_1");
        assertThat(doc.title()).isEqualTo("交通标志标线");
        assertThat(doc.text()).isEqualTo("警告、禁令、指示标志与标线");
        assertThat(doc.metadata()).containsEntry("parentId", 1L).containsEntry("level", 2);
    }

    /* ===================== 跨来源区分 ===================== */

    @Test
    @DisplayName("题目 ID 与知识点 ID 同为 3 时，docId 不冲突（来源前缀区分）")
    void docIdDistinguishesSourceType() {
        Question q = new Question();
        q.setId(3L);
        q.setSubjectCode("SUBJECT_1");
        q.setContent("题干");
        q.setAnalysis("解析");

        KnowledgePoint kp = new KnowledgePoint();
        kp.setId(3L);
        kp.setSubjectCode("SUBJECT_1");
        kp.setName("知识点");
        kp.setDescription("描述");

        assertThat(converter.fromQuestion(q).docId())
                .isNotEqualTo(converter.fromKnowledgePoint(kp).docId());
    }
}
