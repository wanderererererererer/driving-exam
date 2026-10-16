package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交作答入参（刷题 / 错题重练共用）。
 *
 * <p><b>刻意不含 source 字段</b>：作答来源（普通练习 / 错题重练）由**调用的接口**决定，
 * 而不是由请求体携带——避免前端伪造来源去影响 mastered 等副作用。
 * 服务端据此写入 {@code answer_record.source}。</p>
 */
@Data
public class AnswerSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目 ID */
    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /** 用户答案（单选 "A"、多选 "AB"、判断 "对"/"错"） */
    @NotBlank(message = "答案不能为空")
    private String userAnswer;

    /** 答题耗时（秒），可空 */
    private Integer duration;
}
