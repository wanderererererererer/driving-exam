package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目选项（展示用）。
 *
 * <p><b>刻意不含 isCorrect</b>，防止答案泄露到前端。</p>
 */
@Data
public class QuestionOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 选项标识：A / B / C / D */
    private String optionKey;

    /** 选项内容 */
    private String optionContent;
}
