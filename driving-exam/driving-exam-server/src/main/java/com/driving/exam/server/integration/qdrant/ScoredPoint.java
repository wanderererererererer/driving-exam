package com.driving.exam.server.integration.qdrant;

import java.util.Map;

/**
 * Qdrant Query API 返回的一个<b>打分结果点</b>（数据载体）。
 *
 * <p>对应 {@code POST /collections/{name}/points/query} 响应中 {@code result.points[]} 的元素，
 * 字段与 Qdrant 1.19.2 官方 OpenAPI 的 {@code ScoredPoint} schema 对齐：{@code id / version / score / payload}
 * （schema 中 {@code version} <b>为必填字段</b>：客户端要求其存在且为整数，缺失或类型非法时
 * 拒绝整个响应；因此解析成功的实例中 {@code version} <b>恒非 null</b>）。</p>
 *
 * <p>本记录只承载客户端层的原始结果；payload 的业务字段校验与类型转换属于后续检索 Service 的职责。</p>
 *
 * @param id      point ID（本项目写入的均为标准 UUID 字符串；客户端只接受文本 ID）
 * @param version point 版本号（<b>必填</b>：客户端保证解析成功时为非 null 的整数）
 * @param score   相似度分数（float；语义由 Qdrant 按距离度量决定——Cosine 下越高越相似，范围参考 -1~1，
 *                归一化向量下多见 0~1；排序责任在 Qdrant，客户端不重排）
 * @param payload 结构化负载（请求 {@code with_payload=true} 时返回；Qdrant schema 允许为空，故可能为 {@code null}）
 */
public record ScoredPoint(String id, Long version, double score, Map<String, Object> payload) {
}
