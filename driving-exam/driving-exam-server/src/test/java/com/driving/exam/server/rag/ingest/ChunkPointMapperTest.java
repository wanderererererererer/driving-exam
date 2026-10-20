package com.driving.exam.server.rag.ingest;

import com.driving.exam.server.rag.DocumentChunk;
import com.driving.exam.server.rag.KnowledgeDocType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ChunkPointMapper} 单元测试（纯逻辑，无 Spring / 无数据库 / 无外部服务）。
 */
@DisplayName("R2.2 DocumentChunk → 向量点映射")
class ChunkPointMapperTest {

    private final ChunkPointMapper mapper = new ChunkPointMapper();

    /** 构造一个合法分块（chunkId/docId 与 R1 分块器规则一致：docId + "#" + chunkIndex）。 */
    private static DocumentChunk chunk(KnowledgeDocType type, long sourceId, int idx,
                                       String text, Map<String, Object> meta) {
        String docId = type.name() + ":" + sourceId;
        return new DocumentChunk(docId + "#" + idx, docId, type, sourceId, idx, text, meta);
    }

    /* ==================== 1. Embedding 输入 ==================== */

    @Test
    @DisplayName("Embedding 输入 == chunk.text()（原样，不裁剪不改写）")
    void embeddingInputEqualsChunkText() {
        String text = "  红灯表示禁止通行，应停车等待。  "; // 前后空白也必须原样保留
        DocumentChunk c = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 12L, 0, text, Map.of("a", 1));

        assertThat(mapper.toEmbeddingInput(c)).isEqualTo(text);
    }

    /* ==================== 2. point ID 稳定性 ==================== */

    @Test
    @DisplayName("相同身份重复映射 → point ID 相同")
    void stablePointId() {
        DocumentChunk c1 = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 7L, 2, "文本", Map.of());
        DocumentChunk c2 = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 7L, 2, "文本", Map.of());

        assertThat(mapper.toPoint(c1, "SUBJECT_1").pointId())
                .isEqualTo(mapper.toPoint(c2, "SUBJECT_1").pointId());
    }

    @Test
    @DisplayName("不同来源 / 不同分块 / 不同来源类型 → point ID 不同")
    void differentIdentityDifferentId() {
        String a = mapper.toPoint(chunk(KnowledgeDocType.QUESTION_ANALYSIS, 7L, 0, "t", Map.of()), "SUBJECT_1").pointId();
        String diffSource = mapper.toPoint(chunk(KnowledgeDocType.QUESTION_ANALYSIS, 8L, 0, "t", Map.of()), "SUBJECT_1").pointId();
        String diffChunk = mapper.toPoint(chunk(KnowledgeDocType.QUESTION_ANALYSIS, 7L, 1, "t", Map.of()), "SUBJECT_1").pointId();
        String diffType = mapper.toPoint(chunk(KnowledgeDocType.KNOWLEDGE_POINT, 7L, 0, "t", Map.of()), "SUBJECT_1").pointId();

        assertThat(a).isNotEqualTo(diffSource);
        assertThat(a).isNotEqualTo(diffChunk);
        assertThat(a).isNotEqualTo(diffType);
    }

    @Test
    @DisplayName("身份编码无歧义：id=1,idx=23 与 id=12,idx=3 不产生相同 point ID（防裸拼接碰撞）")
    void separatorPreventsCollision() {
        DocumentChunk a = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 1L, 23, "t", Map.of());
        DocumentChunk b = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 12L, 3, "t", Map.of());

        assertThat(mapper.toPoint(a, "SUBJECT_1").pointId())
                .isNotEqualTo(mapper.toPoint(b, "SUBJECT_1").pointId());
    }

    /* ==================== 3. payload 字段 ==================== */

    @Test
    @DisplayName("payload 各字段映射正确，结构与类型符合源字段")
    void payloadFields() {
        Map<String, Object> meta = Map.of("knowledgePointId", 3L, "questionType", "SINGLE");
        DocumentChunk c = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 12L, 1, "解析文本", meta);

        ChunkPoint point = mapper.toPoint(c, "SUBJECT_1");

        assertThat(point.pointId()).isNotBlank();
        assertThat(point.payload())
                .containsEntry("chunkId", "QUESTION_ANALYSIS:12#1")
                .containsEntry("docId", "QUESTION_ANALYSIS:12")
                .containsEntry("sourceType", "QUESTION_ANALYSIS")
                .containsEntry("sourceId", 12L)
                .containsEntry("text", "解析文本")
                .containsEntry("subjectCode", "SUBJECT_1");
        // metadata 原样保留（同一映射内容，不丢失、不被压成字符串）
        assertThat(point.payload()).containsEntry("metadata", meta);
    }

    /* ==================== 4. subjectCode ==================== */

    @Test
    @DisplayName("subjectCode 来自显式入参且原样使用（不做大小写/语义修改）")
    void subjectCodeUsedAsIs() {
        DocumentChunk c = chunk(KnowledgeDocType.KNOWLEDGE_POINT, 3L, 0, "描述", Map.of());
        assertThat(mapper.toPoint(c, "SUBJECT_4").payload()).containsEntry("subjectCode", "SUBJECT_4");
    }

    @Test
    @DisplayName("subjectCode 为 null/空/纯空白 → 抛 IllegalArgumentException")
    void subjectCodeRequired() {
        DocumentChunk c = chunk(KnowledgeDocType.KNOWLEDGE_POINT, 3L, 0, "描述", Map.of());
        assertThatThrownBy(() -> mapper.toPoint(c, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toPoint(c, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toPoint(c, "   ")).isInstanceOf(IllegalArgumentException.class);
    }

    /* ==================== 5. metadata ==================== */

    @Test
    @DisplayName("metadata 现有键值完整保留")
    void metadataPreserved() {
        Map<String, Object> meta = Map.of("parentId", 1L, "level", 2);
        DocumentChunk c = chunk(KnowledgeDocType.KNOWLEDGE_POINT, 3L, 0, "描述", meta);

        Object payloadMeta = mapper.toPoint(c, "SUBJECT_1").payload().get("metadata");
        assertThat(payloadMeta).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> asMap = (Map<String, Object>) payloadMeta;
        assertThat(asMap).containsEntry("parentId", 1L).containsEntry("level", 2);
    }

    @Test
    @DisplayName("metadata 为 null → payload 的 metadata 为空 Map（防御性约定，不丢已有键）")
    void metadataNullBecomesEmptyMap() {
        DocumentChunk c = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 5L, 0, "解析", null);

        Object payloadMeta = mapper.toPoint(c, "SUBJECT_1").payload().get("metadata");
        assertThat(payloadMeta).isInstanceOf(Map.class);
        assertThat((Map<?, ?>) payloadMeta).isEmpty();
    }

    /* ==================== 6. 输入非法 ==================== */

    @Test
    @DisplayName("chunk 为 null → 抛 IllegalArgumentException")
    void nullChunkRejected() {
        assertThatThrownBy(() -> mapper.toEmbeddingInput(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toPoint(null, "SUBJECT_1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("身份字段缺失/非法（sourceType/sourceId/chunkIndex/chunkId/docId）→ 抛 IllegalArgumentException")
    void invalidIdentityRejected() {
        // sourceType 缺失
        assertThatThrownBy(() -> mapper.toPoint(
                new DocumentChunk("c#0", "c", null, 1L, 0, "t", Map.of()), "SUBJECT_1"))
                .isInstanceOf(IllegalArgumentException.class);
        // sourceId 缺失
        assertThatThrownBy(() -> mapper.toPoint(
                new DocumentChunk("c#0", "c", KnowledgeDocType.QUESTION_ANALYSIS, null, 0, "t", Map.of()), "SUBJECT_1"))
                .isInstanceOf(IllegalArgumentException.class);
        // 负数 chunkIndex
        assertThatThrownBy(() -> mapper.toPoint(
                new DocumentChunk("c#-1", "c", KnowledgeDocType.QUESTION_ANALYSIS, 1L, -1, "t", Map.of()), "SUBJECT_1"))
                .isInstanceOf(IllegalArgumentException.class);
        // chunkId 缺失
        assertThatThrownBy(() -> mapper.toPoint(
                new DocumentChunk(null, "c", KnowledgeDocType.QUESTION_ANALYSIS, 1L, 0, "t", Map.of()), "SUBJECT_1"))
                .isInstanceOf(IllegalArgumentException.class);
        // docId 缺失
        assertThatThrownBy(() -> mapper.toPoint(
                new DocumentChunk("c#0", null, KnowledgeDocType.QUESTION_ANALYSIS, 1L, 0, "t", Map.of()), "SUBJECT_1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("文本为 null/空/纯空白 → toEmbeddingInput 与 toPoint 均抛 IllegalArgumentException")
    void blankTextRejected() {
        DocumentChunk blank = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 9L, 0, "   ", Map.of());
        assertThatThrownBy(() -> mapper.toEmbeddingInput(blank)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toPoint(blank, "SUBJECT_1")).isInstanceOf(IllegalArgumentException.class);

        DocumentChunk empty = chunk(KnowledgeDocType.QUESTION_ANALYSIS, 9L, 0, "", Map.of());
        assertThatThrownBy(() -> mapper.toEmbeddingInput(empty)).isInstanceOf(IllegalArgumentException.class);
    }
}
