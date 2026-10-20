package com.driving.exam.server.rag.ingest;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.server.integration.qdrant.QdrantClient;
import com.driving.exam.server.integration.qdrant.QdrantProperties;
import com.driving.exam.server.rag.assemble.KnowledgeDocumentAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理员手动知识入库入口（业务库只读 → Qdrant 写入）。
 *
 * <p>本类解决“项目已有入库组件、但没有运行时入口”的问题：把现有
 * {@link KnowledgeDocumentAssembler}、{@link KnowledgeIngestService} 和 {@link QdrantClient}
 * 串成一个受管理员权限保护、可重复执行的流程。它不改变低层组件的行为：</p>
 * <ol>
 *   <li>先执行 {@link KbDevIngestGuard}，拒绝错误集合/错误向量配置，避免误写正式集合；</li>
 *   <li>只读装配业务库中的题目解析和知识点描述；无文档时直接返回零结果，不触网；</li>
 *   <li>确保 collection 存在（仅 404 时创建）；</li>
 *   <li>调用现有 {@link KnowledgeIngestService} 完成分块、Embedding 和 upsert。</li>
 * </ol>
 *
 * <p><b>重复执行安全</b>：{@link ChunkPointMapper} 使用
 * {@code sourceType + sourceId + chunkIndex} 生成确定性 point ID，重复入库会覆盖同一批点，
 * 不会因为重跑而无限增加重复向量；本流程不删除、不清空 collection，也不写业务数据库。
 * 注意：如果题库内容删除了某个旧文档，旧 point 不会自动清理；这是当前“增量 upsert”方案的已知边界。</p>
 *
 * <p><b>错误语义</b>：装配/配置/Embedding/Qdrant 任一步失败都转换为
 * {@link ErrorCode#RAG_INGEST_FAILED}，保留服务端日志中的原始异常。入库不是事务性操作；
 * 失败前已成功 upsert 的批次不会回滚，错误消息会由底层 {@link IngestException} 说明部分进度。</p>
 */
@Slf4j
@Service
public class KnowledgeBaseIngestService {

    private final KnowledgeDocumentAssembler assembler;
    private final KnowledgeIngestService ingestService;
    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;

    public KnowledgeBaseIngestService(KnowledgeDocumentAssembler assembler,
                                      KnowledgeIngestService ingestService,
                                      QdrantClient qdrantClient,
                                      QdrantProperties qdrantProperties) {
        this.assembler = assembler;
        this.ingestService = ingestService;
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
    }

    /**
     * 执行一次管理员手动入库。
     *
     * @return 装配过滤统计与写入统计；业务库没有可入库文档时 {@code ingested=false}
     * @throws BusinessException 集合/向量配置不合法、业务库读取异常、Embedding/Qdrant 失败
     */
    public KnowledgeBaseIngestReport ingest() {
        try {
            // 1) 在任何外部调用之前完成集合与向量配置守卫
            KbDevIngestGuard.requireDevCollection(qdrantProperties.getCollection());
            KbDevIngestGuard.requireValidatedVectorConfig(qdrantProperties);

            // 2) 只读装配；空结果不创建 collection、不调用 Embedding
            KnowledgeDocumentAssembler.AssemblyResult assembly = assembler.assemble();
            if (assembly.totalDocumentCount() == 0) {
                log.info("知识库入库：没有可入库文档，跳过 Embedding/Qdrant collection={}",
                        qdrantProperties.getCollection());
                return KnowledgeBaseIngestReport.from(
                        qdrantProperties.getCollection(), false, assembly, null);
            }

            // 3) 确保 collection 存在（仅在远端明确 404 时创建）
            qdrantClient.ensureCollection();

            // 4) 分块 + Embedding + upsert；失败时保留部分进度
            IngestResult result = ingestService.ingest(assembly.documents());
            log.info("知识库入库成功 collection={} documents={} chunks={} writtenPoints={}",
                    qdrantProperties.getCollection(), result.documentCount(),
                    result.chunkCount(), result.writtenPointCount());
            return KnowledgeBaseIngestReport.from(
                    qdrantProperties.getCollection(), true, assembly, result);
        } catch (IngestException e) {
            log.error("知识库入库失败 collection={} 失败批次={} 已写入批次={} 已写入点数={} 总块数={}",
                    qdrantProperties.getCollection(), e.getFailedBatchIndex(),
                    e.getWrittenBatchCount(), e.getWrittenPointCount(), e.getTotalChunkCount(), e);
            throw new BusinessException(ErrorCode.RAG_INGEST_FAILED, e.getMessage());
        } catch (BusinessException e) {
            log.error("知识库入库失败（下游服务异常）collection={} code={} msg={}",
                    qdrantProperties.getCollection(), e.getCode(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.RAG_INGEST_FAILED);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("知识库入库失败（配置或数据非法）collection={} msg={}",
                    qdrantProperties.getCollection(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.RAG_INGEST_FAILED, e.getMessage());
        }
    }
}
