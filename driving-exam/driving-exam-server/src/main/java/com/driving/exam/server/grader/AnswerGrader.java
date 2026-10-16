package com.driving.exam.server.grader;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * 判分核心（纯逻辑，无状态）。
 *
 * <p>被 3.7 的 PracticeService 注入使用；因为不依赖任何外部资源，
 * 单元测试可以直接 {@code new AnswerGrader()}，无需启动 Spring 容器。</p>
 *
 * <p>判分规则：单选/多选按"归一化后相等"，多选另做"集合相等"（去重 + 排序）；
 * 判断题额外做同义映射（T/√/正确… → 对）。<b>全对才算对，不设部分分。</b></p>
 */
@Component
public class AnswerGrader {

    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_MULTIPLE = "MULTIPLE";
    public static final String TYPE_JUDGE = "JUDGE";

    /** 判断题归一化后的标准写法 */
    private static final String JUDGE_TRUE = "对";
    private static final String JUDGE_FALSE = "错";

    /** 判断题"正确"的同义写法（已按 NFKC + 大写预处理过） */
    private static final Set<String> TRUE_WORDS =
            Set.of("对", "正确", "是", "T", "TRUE", "Y", "YES", "√", "✓");

    /** 判断题"错误"的同义写法 */
    private static final Set<String> FALSE_WORDS =
            Set.of("错", "错误", "否", "F", "FALSE", "N", "NO", "×", "✗", "X");

    /**
     * 判定作答是否正确。
     *
     * @param questionType 题型：SINGLE / MULTIPLE / JUDGE（大小写不敏感）
     * @param userAnswer   用户答案
     * @param correctAnswer 标准答案
     * @return 是否答对；用户未作答或标准答案缺失一律返回 false
     * @throws IllegalArgumentException 题型无法识别
     */
    public boolean isCorrect(String questionType, String userAnswer, String correctAnswer) {
        String type = normalizeType(questionType);
        String user = normalize(userAnswer, type);
        String correct = normalize(correctAnswer, type);

        // 任一侧为空（未作答 / 脏数据 / 无法识别）都判错，避免"空 vs 空"被误判为正确
        if (user.isEmpty() || correct.isEmpty()) {
            return false;
        }
        return user.equals(correct);
    }

    /**
     * 题型是否**可判分**（{@code SINGLE} / {@code MULTIPLE} / {@code JUDGE}；大小写不敏感、忽略首尾空白）。
     *
     * <p>供考试链路（开考前校验 / 单题判题 / 详情组装 / 最终结算）**统一**判断用。
     * <b>不改变</b> {@link #isCorrect} / {@link #normalize} 的既有契约（未知题型时 {@code isCorrect} 仍抛、
     * {@code normalize} 仍走原逻辑）。</p>
     *
     * @param questionType 题型；{@code null} 或未知 → {@code false}
     */
    public boolean isGradeable(String questionType) {
        if (questionType == null) {
            return false;
        }
        String type = questionType.trim().toUpperCase(Locale.ROOT);
        return TYPE_SINGLE.equals(type) || TYPE_MULTIPLE.equals(type) || TYPE_JUDGE.equals(type);
    }

    /** 题型归一化并校验合法性 */
    private String normalizeType(String questionType) {
        String type = questionType == null ? "" : questionType.trim().toUpperCase(Locale.ROOT);
        if (!TYPE_SINGLE.equals(type) && !TYPE_MULTIPLE.equals(type) && !TYPE_JUDGE.equals(type)) {
            throw new IllegalArgumentException("未知题型: " + questionType);
        }
        return type;
    }

    /**
     * 答案归一化。
     *
     * <p><b>第一步为什么用 NFKC：</b>NFKC 归一化会把"全角 / 兼容字符"折叠成标准半角形式，
     * 例如全角字母 Ａ→A、全角逗号 ，→ ,。这样无论前端提交的是全角还是半角，都能落到同一形态再比对，
     * 省掉一堆针对全角的边界特判。它对普通 ASCII 与中文汉字无副作用，成本只是一次字符扫描，极低。</p>
     *
     * @return 归一化后的答案；无法识别或为空时返回空串
     */
    public String normalize(String answer, String questionType) {
        if (answer == null) {
            return "";
        }
        String s = Normalizer.normalize(answer, Normalizer.Form.NFKC)
                .trim()
                .toUpperCase(Locale.ROOT);
        if (s.isEmpty()) {
            return "";
        }
        if (TYPE_JUDGE.equals(questionType)) {
            return normalizeJudge(s);
        }
        if (TYPE_MULTIPLE.equals(questionType)) {
            return normalizeMultiple(s);
        }
        return keepLetters(s);
    }

    /**
     * 判断题归一化：同义写法映射为"对"/"错"；无法识别返回空串（交由调用方判错）。
     */
    private String normalizeJudge(String s) {
        if (TRUE_WORDS.contains(s)) {
            return JUDGE_TRUE;
        }
        if (FALSE_WORDS.contains(s)) {
            return JUDGE_FALSE;
        }
        return "";
    }

    /**
     * 多选归一化：只保留 A-Z 字母 → 去重 → 排序。
     * 使 "BA"、"A,B"、"A B"、"ＡＢ"、"AAB" 都归一为 "AB"。
     */
    private String normalizeMultiple(String s) {
        String letters = keepLetters(s);
        if (letters.isEmpty()) {
            return "";
        }
        char[] arr = letters.toCharArray();
        java.util.Arrays.sort(arr);

        StringBuilder sb = new StringBuilder(arr.length);
        char prev = 0;
        for (char c : arr) {
            if (c != prev) {
                sb.append(c);
            }
            prev = c;
        }
        return sb.toString();
    }

    /**
     * 只保留 A-Z 字母（剔除数字、标点、分隔符等一切其它字符）。
     * 这样 "AB!"、"AB1" 也能正确归一为 "AB"。
     */
    private String keepLetters(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
