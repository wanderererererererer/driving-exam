package com.driving.exam.server.rag.ingest;

import com.driving.exam.server.integration.qdrant.QdrantProperties;

/**
 * 开发集合入库安全守卫（R2 装配阶段，<b>纯逻辑、无副作用</b>）。
 *
 * <p>供未来的手动集成入口在<b>第一次任何 Qdrant / Embedding 调用之前</b>调用，
 * 以纯内存校验阻止"写错集合"与"向量配置不符"两类事故。本类本身：</p>
 * <ul>
 *   <li><b>不</b>使用 Spring 注解、<b>不</b>访问数据库、<b>不</b>调用 Embedding、<b>不</b>访问 Qdrant；</li>
 *   <li><b>不</b>发送 HTTP 请求、<b>不</b>读取环境变量、<b>不</b>修改任何配置；</li>
 *   <li><b>不</b>依赖装配器/入库编排服务，<b>不</b>创建集合，<b>不</b>执行任何写入/删除/清理。</li>
 * </ul>
 * <p>只接收参数、校验，失败抛出异常、成功正常返回。</p>
 *
 * <h3>集合边界（项目已定策略）</h3>
 * <ul>
 *   <li>正式集合 {@code driving_exam_kb}：<b>严禁</b>写入；</li>
 *   <li>R2.4 集成测试集合 {@code driving_exam_kb_it}：必须保持为空，<b>严禁</b>写入；</li>
 *   <li>开发集合 {@code driving_exam_kb_dev}：当前阶段<b>唯一</b>允许写入的集合。</li>
 * </ul>
 * <p>集合名采用<b>严格逐字符比较</b>：不做 trim、不忽略大小写、不接受任何别名或前后空格。</p>
 *
 * <h3>向量配置边界</h3>
 * <ul>
 *   <li>{@code qdrant.vector-size} 必须显式为 {@code 1024}——依据是本项目 R2.4 已完成的
 *       BGE-M3 真实 Embedding + Qdrant 写入回查验证（2026-10-18），<b>不</b>引入新的模型或维度假设；</li>
 *   <li>{@code qdrant.distance} 必须表示 {@code Cosine}，比较策略为<b>大小写不敏感</b>
 *       （{@code equalsIgnoreCase}；前后空格等不一致仍会被拒绝），与 {@link QdrantProperties#getDistance()}
 *       的字符串类型一致（不做枚举转换，避免为测试而修改属性类）。</li>
 * </ul>
 *
 * <p><b>异常类型约定</b>（沿用项目纯逻辑类风格）：集合名/入参非法抛 {@link IllegalArgumentException}；
 * 已绑定配置的取值不符合要求抛 {@link IllegalStateException}。异常信息只包含集合名与配置项取值，
 * <b>不含</b>任何密钥、口令或令牌。</p>
 */
public final class KbDevIngestGuard {

    /** 当前阶段唯一允许写入的开发集合名。 */
    public static final String DEV_COLLECTION = "driving_exam_kb_dev";

    /** 正式集合名（严禁写入）。 */
    public static final String PRODUCTION_COLLECTION = "driving_exam_kb";

    /** R2.4 集成测试集合名（必须保持为空，严禁写入）。 */
    public static final String INTEGRATION_TEST_COLLECTION = "driving_exam_kb_it";

    /** 期望向量维度：R2.4 实测 BGE-M3 维度（2026-10-18）。 */
    public static final int EXPECTED_VECTOR_SIZE = 1024;

    /** 期望距离度量（大小写不敏感比较）。 */
    public static final String EXPECTED_DISTANCE = "Cosine";

    private KbDevIngestGuard() {
        // 纯静态工具类，禁止实例化
    }

    /**
     * 校验目标集合名——必须严格逐字符等于 {@link #DEV_COLLECTION}。
     *
     * <p>拒绝：正式集合、集成测试集合、{@code null}、空串、纯空白、带前后空格的开发集合名、
     * 大小写变体以及任何其他名称/别名。</p>
     *
     * @param collection 实际生效的目标集合名（通常取自 {@code QdrantProperties#getCollection()}）
     * @throws IllegalArgumentException 集合名不是唯一允许的开发集合名
     */
    public static void requireDevCollection(String collection) {
        if (DEV_COLLECTION.equals(collection)) {
            return;
        }
        String reason;
        if (collection == null || collection.isBlank()) {
            reason = "集合名为 null 或空白";
        } else if (PRODUCTION_COLLECTION.equals(collection)) {
            reason = "目标是正式集合，严禁写入";
        } else if (INTEGRATION_TEST_COLLECTION.equals(collection)) {
            reason = "目标是集成测试集合，必须保持为空、严禁写入";
        } else {
            reason = "与唯一允许的集合名不完全一致（不接受前后空格、大小写变体或其他别名）";
        }
        throw new IllegalArgumentException(
                "开发集合入库守卫拒绝：目标集合名 [" + collection + "] 非法（" + reason + "）。"
                        + "唯一允许的集合名是严格逐字符等于 " + DEV_COLLECTION + "（不做 trim、不忽略大小写）");
    }

    /**
     * 校验实际绑定的向量配置——维度必须为 {@link #EXPECTED_VECTOR_SIZE}，距离必须表示 Cosine。
     *
     * @param properties 实际生效的 {@link QdrantProperties}（通常由 Spring 绑定后注入）
     * @throws IllegalArgumentException {@code properties} 为 {@code null}
     * @throws IllegalStateException    维度缺失/不等于 1024，或距离缺失/不表示 Cosine
     */
    public static void requireValidatedVectorConfig(QdrantProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("QdrantProperties 不能为空，无法校验向量配置");
        }
        Integer size = properties.getVectorSize();
        if (size == null || size != EXPECTED_VECTOR_SIZE) {
            throw new IllegalStateException(
                    "向量配置守卫拒绝：qdrant.vector-size 必须显式为 " + EXPECTED_VECTOR_SIZE
                            + "（BGE-M3 实测维度，2026-10-18 R2.4 验证），实际=" + size);
        }
        String distance = properties.getDistance();
        if (distance == null || distance.isBlank() || !EXPECTED_DISTANCE.equalsIgnoreCase(distance)) {
            throw new IllegalStateException(
                    "向量配置守卫拒绝：qdrant.distance 必须表示 " + EXPECTED_DISTANCE
                            + "（大小写不敏感比较），实际=" + distance);
        }
    }
}
