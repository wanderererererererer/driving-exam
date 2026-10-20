package com.driving.exam.server.rag.ingest;

import com.driving.exam.server.integration.qdrant.QdrantProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link KbDevIngestGuard} 离线单元测试。
 *
 * <p>纯对象构造 + JUnit5 + AssertJ：<b>不启动 Spring、不连数据库、不调用 Embedding API、
 * 不访问 Qdrant、不访问网络、不读取环境变量</b>。</p>
 *
 * <p>覆盖两类边界：集合名严格逐字符校验（9 组场景）与向量配置校验（7 组场景，
 * 含 distance 大小写不敏感策略）；并验证两类错误的异常类型与错误信息可区分。</p>
 */
@DisplayName("R2 开发集合入库守卫（离线）")
class KbDevIngestGuardTest {

    /* ============================== 集合名校验 ============================== */

    @Test
    @DisplayName("集合名：严格的 driving_exam_kb_dev 被接受")
    void acceptsDevCollection() {
        assertThatCode(() -> KbDevIngestGuard.requireDevCollection("driving_exam_kb_dev"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("集合名：正式集合 driving_exam_kb 被拒绝（信息指明正式集合）")
    void rejectsProductionCollection() {
        assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection("driving_exam_kb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("driving_exam_kb")
                .hasMessageContaining("正式集合");
    }

    @Test
    @DisplayName("集合名：集成测试集合 driving_exam_kb_it 被拒绝（信息指明集成测试集合）")
    void rejectsIntegrationTestCollection() {
        assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection("driving_exam_kb_it"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("driving_exam_kb_it")
                .hasMessageContaining("集成测试");
    }

    @Test
    @DisplayName("集合名：null 被拒绝")
    void rejectsNullCollection() {
        assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null 或空白");
    }

    @Test
    @DisplayName("集合名：空字符串被拒绝")
    void rejectsEmptyCollection() {
        assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null 或空白");
    }

    @Test
    @DisplayName("集合名：纯空白字符串被拒绝")
    void rejectsBlankCollection() {
        for (String blank : new String[]{"   ", "\t", "\n", " \t\n "}) {
            assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection(blank))
                    .as("纯空白应被拒绝：%s", escape(blank))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null 或空白");
        }
    }

    @Test
    @DisplayName("集合名：带前后空格的开发集合名被拒绝（不做 trim）")
    void rejectsPaddedCollection() {
        for (String padded : new String[]{" driving_exam_kb_dev", "driving_exam_kb_dev ", " driving_exam_kb_dev "}) {
            assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection(padded))
                    .as("前后空格应被拒绝：%s", escape(padded))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不完全一致");
        }
    }

    @Test
    @DisplayName("集合名：大小写变体被拒绝（不忽略大小写）")
    void rejectsCaseVariants() {
        for (String variant : new String[]{"DRIVING_EXAM_KB_DEV", "Driving_Exam_Kb_Dev", "driving_Exam_kb_dev"}) {
            assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection(variant))
                    .as("大小写变体应被拒绝：%s", variant)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不完全一致");
        }
    }

    @Test
    @DisplayName("集合名：其他名称/别名一律被拒绝")
    void rejectsOtherNames() {
        for (String other : new String[]{
                "driving_exam_kb_dev2", "driving_exam_kb_prod", "kb_dev", "driving-exam-kb-dev", "dev"}) {
            assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection(other))
                    .as("其他名称应被拒绝：%s", other)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不完全一致");
        }
    }

    /* ============================== 向量配置校验 ============================== */

    @Test
    @DisplayName("向量配置：vectorSize=1024 且 distance=Cosine 被接受")
    void acceptsValidVectorConfig() {
        assertThatCode(() -> KbDevIngestGuard.requireValidatedVectorConfig(validProperties()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("向量配置：QdrantProperties 为 null 被拒绝")
    void rejectsNullProperties() {
        assertThatThrownBy(() -> KbDevIngestGuard.requireValidatedVectorConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("QdrantProperties 不能为空");
    }

    @Test
    @DisplayName("向量配置：vectorSize 为 null 被拒绝（信息指明 vector-size 与 1024）")
    void rejectsNullVectorSize() {
        QdrantProperties p = validProperties();
        p.setVectorSize(null);

        assertThatThrownBy(() -> KbDevIngestGuard.requireValidatedVectorConfig(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector-size")
                .hasMessageContaining("1024");
    }

    @Test
    @DisplayName("向量配置：vectorSize 小于或大于 1024 被拒绝")
    void rejectsWrongVectorSize() {
        for (int size : new int[]{0, 1, 768, 1023, 1025, 1536}) {
            QdrantProperties p = validProperties();
            p.setVectorSize(size);
            assertThatThrownBy(() -> KbDevIngestGuard.requireValidatedVectorConfig(p))
                    .as("错误维度应被拒绝：%d", size)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("vector-size");
        }
    }

    @Test
    @DisplayName("向量配置：distance 为 null 被拒绝")
    void rejectsNullDistance() {
        QdrantProperties p = validProperties();
        p.setDistance(null);

        assertThatThrownBy(() -> KbDevIngestGuard.requireValidatedVectorConfig(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("distance");
    }

    @Test
    @DisplayName("向量配置：空/纯空白/非 Cosine 距离被拒绝")
    void rejectsNonCosineDistance() {
        for (String distance : new String[]{"", "   ", "Euclid", "Dot", "Manhattan", "Cosine ", " Cosine"}) {
            QdrantProperties p = validProperties();
            p.setDistance(distance);
            assertThatThrownBy(() -> KbDevIngestGuard.requireValidatedVectorConfig(p))
                    .as("非 Cosine 距离应被拒绝：[%s]", escape(distance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cosine");
        }
    }

    @Test
    @DisplayName("向量配置：distance 大小写不敏感 → cosine/COSINE/cOsInE 均被接受（既定策略）")
    void acceptsDistanceCaseVariants() {
        for (String distance : new String[]{"cosine", "COSINE", "cOsInE"}) {
            QdrantProperties p = validProperties();
            p.setDistance(distance);
            assertThatCode(() -> KbDevIngestGuard.requireValidatedVectorConfig(p))
                    .as("大小写变体应被接受：%s", distance)
                    .doesNotThrowAnyException();
        }
    }

    /* ============================== 错误可区分性 ============================== */

    @Test
    @DisplayName("两类错误可区分：集合名错误=IllegalArgumentException+集合语义；配置错误=IllegalStateException+向量语义")
    void errorsAreDistinguishable() {
        assertThatThrownBy(() -> KbDevIngestGuard.requireDevCollection("driving_exam_kb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("集合");

        QdrantProperties p = validProperties();
        p.setVectorSize(512);
        assertThatThrownBy(() -> KbDevIngestGuard.requireValidatedVectorConfig(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("向量配置");
    }

    /* ============================== 辅助 ============================== */

    private static QdrantProperties validProperties() {
        QdrantProperties p = new QdrantProperties();
        p.setVectorSize(KbDevIngestGuard.EXPECTED_VECTOR_SIZE);
        p.setDistance(KbDevIngestGuard.EXPECTED_DISTANCE);
        return p;
    }

    /** 把空白/控制字符可视化，便于失败时定位。 */
    private static String escape(String s) {
        if (s == null) {
            return "null";
        }
        return s.replace("\t", "\\t").replace("\n", "\\n");
    }
}
