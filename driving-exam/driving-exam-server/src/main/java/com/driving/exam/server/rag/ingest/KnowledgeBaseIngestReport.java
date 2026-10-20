package com.driving.exam.server.rag.ingest;

import com.driving.exam.server.rag.assemble.KnowledgeDocumentAssembler;

/**
 * 一次知识库入库操作的完整统计（管理员手动入库接口的返回）。
 *
 * <p>它同时保留装配阶段的过滤计数和写入阶段的分块/批次统计，便于管理员核对：
 * “读到了多少题目/知识点、为什么被过滤、最终写入了多少个向量点”。不包含知识库正文，
 * 避免把题库内容通过运维接口整批返回。</p>
 *
 * @param collection                                      本次写入的 Qdrant collection
 * @param ingested                                       是否实际执行了 Embedding/upsert；
 *                                                       装配结果为空时为 false，未调用外部服务
 * @param questionReadCount                               读取的题目数（过滤前）
 * @param questionSkippedByStatusCount                    因未启用被跳过的题目数
 * @param questionSkippedBySubjectCount                   因科目不在 SUBJECT_1/SUBJECT_4 被跳过的题目数
 * @param questionSkippedByBlankAnalysisCount             因解析为空被跳过的题目数
 * @param questionDocumentCount                           实际生成的题目文档数
 * @param knowledgePointReadCount                         读取的知识点数
 * @param knowledgePointSkippedByBlankDescriptionCount    因描述为空被跳过的知识点数
 * @param knowledgePointDocumentCount                     实际生成的知识点文档数
 * @param documentCount                                   本次输入入库服务的文档总数
 * @param chunkCount                                      分块总数
 * @param batchCount                                      Embedding/upsert 批次数
 * @param writtenPointCount                               成功返回 upsert 的点数
 */
public record KnowledgeBaseIngestReport(
        String collection,
        boolean ingested,
        int questionReadCount,
        int questionSkippedByStatusCount,
        int questionSkippedBySubjectCount,
        int questionSkippedByBlankAnalysisCount,
        int questionDocumentCount,
        int knowledgePointReadCount,
        int knowledgePointSkippedByBlankDescriptionCount,
        int knowledgePointDocumentCount,
        int documentCount,
        int chunkCount,
        int batchCount,
        int writtenPointCount) {

    /** 从装配结果和写入结果构造报告；未执行写入时传入零结果。 */
    public static KnowledgeBaseIngestReport from(String collection,
                                                 boolean ingested,
                                                 KnowledgeDocumentAssembler.AssemblyResult assembly,
                                                 IngestResult ingest) {
        int chunkCount = ingest == null ? 0 : ingest.chunkCount();
        int batchCount = ingest == null ? 0 : ingest.batchCount();
        int writtenPointCount = ingest == null ? 0 : ingest.writtenPointCount();
        return new KnowledgeBaseIngestReport(
                collection,
                ingested,
                assembly.questionReadCount(),
                assembly.questionSkippedByStatusCount(),
                assembly.questionSkippedBySubjectCount(),
                assembly.questionSkippedByBlankAnalysisCount(),
                assembly.questionDocumentCount(),
                assembly.knowledgePointReadCount(),
                assembly.knowledgePointSkippedByBlankDescriptionCount(),
                assembly.knowledgePointDocumentCount(),
                assembly.totalDocumentCount(),
                chunkCount,
                batchCount,
                writtenPointCount);
    }
}
