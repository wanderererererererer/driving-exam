package com.driving.exam.server.rag.ingest;

import com.driving.exam.server.integration.embedding.EmbeddingClient;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.integration.qdrant.VectorPoint;
import com.driving.exam.server.rag.DocumentChunk;
import com.driving.exam.server.rag.DocumentChunker;
import com.driving.exam.server.rag.KnowledgeDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识入库编排服务（R2.4 Step 2，**离线可测**）。
 *
 * <p>把一批 {@link KnowledgeDocument} 转换为向量点写入 Qdrant：
 * 分块 → 分批 Embedding（≤{@link EmbeddingClient#MAX_BATCH_SIZE}）→ 维度/数值校验 → 组装
 * {@link VectorPoint} → upsert。每批 Embedding 完成后**立即**转换并写入。</p>
 *
 * <h3>关键设计</h3>
 * <ul>
 *   <li><b>维度唯一来源</b>：注入的 {@link QdrantProperties#getVectorSize()}；为 {@code null}/{@code <1} 时在
 *       <b>任何外部调用之前</b>失败（{@link IllegalStateException}），<b>绝不</b>默认 1024。</li>
 *   <li><b>前置校验整批</b>：输入列表/元素/`subjectCode`/重复 `docId`/维度均在外部调用前校验，
 *       避免“后面的非法文档导致前面已写入”。</li>
 *   <li><b>批次 fail-fast、非事务性</b>：任一批失败抛 {@link IngestException}（携带部分进度、保留 cause）；
 *       <b>不重试</b>；已写入批次不回滚。</li>
 *   <li><b>不读数据库</b>：文档由调用方传入（DB 装配属后续阶段）。</li>
 *   <li>不实现旧分块清理/版本/队列/并发。</li>
 * </ul>
 */
@Slf4j
@Service
public class KnowledgeIngestService {

    private final DocumentChunker documentChunker;
    private final ChunkPointMapper chunkPointMapper;
    private final VectorConverter vectorConverter;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;

    public KnowledgeIngestService(DocumentChunker documentChunker,
                                  ChunkPointMapper chunkPointMapper,
                                  VectorConverter vectorConverter,
                                  EmbeddingClient embeddingClient,
                                  QdrantClient qdrantClient,
                                  QdrantProperties qdrantProperties) {
        this.documentChunker = documentChunker;
        this.chunkPointMapper = chunkPointMapper;
        this.vectorConverter = vectorConverter;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
    }

    /**
     * 执行一次入库。
     *
     * @param documents 待入库文档（可为空列表→零结果；不得为 null、不得含 null 元素）
     * @return 成功统计（仅全部批次成功时返回）
     * @throws IllegalArgumentException 输入非法（null 列表/null 元素/空白 subjectCode/重复 docId/空白 docId）
     * @throws IllegalStateException    {@code qdrant.vector-size} 未配置或非法
     * @throws IngestException          某一批次处理失败（非事务性；携带部分进度与原始 cause）
     */
    public IngestResult ingest(List<KnowledgeDocument> documents) {
        validateDocuments(documents);
        int dimension = requireVectorSize();

        // 分块，并保留每个分块对应的真实 subjectCode（来自其 KnowledgeDocument）
        List<ChunkSubject> items = new ArrayList<>();
        for (KnowledgeDocument doc : documents) {
            for (DocumentChunk chunk : documentChunker.chunk(doc)) {
                items.add(new ChunkSubject(chunk, doc.subjectCode()));
            }
        }
        if (items.isEmpty()) {
            log.info("知识入库：无有效分块，跳过 Embedding/Qdrant。documents={}", documents.size());
            return new IngestResult(documents.size(), 0, 0, 0, 0);
        }

        int total = items.size();
        int batchSize = EmbeddingClient.MAX_BATCH_SIZE;
        int batchCount = (total + batchSize - 1) / batchSize;
        int writtenBatches = 0;
        int writtenPoints = 0;

        for (int start = 0; start < total; start += batchSize) {
            int batchIndex = start / batchSize;
            int end = Math.min(start + batchSize, total);
            List<ChunkSubject> batch = items.subList(start, end);
            try {
                // 1) 批次文本（与分块严格同序）
                List<String> texts = new ArrayList<>(batch.size());
                for (ChunkSubject cs : batch) {
                    texts.add(chunkPointMapper.toEmbeddingInput(cs.chunk()));
                }

                // 2) Embedding（返回须与输入数量一致）
                List<double[]> vectors = embeddingClient.embed(texts);
                if (vectors == null || vectors.size() != texts.size()) {
                    throw new IllegalStateException("Embedding 返回向量数量与输入不一致 expected=" + texts.size()
                            + " actual=" + (vectors == null ? "null" : vectors.size()));
                }

                // 3) 逐条转换（维度/有限值校验）+ 生成 point ID/payload（chunk[i] ↔ vector[i]）
                List<VectorPoint> points = new ArrayList<>(batch.size());
                for (int i = 0; i < batch.size(); i++) {
                    ChunkSubject cs = batch.get(i);
                    float[] fv = vectorConverter.toFloatVector(vectors.get(i), dimension);
                    ChunkPoint cp = chunkPointMapper.toPoint(cs.chunk(), cs.subjectCode());
                    points.add(new VectorPoint(cp.pointId(), fv, cp.payload()));
                }

                // 4) 写入本批；仅在 upsert 正常返回后才计入统计
                qdrantClient.upsertPoints(points);
                writtenBatches++;
                writtenPoints += points.size();
            } catch (RuntimeException e) {
                throw new IngestException(batchIndex, writtenBatches, writtenPoints, total, e);
            }
        }

        log.info("知识入库完成：documents={} chunks={} batches={} written={}",
                documents.size(), total, batchCount, writtenPoints);
        // 全部批次成功：embedded 与 written 均等于总块数
        return new IngestResult(documents.size(), total, batchCount, writtenPoints, writtenPoints);
    }

    /* ============================== 前置校验（外部调用之前完成） ============================== */

    private void validateDocuments(List<KnowledgeDocument> documents) {
        if (documents == null) {
            throw new IllegalArgumentException("documents 不能为 null");
        }
        Set<String> docIds = new HashSet<>();
        for (int i = 0; i < documents.size(); i++) {
            KnowledgeDocument doc = documents.get(i);
            if (doc == null) {
                throw new IllegalArgumentException("documents[" + i + "] 不能为 null");
            }
            if (doc.docId() == null || doc.docId().isBlank()) {
                throw new IllegalArgumentException("documents[" + i + "] 的 docId 不能为空");
            }
            if (doc.subjectCode() == null || doc.subjectCode().isBlank()) {
                throw new IllegalArgumentException("documents[" + i + "] 的 subjectCode 不能为空（docId=" + doc.docId() + "）");
            }
            if (!docIds.add(doc.docId())) {
                throw new IllegalArgumentException("同一次入库存在重复 docId：" + doc.docId());
            }
        }
    }

    /** 维度唯一来源：QdrantProperties.vectorSize，未配置/非法即失败（不猜测默认值）。 */
    private int requireVectorSize() {
        Integer size = qdrantProperties.getVectorSize();
        if (size == null || size < 1) {
            throw new IllegalStateException("qdrant.vector-size 未配置或非法，禁止入库（不得猜测向量维度）");
        }
        return size;
    }

    /** 分块 + 其所属文档的 subjectCode（保证对应关系不错位）。 */
    private record ChunkSubject(DocumentChunk chunk, String subjectCode) {
    }
}
