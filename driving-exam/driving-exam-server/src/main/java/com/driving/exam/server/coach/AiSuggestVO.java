package com.driving.exam.server.coach;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 教练端 · 学员 AI 学习建议（{@code POST /api/coach/students/{id}/ai-suggest}）。
 *
 * <p><b>来源标识</b>：{@link #source} 只会是 {@code AI} 或 {@code RULE}。
 * AI 调用失败时使用规则建议，此时 {@code aiSuccess=false}、{@code degraded=true}，
 * 且不会把规则文本伪装成模型生成结果。</p>
 *
 * <p><b>持久化</b>：规则建议写入 {@code student_subject_status.exam_ready_suggest}；
 * AI 成功文本写入 {@code ai_suggest} 与 {@code ai_suggest_time}。AI 失败时
 * {@code ai_suggest}/{@code ai_suggest_time} 置空，避免旧模型结果被误认为本次成功生成。</p>
 */
@Data
public class AiSuggestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_RULE = "RULE";

    /** 学员用户 ID（由已验证绑定记录反查） */
    private Long studentId;

    /** 科目：SUBJECT_1 ~ SUBJECT_4 */
    private String subjectCode;

    /** 规则/系统建议：READY / NEED_MORE / NEED_HELP；历史异常行可能为 null */
    private String examReadySuggest;

    /** 展示给教练的建议文本；读取历史规则建议时为 null（当时只保存了等级） */
    private String suggestion;

    /** 来源：AI / RULE */
    private String source;

    /** 本次是否由 AI 成功生成 */
    private Boolean aiSuccess;

    /** 是否降级为规则建议 */
    private Boolean degraded;

    /** 降级原因：AI_TIMEOUT / AI_CALL_FAILED / AI_QUOTA_EXCEEDED；未降级为 null */
    private String degradedReason;

    /** AI 建议生成时间；规则建议为 null */
    private LocalDateTime aiSuggestTime;

    /** 当前教练确认状态：CONFIRMED_READY / NOT_READY / NEED_HELP；未确认为 null */
    private String coachConfirmStatus;
}
