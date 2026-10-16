package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成绩报告（{@code POST /exam/{examId}/submit} 与 {@code GET /exam/{examId}/report} 的返回）。
 */
@Data
public class ExamReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long examId;

    /** 得分 */
    private Integer score;

    /** 满分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /**
     * 是否及格。
     *
     * <p><b>用包装类型 {@code Boolean}</b>：Lombok 对包装类型生成 {@code getIsPass()}，
     * Jackson 序列化出的 JSON 键才是 {@code isPass}；若用基本类型会生成 {@code isPass()}，
     * JSON 键变成 {@code pass}，与前端约定不符。</p>
     */
    private Boolean isPass;

    /** 答对题数 */
    private Integer correctCount;

    /** 答错题数（已作答且错误） */
    private Integer wrongCount;

    /** 未作答题数 */
    private Integer unansweredCount;

    /** 逐题结果（**以试卷题目为主线**，未作答题也在其中） */
    private List<ExamReportItemVO> items;
}
