package com.driving.exam.server.integration.qdrant;

import java.util.Map;

/**
 * 一个待写入 Qdrant 的向量点（数据载体）。
 *
 * <p>由 R2.4 的入库编排组装：{@code id} 取 {@code ChunkPoint.pointId()}、{@code payload} 取
 * {@code ChunkPoint.payload()}、{@code vector} 取 {@code EmbeddingClient} 返回向量（转 float）。</p>
 *
 * <p>对应 Qdrant REST upsert 的 JSON 结构：{@code {id, vector, payload}}。{@code vector} 使用 {@code float[]}；
 * 校验（维度/有限数值等）在 {@link QdrantClient} 发送前完成。</p>
 *
 * @param id      point ID（UUID 字符串，来自 {@code ChunkPoint}）
 * @param vector  向量（float 数组）
 * @param payload 结构化负载（Map，来自 {@code ChunkPoint}）
 */
public record VectorPoint(String id, float[] vector, Map<String, Object> payload) {
}
