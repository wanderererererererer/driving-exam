package com.driving.exam.server.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocumentChunker} 单元测试（纯逻辑，无 Spring / 无数据库 / 无外部服务）。
 */
@DisplayName("RAG 基础分块")
class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    private static KnowledgeDocument doc(String docId, KnowledgeDocType type, Long sourceId, String text) {
        return new KnowledgeDocument(docId, type, sourceId, "SUBJECT_1", "标题", text, Map.of("k", "v"));
    }

    /* ===================== 空内容 ===================== */

    @Test
    @DisplayName("null / 空白文本 → 空分块列表")
    void blankText_yieldsNoChunks() {
        assertThat(chunker.chunk(doc("QUESTION_ANALYSIS:1", KnowledgeDocType.QUESTION_ANALYSIS, 1L, null))).isEmpty();
        assertThat(chunker.chunk(doc("QUESTION_ANALYSIS:1", KnowledgeDocType.QUESTION_ANALYSIS, 1L, ""))).isEmpty();
        assertThat(chunker.chunk(doc("QUESTION_ANALYSIS:1", KnowledgeDocType.QUESTION_ANALYSIS, 1L, "   \n\t "))).isEmpty();
    }

    /* ===================== 短文本：单块 ===================== */

    @Test
    @DisplayName("短文本 → 单块，文本不变、chunkId=docId#0、来源信息保留")
    void shortText_singleChunk() {
        String text = "红灯表示禁止通行，应停车等待。";
        KnowledgeDocument d = doc("KNOWLEDGE_POINT:3", KnowledgeDocType.KNOWLEDGE_POINT, 3L, text);

        List<DocumentChunk> chunks = chunker.chunk(d);

        assertThat(chunks).hasSize(1);
        DocumentChunk c = chunks.get(0);
        assertThat(c.text()).isEqualTo(text);
        assertThat(c.chunkId()).isEqualTo("KNOWLEDGE_POINT:3#0");
        assertThat(c.docId()).isEqualTo("KNOWLEDGE_POINT:3");
        assertThat(c.sourceType()).isEqualTo(KnowledgeDocType.KNOWLEDGE_POINT);
        assertThat(c.sourceId()).isEqualTo(3L);
        assertThat(c.chunkIndex()).isZero();
        assertThat(c.metadata()).containsEntry("k", "v");
    }

    /* ===================== 超长文本：多块且不丢内容 ===================== */

    @Test
    @DisplayName("超长文本 → 多块，每块≤上限，且各块顺序拼接 == 原文（无丢失）")
    void longText_multiChunks_noContentLoss() {
        // 构造明显超过上限的确定性文本（带句子边界）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("这是第").append(i).append("句用于测试分块的文本。");
        }
        String text = sb.toString();
        assertThat(text.length()).isGreaterThan(DocumentChunker.MAX_CHUNK_CHARS);

        KnowledgeDocument d = doc("QUESTION_ANALYSIS:7", KnowledgeDocType.QUESTION_ANALYSIS, 7L, text);
        List<DocumentChunk> chunks = chunker.chunk(d);

        assertThat(chunks.size()).isGreaterThan(1);

        // 序号连续、chunkId 稳定、每块不超过字符上限
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk c = chunks.get(i);
            assertThat(c.chunkIndex()).isEqualTo(i);
            assertThat(c.chunkId()).isEqualTo("QUESTION_ANALYSIS:7#" + i);
            assertThat(c.text().length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHUNK_CHARS);
            assertThat(c.text()).isNotEmpty();
            joined.append(c.text());
        }
        // 关键：拼接后与原文完全一致 → 没有静默丢失任何字符
        assertThat(joined.toString()).isEqualTo(text);
    }

    @Test
    @DisplayName("超长无标点文本 → 仍可按上限硬切，不丢内容")
    void longText_noBoundaries_stillNoLoss() {
        String text = "甲".repeat(DocumentChunker.MAX_CHUNK_CHARS * 2 + 5);
        KnowledgeDocument d = doc("QUESTION_ANALYSIS:8", KnowledgeDocType.QUESTION_ANALYSIS, 8L, text);

        List<DocumentChunk> chunks = chunker.chunk(d);

        assertThat(chunks).hasSize(3);
        StringBuilder joined = new StringBuilder();
        for (DocumentChunk c : chunks) {
            assertThat(c.text().length()).isLessThanOrEqualTo(DocumentChunker.MAX_CHUNK_CHARS);
            joined.append(c.text());
        }
        assertThat(joined.toString()).isEqualTo(text);
    }

    /* ===================== 跨来源区分 ===================== */

    @Test
    @DisplayName("同一数字 ID、不同来源类型的 chunkId 不冲突")
    void chunkIdDistinguishesSourceType() {
        String text = "同一段文本。";

        DocumentChunk qc = chunker.chunk(doc("QUESTION_ANALYSIS:5", KnowledgeDocType.QUESTION_ANALYSIS, 5L, text)).get(0);
        DocumentChunk kc = chunker.chunk(doc("KNOWLEDGE_POINT:5", KnowledgeDocType.KNOWLEDGE_POINT, 5L, text)).get(0);

        assertThat(qc.chunkId()).isEqualTo("QUESTION_ANALYSIS:5#0");
        assertThat(kc.chunkId()).isEqualTo("KNOWLEDGE_POINT:5#0");
        assertThat(qc.chunkId()).isNotEqualTo(kc.chunkId());
    }
}
