package com.driving.exam.server.rag.ingest;

import org.springframework.stereotype.Component;

/**
 * Embedding 向量转换与校验工具（R2.4 Step 1，纯离线、无副作用）。
 *
 * <p>把 {@code EmbeddingClient} 返回的 {@code double[]} 安全转换为 Qdrant {@code VectorPoint} 使用的
 * {@code float[]}，并做<b>严格校验</b>。期望维度由调用方显式传入——<b>本类不设默认维度</b>，更不默认 1024。</p>
 *
 * <h3>校验契约（任一不满足即抛 {@link IllegalArgumentException}，整条失败、不返回部分结果）</h3>
 * <ul>
 *   <li>向量 {@code null}、空数组 → 拒绝；</li>
 *   <li>期望维度 {@code < 1} → 拒绝；</li>
 *   <li>向量长度 ≠ 期望维度 → 拒绝；</li>
 *   <li>任一分量非有限（{@code NaN}/{@code ±Infinity}）→ 拒绝；</li>
 *   <li><b>转 float 后</b>再判有限，防“有限 double 溢出为 float Infinity”→ 拒绝。</li>
 * </ul>
 * <p><b>不允许</b>截断、补零、随机填充或静默修正；返回<b>新数组</b>，不修改调用方传入的数组。</p>
 *
 * <p>本类不调用 Embedding API、不读配置、不连 Qdrant；无状态，单测可直接 {@code new VectorConverter()}。</p>
 */
@Component
public class VectorConverter {

    /**
     * 将 {@code double[]} 安全转换为 {@code float[]}。
     *
     * @param vector            Embedding 返回向量（不得为 null/空）
     * @param expectedDimension 期望维度（调用方显式提供，必须 ≥ 1）
     * @return 新 {@code float[]}，长度与顺序与输入一致
     * @throws IllegalArgumentException 任一校验失败（信息含可定位的原因）
     */
    public float[] toFloatVector(double[] vector, int expectedDimension) {
        if (expectedDimension < 1) {
            throw new IllegalArgumentException("期望维度必须 ≥ 1，实际=" + expectedDimension);
        }
        if (vector == null) {
            throw new IllegalArgumentException("向量不能为 null");
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("向量不能为空数组");
        }
        if (vector.length != expectedDimension) {
            throw new IllegalArgumentException(
                    "向量维度不符 expected=" + expectedDimension + " actual=" + vector.length);
        }

        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            double d = vector[i];
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("向量第 " + i + " 个分量为非有限数值（NaN/Infinity）");
            }
            float f = (float) d;
            if (!Float.isFinite(f)) {
                // 有限 double 仍可能超出 float 表示范围 → 转成 Infinity，必须拒绝
                throw new IllegalArgumentException("向量第 " + i + " 个分量超出 float 表示范围");
            }
            result[i] = f;
        }
        return result;
    }
}
