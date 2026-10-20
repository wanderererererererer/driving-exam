package com.driving.exam.server.rag.ingest;

/**
 * 一次知识入库的<b>成功</b>结果统计。
 *
 * <p>仅在**全部批次成功**时返回；任一批失败会抛 {@link IngestException}（不返回成功结果）。</p>
 *
 * @param documentCount        本次输入的文档数（已通过前置校验）
 * @param chunkCount           总计有效分块数（= 需要向量化的文本数）
 * @param batchCount           分批数（每批 ≤ {@code EmbeddingClient.MAX_BATCH_SIZE}）
 * @param embeddedVectorCount  已成功获取并校验通过的向量数；全部成功时等于 {@code chunkCount}
 * @param writtenPointCount    已成功返回的 upsert 调用所对应的点数；全部成功时等于 {@code chunkCount}
 */
public record IngestResult(
        int documentCount,
        int chunkCount,
        int batchCount,
        int embeddedVectorCount,
        int writtenPointCount) {
}
