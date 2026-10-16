package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 作答判分结果。
 *
 * <p>正确答案与解析<b>只在此处下发</b>，拉题接口不返回。</p>
 */
@Data
public class AnswerResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;

    /** 是否答对 */
    private Boolean isCorrect;

    /** 用户答案（归一化后的值） */
    private String userAnswer;

    /** 正确答案 */
    private String correctAnswer;

    /** 解析 */
    private String analysis;
}
