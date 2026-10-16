package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 问答请求入参。
 */
@Data
public class AiChatRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户问题。
     *
     * <p>{@code @Size(max = 500)} 是**成本控制**：TEXT 列能存 64KB，
     * 但每多一个字都要付 token 费用，且过长的输入也说明问题没问清楚。</p>
     */
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过 500 字")
    private String question;

    /** 科目：SUBJECT_1 / SUBJECT_4；不传表示通用提问（仅用于注入 Prompt 上下文） */
    private String subjectCode;

    /** 会话 ID：前端生成的 UUID，用于把同一轮对话聚合；不传则落库为 NULL */
    private String sessionId;
}
