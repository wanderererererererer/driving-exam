package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 单题判题入参（{@code POST /api/student/exam/{examId}/judge}）。
 *
 * <p>只传题目 ID；判题所用答案一律取 **服务器已保存** 的答案（见 Service）。</p>
 */
@Data
public class ExamJudgeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目 ID */
    @NotNull(message = "题目 ID 不能为空")
    private Long questionId;
}
