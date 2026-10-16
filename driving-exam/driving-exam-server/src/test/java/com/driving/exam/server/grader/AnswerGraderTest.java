package com.driving.exam.server.grader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AnswerGrader 判分逻辑单元测试（JUnit 5 + AssertJ）。
 *
 * <p>AnswerGrader 是无状态纯逻辑类，这里直接 new，<b>不需要启动 Spring 容器</b>。</p>
 */
@DisplayName("AnswerGrader 判分逻辑")
class AnswerGraderTest {

    private final AnswerGrader grader = new AnswerGrader();

    /* ==================== A. 单选 SINGLE ==================== */

    @ParameterizedTest(name = "[{index}] user=\"{0}\", correct=\"{1}\" => {2}")
    @MethodSource("singleCases")
    @DisplayName("A. 单选：正确 / 错误 / 小写 / 前后空格")
    void singleCase(String user, String correct, boolean expected) {
        assertThat(grader.isCorrect("SINGLE", user, correct)).isEqualTo(expected);
    }

    static Stream<Arguments> singleCases() {
        return Stream.of(
                Arguments.of("A", "A", true),    // 1  正确
                Arguments.of("B", "A", false),   // 2  错误
                Arguments.of("a", "A", true),    // 3  小写
                Arguments.of(" A ", "A", true)   // 4  前后空格
        );
    }

    /* ==================== B. 多选 MULTIPLE ==================== */

    @ParameterizedTest(name = "[{index}] user=\"{0}\", correct=\"{1}\" => {2}")
    @MethodSource("multipleCases")
    @DisplayName("B. 多选：全对 / 顺序 / 少选 / 多选 / 分隔符 / 重复")
    void multipleCase(String user, String correct, boolean expected) {
        assertThat(grader.isCorrect("MULTIPLE", user, correct)).isEqualTo(expected);
    }

    static Stream<Arguments> multipleCases() {
        return Stream.of(
                Arguments.of("AB", "AB", true),      // 5  完全正确
                Arguments.of("BA", "AB", true),      // 6  顺序颠倒
                Arguments.of("A", "AB", false),      // 7  少选
                Arguments.of("ABC", "AB", false),    // 8  多选
                Arguments.of("A,B", "AB", true),     // 9  半角逗号
                Arguments.of("A，B", "AB", true),    // 10 全角逗号
                Arguments.of("A B", "AB", true),     // 11 空格分隔
                Arguments.of("A、B", "AB", true),    // 12 顿号
                Arguments.of("ba", "AB", true),      // 13 小写
                Arguments.of("AAB", "AB", true),     // 14 重复字母（归一化去重）
                Arguments.of("AB", "B,A", true)      // 15 标准答案本身带分隔符
        );
    }

    /* ==================== C. 判断 JUDGE ==================== */

    @ParameterizedTest(name = "[{index}] user=\"{0}\", correct=\"{1}\" => {2}")
    @MethodSource("judgeCases")
    @DisplayName("C. 判断：对/错 + 同义映射")
    void judgeCase(String user, String correct, boolean expected) {
        assertThat(grader.isCorrect("JUDGE", user, correct)).isEqualTo(expected);
    }

    static Stream<Arguments> judgeCases() {
        return Stream.of(
                Arguments.of("对", "对", true),     // 16
                Arguments.of("错", "对", false),    // 17
                Arguments.of("T", "对", true),      // 18 T -> 对
                Arguments.of("正确", "对", true),   // 19 正确 -> 对
                Arguments.of("√", "对", true),      // 20 √ -> 对
                Arguments.of("F", "对", false),     // 21 F -> 错
                Arguments.of("错误", "错", true),   // 22 错误 -> 错
                Arguments.of("×", "错", true),      // 23 × -> 错
                Arguments.of("t", "对", true)       // 24 小写 t -> 对
        );
    }

    /* ==================== D. 全角字符（NFKC） ==================== */

    @ParameterizedTest(name = "[{index}] type={0}, user=\"{1}\", correct=\"{2}\" => {3}")
    @MethodSource("fullWidthCases")
    @DisplayName("D. 全角字符经 NFKC 折叠为半角")
    void fullWidthCase(String type, String user, String correct, boolean expected) {
        assertThat(grader.isCorrect(type, user, correct)).isEqualTo(expected);
    }

    static Stream<Arguments> fullWidthCases() {
        return Stream.of(
                Arguments.of("SINGLE", "Ａ", "A", true),      // 25 全角字母
                Arguments.of("MULTIPLE", "ＡＢ", "AB", true)  // 26 全角字母 + 多选
        );
    }

    /* ==================== E. 脏输入容错（只保留 A-Z） ==================== */

    @ParameterizedTest(name = "[{index}] user=\"{0}\" => {1}")
    @MethodSource("dirtyCases")
    @DisplayName("E. 脏输入：非字母字符被剔除")
    void dirtyCase(String user, boolean expected) {
        assertThat(grader.isCorrect("MULTIPLE", user, "AB")).isEqualTo(expected);
    }

    static Stream<Arguments> dirtyCases() {
        return Stream.of(
                Arguments.of("AB!", true),   // 27 感叹号剔除
                Arguments.of("AB1", true)    // 28 数字剔除
        );
    }

    /* ==================== F. 边界与异常 ==================== */

    @Test
    @DisplayName("29. userAnswer 为 null => false")
    void nullUserAnswer() {
        assertThat(grader.isCorrect("SINGLE", null, "A")).isFalse();
    }

    @Test
    @DisplayName("30. userAnswer 为空串 => false")
    void emptyUserAnswer() {
        assertThat(grader.isCorrect("SINGLE", "", "A")).isFalse();
    }

    @Test
    @DisplayName("31. userAnswer 全空白 => false")
    void blankUserAnswer() {
        assertThat(grader.isCorrect("SINGLE", "   ", "A")).isFalse();
    }

    @Test
    @DisplayName("32. userAnswer 只有分隔符 => false")
    void separatorOnlyUserAnswer() {
        assertThat(grader.isCorrect("MULTIPLE", ",,", "AB")).isFalse();
    }

    @Test
    @DisplayName("33. 用户与标准答案都为空 => false（不得误判为正确）")
    void bothEmpty() {
        assertThat(grader.isCorrect("MULTIPLE", "", "")).isFalse();
    }

    @Test
    @DisplayName("34. correctAnswer 为 null => false（脏数据判错，不抛异常）")
    void nullCorrectAnswer() {
        assertThat(grader.isCorrect("SINGLE", "A", null)).isFalse();
    }

    @Test
    @DisplayName("35. 判断题答案全空白 => false")
    void blankJudgeAnswer() {
        assertThat(grader.isCorrect("JUDGE", "  ", "对")).isFalse();
    }

    @Test
    @DisplayName("36. 未知题型 => 抛 IllegalArgumentException")
    void unknownQuestionType() {
        assertThatThrownBy(() -> grader.isCorrect("FOO", "A", "A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知题型");
    }

    @Test
    @DisplayName("37. 题型为 null => 抛 IllegalArgumentException")
    void nullQuestionType() {
        assertThatThrownBy(() -> grader.isCorrect(null, "A", "A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("38. 题型兼容小写 single => 正常判分")
    void lowerCaseQuestionType() {
        assertThat(grader.isCorrect("single", "a", "A")).isTrue();
    }

    /* ==================== E. isGradeable 可判分性（新增） ==================== */

    @Test
    @DisplayName("39. isGradeable：SINGLE / MULTIPLE / JUDGE 均可判分")
    void gradeableKnownTypes() {
        assertThat(grader.isGradeable("SINGLE")).isTrue();
        assertThat(grader.isGradeable("MULTIPLE")).isTrue();
        assertThat(grader.isGradeable("JUDGE")).isTrue();
    }

    @Test
    @DisplayName("40. isGradeable：大小写不敏感、忽略首尾空白")
    void gradeableCaseAndTrim() {
        assertThat(grader.isGradeable("single")).isTrue();
        assertThat(grader.isGradeable("  Multiple  ")).isTrue();
        assertThat(grader.isGradeable("\tjudge\n")).isTrue();
    }

    @Test
    @DisplayName("41. isGradeable：未知 / 空 / null 题型 => false")
    void gradeableInvalidTypes() {
        assertThat(grader.isGradeable("FOO")).isFalse();
        assertThat(grader.isGradeable("")).isFalse();
        assertThat(grader.isGradeable("   ")).isFalse();
        assertThat(grader.isGradeable(null)).isFalse();
    }
}
