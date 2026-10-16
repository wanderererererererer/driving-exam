package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 考试历史条目（{@code GET /api/student/exam/history} 的列表元素）。
 *
 * <p>只含已结束（{@code SUBMITTED} / {@code TIMEOUT}）的考试。</p>
 */
@Data
public class ExamRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 考试 ID（mock_exam.id） */
    private Long examId;

    private String paperTitle;

    /** 得分 */
    private Integer score;

    /** 是否及格（包装类型，理由同 {@link ExamReportVO#getIsPass()}） */
    private Boolean isPass;

    /** 交卷时间（JacksonConfig 已全局格式化为 yyyy-MM-dd HH:mm:ss） */
    private LocalDateTime submitTime;

    /** 实际用时（秒） */
    private Integer duration;
}
