package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 考试已答条目（答题卡 / 已答回显用）。
 *
 * <p>{@code ExamVO.answeredMap} 是同样信息的 Map 形态；本类提供 List 形态，
 * 供需要有序遍历的场景使用。</p>
 */
@Data
public class ExamAnswerItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;

    private String userAnswer;
}
