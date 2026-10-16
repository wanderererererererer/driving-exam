package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 可用试卷（{@code GET /api/student/exam/papers} 的列表元素）。
 *
 * <p><b>考试参数一律来自本对象（读 {@code exam_paper} 字段），后端不写死 100/45/90</b>——
 * 题库扩充后只改数据、不改代码。</p>
 */
@Data
public class PaperVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    private String subjectCode;

    /** 题目数量 */
    private Integer questionCount;

    /** 考试时长（分钟） */
    private Integer duration;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /**
     * 该试卷**是否已有该学员「进行中」的考试**（{@code mock_exam.status = ONGOING}）。
     *
     * <p>有则填那份考试的 id，前端据此显示「继续考试」；无则为 {@code null}。</p>
     *
     * <p><b>用包装类型 {@code Long}</b>：基本类型 {@code long} 遇 null 会序列化成 {@code 0}，
     * 前端无法区分"没有进行中的考试"和"考试 id 是 0"。</p>
     */
    private Long ongoingExamId;
}
