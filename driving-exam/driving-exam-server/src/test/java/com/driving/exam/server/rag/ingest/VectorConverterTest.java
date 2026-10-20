package com.driving.exam.server.rag.ingest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link VectorConverter} 单元测试（纯逻辑，无 Spring / 无数据库 / 无外部服务）。
 */
@DisplayName("R2.4 向量转换与校验")
class VectorConverterTest {

    private final VectorConverter converter = new VectorConverter();

    /* ==================== 正常转换 ==================== */

    @Test
    @DisplayName("正常向量：值、顺序、长度一致")
    void normalConversion() {
        double[] input = {1.0, 2.5, -3.0, 0.0};
        float[] out = converter.toFloatVector(input, 4);

        assertThat(out).hasSize(4);
        assertThat(out).containsExactly(1.0f, 2.5f, -3.0f, 0.0f);
    }

    @Test
    @DisplayName("负数、零、小数转换正确且有序")
    void signsZeroFraction() {
        float[] out = converter.toFloatVector(new double[]{-0.5, 0.0, 0.25}, 3);
        assertThat(out).containsExactly(-0.5f, 0.0f, 0.25f);
    }

    @Test
    @DisplayName("不修改调用方传入的数组")
    void doesNotMutateInput() {
        double[] input = {1.0, -2.0, 3.5};
        double[] before = input.clone();

        converter.toFloatVector(input, 3);

        assertThat(input).containsExactly(before);
    }

    /* ==================== 输入结构非法 ==================== */

    @Test
    @DisplayName("null 向量 → IllegalArgumentException")
    void nullVectorRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(null, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空数组 → IllegalArgumentException")
    void emptyVectorRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // 即使期望维度合法，空数组本身也非法
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{}, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("期望维度 ≤ 0 → IllegalArgumentException")
    void invalidExpectedDimensionRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{1.0}, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{1.0}, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("向量长度与期望维度不符 → IllegalArgumentException")
    void dimensionMismatchRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{1.0, 2.0}, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* ==================== 分量非法 ==================== */

    @Test
    @DisplayName("含 NaN → IllegalArgumentException")
    void nanRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{1.0, Double.NaN}, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("含正无穷 → IllegalArgumentException")
    void positiveInfinityRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{Double.POSITIVE_INFINITY, 1.0}, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("含负无穷 → IllegalArgumentException")
    void negativeInfinityRejected() {
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{Double.NEGATIVE_INFINITY, 1.0}, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("有限 double 转 float 溢出为 Infinity → IllegalArgumentException")
    void floatOverflowRejected() {
        // Double.MAX_VALUE 是有限值，但 (float) 转换后为 Infinity
        assertThatThrownBy(() -> converter.toFloatVector(new double[]{Double.MAX_VALUE, 1.0}, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("非法向量不产生部分成功：异常即抛出、无返回值")
    void noPartialResult() {
        double[] input = {1.0, Double.NaN, 3.0};
        assertThatThrownBy(() -> converter.toFloatVector(input, 3))
                .isInstanceOf(IllegalArgumentException.class);
        // 异常路径下调用方拿不到任何 float[]（无部分结果返回）
    }
}
