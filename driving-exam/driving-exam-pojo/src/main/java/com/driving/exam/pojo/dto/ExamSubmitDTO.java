package com.driving.exam.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 交卷入参（{@code POST /api/student/exam/{examId}/submit}）。
 *
 * <p><b>examId 从路径取</b>，不放请求体（避免"路径/请求体两个来源"不一致）。</p>
 *
 * <p>{@code answers} <b>可为空</b>：为空表示本次交卷不携带答案，服务端改为从
 * {@code mock_exam_answer} 草稿表读取已暂存的答案（断点续考场景）。
 * 若非空则以请求体为准（请求体优先），并会合并进草稿表。</p>
 */
@Data
public class ExamSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交卷时提交的全部已答题目；为空则从暂存表读取 */
    private List<ExamAnswerDTO> answers;
}
