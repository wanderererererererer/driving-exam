package com.driving.exam.server.rag.ingest;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.server.integration.embedding.EmbeddingClient;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.integration.qdrant.VectorPoint;
import com.driving.exam.server.rag.DocumentChunker;
import com.driving.exam.server.rag.KnowledgeDocType;
import com.driving.exam.server.rag.KnowledgeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeIngestService} 离线单元测试。
 *
 * <p>真实纯逻辑组件（{@link DocumentChunker}/{@link ChunkPointMapper}/{@link VectorConverter}）+
 * Mockito 模拟 {@link EmbeddingClient}/{@link QdrantClient}（<b>不触网、不连数据库、不访问 Qdrant</b>）。</p>
 */
@DisplayName("R2.4 知识入库编排（离线）")
class KnowledgeIngestServiceTest {

    private static final int DIM = 2;

    private DocumentChunker chunker;
    private ChunkPointMapper chunkPointMapper;
    private VectorConverter vectorConverter;
    private EmbeddingClient embedding;
    private QdrantClient qdrant;
    private QdrantProperties properties;
    private KnowledgeIngestService service;

    @BeforeEach
    void setUp() {
        chunker = new DocumentChunker();
        chunkPointMapper = new ChunkPointMapper();
        vectorConverter = new VectorConverter();
        embedding = mock(EmbeddingClient.class);
        qdrant = mock(QdrantClient.class);
        properties = new QdrantProperties();
        properties.setVectorSize(DIM);
        service = new KnowledgeIngestService(chunker, chunkPointMapper, vectorConverter,
                embedding, qdrant, properties);
    }

    /* ==================== 1. 正常单批次 ==================== */

    @Test
    @DisplayName("正常单批次：1 文档 1 分块 → 1 批，成功结果，Embedding 与 upsert 各 1 次")
    void normalSingleBatch() throws Exception {
        when(embedding.embed(anyList())).thenAnswer(dimVectors());
        doAnswer(collectUpserts(new ArrayList<>())).when(qdrant).upsertPoints(anyList());

        IngestResult r = service.ingest(List.of(doc("d1", 1L, "SUBJECT_1", "红灯表示禁止通行，应停车等待。")));

        assertThat(r.documentCount()).isEqualTo(1);
        assertThat(r.chunkCount()).isEqualTo(1);
        assertThat(r.batchCount()).isEqualTo(1);
        assertThat(r.embeddedVectorCount()).isEqualTo(1);
        assertThat(r.writtenPointCount()).isEqualTo(1);
        verify(embedding, times(1)).embed(anyList());
        verify(qdrant, times(1)).upsertPoints(anyList());
    }

    /* ==================== 2. 超过 32 个分块正确分批 ==================== */

    @Test
    @DisplayName(">32 分块：按 32 分批（[32,1]），每批 ≤32")
    void batchesOver32() throws Exception {
        List<Integer> embedBatchSizes = new ArrayList<>();
        when(embedding.embed(anyList())).thenAnswer(recordingVectors(embedBatchSizes));
        List<Integer> upsertBatchSizes = new ArrayList<>();
        doAnswer(recordingUpserts(upsertBatchSizes)).when(qdrant).upsertPoints(anyList());

        String text = "甲".repeat(DocumentChunker.MAX_CHUNK_CHARS * 33); // 33 个块
        IngestResult r = service.ingest(List.of(doc("d1", 1L, "SUBJECT_1", text)));

        assertThat(r.chunkCount()).isEqualTo(33);
        assertThat(r.batchCount()).isEqualTo(2);
        assertThat(embedBatchSizes).containsExactly(32, 1);
        assertThat(upsertBatchSizes).containsExactly(32, 1);
        assertThat(embedBatchSizes).allMatch(s -> s <= EmbeddingClient.MAX_BATCH_SIZE);
    }

    /* ==================== 3. 空文档 → 零结果、无外部调用 ==================== */

    @Test
    @DisplayName("空文本文档 → 零结果，且不调用 Embedding/Qdrant")
    void emptyDocs_zeroResult_noExternals() {
        IngestResult r = service.ingest(List.of(doc("d1", 1L, "SUBJECT_1", "   \n\t ")));

        assertThat(r.documentCount()).isEqualTo(1);
        assertThat(r.chunkCount()).isZero();
        assertThat(r.batchCount()).isZero();
        assertThat(r.embeddedVectorCount()).isZero();
        assertThat(r.writtenPointCount()).isZero();
        verifyNoInteractions(embedding, qdrant);
    }

    /* ==================== 4-8. 前置校验（外部调用前失败） ==================== */

    @Test
    @DisplayName("null 输入列表 → IllegalArgumentException，无外部调用")
    void nullListRejected() {
        assertThatThrownBy(() -> service.ingest(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(embedding, qdrant);
    }

    @Test
    @DisplayName("列表含 null 文档 → IllegalArgumentException，无外部调用")
    void nullDocRejected() {
        assertThatThrownBy(() -> service.ingest(
                Arrays.asList(doc("d1", 1L, "SUBJECT_1", "文本"), null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(embedding, qdrant);
    }

    @Test
    @DisplayName("空白 subjectCode → 外部调用前被拒绝")
    void blankSubjectRejected() {
        assertThatThrownBy(() -> service.ingest(List.of(doc("d1", 1L, "   ", "文本"))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(embedding, qdrant);
    }

    @Test
    @DisplayName("重复 docId → 外部调用前被拒绝（不静默去重）")
    void duplicateDocIdRejected() {
        assertThatThrownBy(() -> service.ingest(List.of(
                doc("dup", 1L, "SUBJECT_1", "文本A"),
                doc("dup", 2L, "SUBJECT_1", "文本B"))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(embedding, qdrant);
    }

    @Test
    @DisplayName("vectorSize 未配置/小于 1 → 外部调用前 IllegalStateException")
    void vectorSizeInvalidBlocked() {
        properties.setVectorSize(null);
        assertThatThrownBy(() -> service.ingest(List.of(doc("d1", 1L, "SUBJECT_1", "文本"))))
                .isInstanceOf(IllegalStateException.class);

        properties.setVectorSize(0);
        assertThatThrownBy(() -> service.ingest(List.of(doc("d1", 1L, "SUBJECT_1", "文本"))))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(embedding, qdrant);
    }

    /* ==================== 9-11. 响应/向量非法 → IngestException ==================== */

    @Test
    @DisplayName("Embedding 返回数量不符 → IngestException（cause 为 IllegalStateException），written=0")
    void embedCountMismatch() {
        when(embedding.embed(anyList())).thenReturn(List.of()); // 期望 1，返回 0

        IngestException ex = catchIngestException("d1", "文本");
        assertThat(ex.getFailedBatchIndex()).isZero();
        assertThat(ex.getWrittenBatchCount()).isZero();
        assertThat(ex.getWrittenPointCount()).isZero();
        assertThat(ex.getTotalChunkCount()).isEqualTo(1);
        assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        verify(qdrant, times(0)).upsertPoints(anyList());
    }

    @Test
    @DisplayName("向量维度不符 → IngestException（cause 为 IllegalArgumentException）")
    void dimensionMismatch() {
        when(embedding.embed(anyList())).thenReturn(List.of(new double[]{1.0, 2.0, 3.0})); // 3 != DIM(2)

        IngestException ex = catchIngestException("d1", "文本");
        assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
        verify(qdrant, times(0)).upsertPoints(anyList());
    }

    @Test
    @DisplayName("向量含 NaN → IngestException（cause 为 IllegalArgumentException）")
    void nonFiniteVector() {
        when(embedding.embed(anyList())).thenReturn(List.of(new double[]{Double.NaN, 1.0}));

        IngestException ex = catchIngestException("d1", "文本");
        assertThat(ex.getCause()).isInstanceOf(IllegalArgumentException.class);
        verify(qdrant, times(0)).upsertPoints(anyList());
    }

    /* ==================== 12-13. 客户端异常 → IngestException 且保留 cause ==================== */

    @Test
    @DisplayName("Embedding 调用失败 → IngestException 且保留原始 cause，未写 Qdrant")
    void embedCallFails() {
        BusinessException boom = new BusinessException(ErrorCode.AI_CALL_FAILED);
        when(embedding.embed(anyList())).thenThrow(boom);

        IngestException ex = catchIngestException("d1", "文本");
        assertThat(ex.getCause()).isSameAs(boom);
        verify(qdrant, times(0)).upsertPoints(anyList());
    }

    @Test
    @DisplayName("Qdrant 写入失败 → IngestException 且保留原始 cause，written=0")
    void qdrantCallFails() throws Exception {
        when(embedding.embed(anyList())).thenAnswer(dimVectors());
        BusinessException boom = new BusinessException(ErrorCode.AI_CALL_FAILED);
        doThrow(boom).when(qdrant).upsertPoints(anyList());

        IngestException ex = catchIngestException("d1", "文本");
        assertThat(ex.getCause()).isSameAs(boom);
        assertThat(ex.getWrittenBatchCount()).isZero();
        assertThat(ex.getWrittenPointCount()).isZero();
    }

    /* ==================== 14. 前批成功、后批失败 → 部分进度、无成功结果 ==================== */

    @Test
    @DisplayName("第 1 批成功、第 2 批失败 → IngestException 记录 writtenBatches=1/writtenPoints=32，不返回结果")
    void partialThenFail() throws Exception {
        when(embedding.embed(anyList())).thenAnswer(dimVectors());
        final int[] calls = {0};
        Answer<Void> failOnSecond = inv -> {
            calls[0]++;
            if (calls[0] == 2) {
                throw new BusinessException(ErrorCode.AI_CALL_FAILED);
            }
            return null;
        };
        doAnswer(failOnSecond).when(qdrant).upsertPoints(anyList());

        String text = "甲".repeat(DocumentChunker.MAX_CHUNK_CHARS * 33); // 33 块 → 2 批
        IngestException ex = catchIngestExceptionFor(List.of(doc("d1", 1L, "SUBJECT_1", text)));

        assertThat(ex.getFailedBatchIndex()).isEqualTo(1);
        assertThat(ex.getWrittenBatchCount()).isEqualTo(1);
        assertThat(ex.getWrittenPointCount()).isEqualTo(32);
        assertThat(ex.getTotalChunkCount()).isEqualTo(33);
        assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
    }

    /* ==================== 15. 对应关系 + subjectCode 传递 ==================== */

    @Test
    @DisplayName("chunk↔text↔vector↔point 对应；payload 的 chunkId 顺序正确、subjectCode 来自文档")
    void correspondenceAndSubjectCode() throws Exception {
        // 1000 字 → 2 块（800 + 200），无标点 → 硬切
        String text = "甲".repeat(DocumentChunker.MAX_CHUNK_CHARS) + "乙".repeat(200);
        when(embedding.embed(anyList())).thenAnswer(dimVectors());
        List<VectorPoint> captured = new ArrayList<>();
        doAnswer(collectUpserts(captured)).when(qdrant).upsertPoints(anyList());

        service.ingest(List.of(doc("dcorr", 9L, "SUBJECT_4", text)));

        assertThat(captured).hasSize(2);
        for (int i = 0; i < captured.size(); i++) {
            VectorPoint p = captured.get(i);
            assertThat(p.payload()).containsEntry("chunkId", "dcorr#" + i);
            assertThat(p.payload()).containsEntry("subjectCode", "SUBJECT_4");
            String chunkText = (String) p.payload().get("text");
            // 向量第 0 分量为该块文本长度 → 证明 text[i] 与 vector[i] 严格对应
            assertThat(p.vector()[0]).isEqualTo((float) chunkText.length());
        }
    }

    /* ============================== 辅助 ============================== */

    private static KnowledgeDocument doc(String docId, long sourceId, String subject, String text) {
        return new KnowledgeDocument(docId, KnowledgeDocType.QUESTION_ANALYSIS, sourceId,
                subject, "title", text, Map.of());
    }

    private static List<double[]> vectorsFor(List<String> texts) {
        List<double[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(new double[]{t.length(), 1.0}); // DIM=2
        }
        return out;
    }

    private static Answer<List<double[]>> dimVectors() {
        return inv -> vectorsFor(inv.getArgument(0));
    }

    private static Answer<List<double[]>> recordingVectors(List<Integer> sizes) {
        return inv -> {
            List<String> texts = inv.getArgument(0);
            sizes.add(texts.size());
            return vectorsFor(texts);
        };
    }

    private static Answer<Void> recordingUpserts(List<Integer> sizes) {
        return inv -> {
            List<VectorPoint> pts = inv.getArgument(0);
            sizes.add(pts.size());
            return null;
        };
    }

    private static Answer<Void> collectUpserts(List<VectorPoint> sink) {
        return inv -> {
            List<VectorPoint> pts = inv.getArgument(0);
            sink.addAll(pts);
            return null;
        };
    }

    private IngestException catchIngestException(String docId, String text) {
        return catchIngestExceptionFor(List.of(doc(docId, 1L, "SUBJECT_1", text)));
    }

    private IngestException catchIngestExceptionFor(List<KnowledgeDocument> docs) {
        try {
            service.ingest(docs);
            throw new AssertionError("预期抛出 IngestException，但正常返回");
        } catch (IngestException e) {
            return e;
        }
    }
}
