package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 考试中「单题作答」入参。
 *
 * <p>两处复用：</p>
 * <ul>
 *   <li>{@code POST /api/student/exam/{examId}/answer} 的请求体（考试中暂存单题答案）；</li>
 *   <li>{@code POST /api/student/exam/{examId}/submit} 请求体里 {@code answers} 列表的元素（交卷时一次性提交）。</li>
 * </ul>
 *
 * <p><b>只带答案、不带对错</b>——考试中不判分，判分在交卷时统一做。</p>
 */
@Data
public class ExamAnswerDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目 ID */
    @NotNull(message = "题目 ID 不能为空")
    private Long questionId;

    /** 用户答案：单选 'A' / 多选 'AB' / 判断 '对' 或 '错' */
    @NotBlank(message = "答案不能为空")
    private String userAnswer;
}
