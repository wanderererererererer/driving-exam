package com.driving.exam.server.rag.ingest;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.rag.KnowledgeDocType;
import com.driving.exam.server.rag.KnowledgeDocument;
import com.driving.exam.server.rag.assemble.KnowledgeDocumentAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link KnowledgeBaseIngestService} 离线单元测试。
 *
 * <p>Mockito 模拟装配器、低层入库服务与 Qdrant 客户端；使用真实纯逻辑守卫。
 * 不启动 Spring、不访问数据库、不调用 Embedding/Qdrant。</p>
 */
@DisplayName("管理员知识入库编排（离线）")
class KnowledgeBaseIngestServiceTest {

    private KnowledgeDocumentAssembler assembler;
    private KnowledgeIngestService ingestService;
    private QdrantClient qdrantClient;
    private QdrantProperties properties;
    private KnowledgeBaseIngestService service;

    @BeforeEach
    void setUp() {
        assembler = mock(KnowledgeDocumentAssembler.class);
        ingestService = mock(KnowledgeIngestService.class);
        qdrantClient = mock(QdrantClient.class);
        properties = new QdrantProperties();
        properties.setCollection(KbDevIngestGuard.DEV_COLLECTION);
        properties.setVectorSize(KbDevIngestGuard.EXPECTED_VECTOR_SIZE);
        properties.setDistance(KbDevIngestGuard.EXPECTED_DISTANCE);
        service = new KnowledgeBaseIngestService(assembler, ingestService, qdrantClient, properties);
    }

    @Test
    @DisplayName("有文档：先守卫、再确保 collection，最后调用现有入库服务并返回统计")
    void ingestHappyPath() {
        KnowledgeDocument doc = document("QUESTION_ANALYSIS:1", 1L);
        when(assembler.assemble()).thenReturn(assembly(List.of(doc), 1, 1, 0, 0, 1, 0, 0, 0));
        when(ingestService.ingest(anyList()))
                .thenReturn(new IngestResult(1, 1, 1, 1, 1));

        KnowledgeBaseIngestReport report = service.ingest();

        assertThat(report.ingested()).isTrue();
        assertThat(report.collection()).isEqualTo(KbDevIngestGuard.DEV_COLLECTION);
        assertThat(report.documentCount()).isEqualTo(1);
        assertThat(report.chunkCount()).isEqualTo(1);
        assertThat(report.batchCount()).isEqualTo(1);
        assertThat(report.writtenPointCount()).isEqualTo(1);
        verify(qdrantClient).ensureCollection();
        verify(ingestService).ingest(List.of(doc));
    }

    @Test
    @DisplayName("装配结果为空：不创建 collection、不调用 Embedding/Qdrant，返回 ingested=false")
    void emptyAssemblyDoesNotTouchQdrant() {
        when(assembler.assemble()).thenReturn(assembly(List.of(), 0, 0, 0, 0, 0, 0, 0, 0));

        KnowledgeBaseIngestReport report = service.ingest();

        assertThat(report.ingested()).isFalse();
        assertThat(report.documentCount()).isZero();
        assertThat(report.writtenPointCount()).isZero();
        verifyNoInteractions(ingestService, qdrantClient);
    }

    @Test
    @DisplayName("集合不是唯一允许的开发集合：在装配/外部调用前拒绝，返回 4006")
    void wrongCollectionRejectedBeforeAnyWork() {
        properties.setCollection(KbDevIngestGuard.PRODUCTION_COLLECTION);

        assertThatThrownBy(() -> service.ingest())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RAG_INGEST_FAILED.getCode()));
        verifyNoInteractions(assembler, ingestService, qdrantClient);
    }

    @Test
    @DisplayName("低层入库失败：转换为 4006，并保留失败前的部分进度说明")
    void ingestFailureMappedToBusinessError() {
        KnowledgeDocument doc = document("QUESTION_ANALYSIS:1", 1L);
        when(assembler.assemble()).thenReturn(assembly(List.of(doc), 1, 1, 0, 0, 1, 0, 0, 0));
        doThrow(new IngestException(0, 0, 0, 1, new RuntimeException("embedding failed")))
                .when(ingestService).ingest(anyList());

        assertThatThrownBy(() -> service.ingest())
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.RAG_INGEST_FAILED.getCode());
                    assertThat(e.getMessage()).contains("知识入库失败");
                });
        verify(qdrantClient).ensureCollection();
    }

    @Test
    @DisplayName("Embedding/Qdrant 业务异常：转换为 4006，不向上暴露底层错误码")
    void downstreamBusinessFailureMappedToIngestError() {
        KnowledgeDocument doc = document("QUESTION_ANALYSIS:1", 1L);
        when(assembler.assemble()).thenReturn(assembly(List.of(doc), 1, 1, 0, 0, 1, 0, 0, 0));
        doThrow(new BusinessException(ErrorCode.AI_CALL_FAILED))
                .when(ingestService).ingest(anyList());

        assertThatThrownBy(() -> service.ingest())
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.RAG_INGEST_FAILED.getCode()));
        verify(qdrantClient).ensureCollection();
    }

    private static KnowledgeDocument document(String docId, long sourceId) {
        return new KnowledgeDocument(
                docId, KnowledgeDocType.QUESTION_ANALYSIS, sourceId, "SUBJECT_1",
                "标题", "红灯亮时禁止通行。", Map.of());
    }

    private static KnowledgeDocumentAssembler.AssemblyResult assembly(
            List<KnowledgeDocument> documents,
            int questionReadCount,
            int questionSkippedByStatusCount,
            int questionSkippedBySubjectCount,
            int questionSkippedByBlankAnalysisCount,
            int questionDocumentCount,
            int knowledgePointReadCount,
            int knowledgePointSkippedByBlankDescriptionCount,
            int knowledgePointDocumentCount) {
        return new KnowledgeDocumentAssembler.AssemblyResult(
                documents, questionReadCount, questionSkippedByStatusCount,
                questionSkippedBySubjectCount, questionSkippedByBlankAnalysisCount,
                questionDocumentCount, knowledgePointReadCount,
                knowledgePointSkippedByBlankDescriptionCount, knowledgePointDocumentCount);
    }
}
