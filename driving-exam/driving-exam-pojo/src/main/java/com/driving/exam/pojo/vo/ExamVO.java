package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 考试视图（{@code POST /exam/start} 与 {@code GET /exam/{examId}} 断点续考的返回）。
 *
 * <p><b>防作弊</b>：{@code questions} 复用刷题模块的 {@link QuestionVO}，**不含 answer / analysis**；
 * 答案与解析只在成绩报告里给。</p>
 */
@Data
public class ExamVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long examId;

    private String paperTitle;

    /**
     * 考试状态：ONGOING / SUBMITTED / TIMEOUT。
     *
     * <p>断点续考时前端据此分流：{@code ONGOING} → 考试页；{@code SUBMITTED/TIMEOUT} → 报告页。</p>
     */
    private String status;

    /**
     * 剩余秒数（<b>由后端计算</b>：{@code duration*60 − (now − start_time)}）。
     *
     * <p>前端只用它**初始化**倒计时；刷新页面重新拉取即可校准，不受本地计时漂移或改系统时间影响。
     * 已交卷/超时时可为 0 或负数，前端应以 {@link #status} 为准。</p>
     */
    private Long remainingSeconds;

    /** 试卷题目（不含答案，防作弊） */
    private List<QuestionVO> questions;

    /** 已作答题目的答案：questionId → userAnswer（断点续考时恢复答题卡与选项回显） */
    private Map<Long, String> answeredMap;

    /**
     * 已作答题目的**正误**：questionId → isCorrect（仅含【已作答】的题；未作答不出现在此）。
     *
     * <p>用于**刷新/重进后恢复答题卡的正误标记**——由服务端用 {@code AnswerGrader} 从
     * "已保存答案 + 题目标准答案"重新计算，<b>不下发标准答案 / 解析</b>。</p>
     */
    private Map<Long, Boolean> correctMap;
}
