package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 开始考试入参（{@code POST /api/student/exam/start}）。
 */
@Data
public class StartExamDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 试卷 ID */
    @NotNull(message = "试卷 ID 不能为空")
    private Long paperId;
}
