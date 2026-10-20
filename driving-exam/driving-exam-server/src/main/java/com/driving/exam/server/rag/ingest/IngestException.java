package com.driving.exam.server.rag.ingest;

/**
 * 知识入库**批次失败**异常（fail-fast，非事务性）。
 *
 * <p>携带可读取的部分进度，用于让调用方明确：<b>前面批次可能已成功写入远端</b>、当前批次失败、
 * 整个流程**不是事务性的**、已写入批次**不会自动回滚**。</p>
 *
 * <h3>计数口径（重要）</h3>
 * <ul>
 *   <li>{@code writtenBatchCount}：仅统计 <b>upsert 已正常返回</b> 的批次数；</li>
 *   <li>{@code writtenPointCount}：仅统计这些<b>已正常返回批次</b>的点数；</li>
 *   <li>失败批次的<b>远端实际持久化状态可能未知</b>（可能未写入或部分写入）——本类<b>不</b>声称其一定没写，
 *       也<b>不</b>把本字段当作“Qdrant 中实际存在的点数”的精确统计。</li>
 * </ul>
 *
 * <p>原始异常通过 {@link #getCause()} 保留（Embedding/Qdrant 远端异常、数量不符、向量非法等）。</p>
 */
public class IngestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int failedBatchIndex;
    private final int writtenBatchCount;
    private final int writtenPointCount;
    private final int totalChunkCount;

    public IngestException(int failedBatchIndex, int writtenBatchCount, int writtenPointCount,
                           int totalChunkCount, Throwable cause) {
        super(buildMessage(failedBatchIndex, writtenBatchCount, writtenPointCount, totalChunkCount), cause);
        this.failedBatchIndex = failedBatchIndex;
        this.writtenBatchCount = writtenBatchCount;
        this.writtenPointCount = writtenPointCount;
        this.totalChunkCount = totalChunkCount;
    }

    /** 失败批次下标（0-based）。 */
    public int getFailedBatchIndex() {
        return failedBatchIndex;
    }

    /** 失败前已正常返回 upsert 的批次数。 */
    public int getWrittenBatchCount() {
        return writtenBatchCount;
    }

    /** 失败前已正常返回 upsert 的点数（不代表远端精确存在数）。 */
    public int getWrittenPointCount() {
        return writtenPointCount;
    }

    /** 本次总有效分块数。 */
    public int getTotalChunkCount() {
        return totalChunkCount;
    }

    private static String buildMessage(int failedBatchIndex, int writtenBatchCount,
                                       int writtenPointCount, int totalChunkCount) {
        return "知识入库失败：批次 #" + failedBatchIndex + "（0-based）处理失败。"
                + "此前已正常返回写入 " + writtenBatchCount + " 批 / " + writtenPointCount + " 个点"
                + "（总块数 " + totalChunkCount + "）。"
                + "本流程非事务性：已写入批次不会自动回滚；失败批次是否已部分写入远端不确定。原始原因见 cause。";
    }
}
