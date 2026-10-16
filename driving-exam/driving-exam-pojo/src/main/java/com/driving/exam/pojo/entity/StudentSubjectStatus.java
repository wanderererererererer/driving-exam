package com.driving.exam.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学员科目进度实体，对应表 {@code student_subject_status}。
 *
 * <p><b>无 {@code deleted} 列</b> → 不加 {@code @TableLogic}；该表靠唯一键
 * {@code uk_student_subject(student_id, subject_code)} + upsert 维护，不需要逻辑删除。</p>
 *
 * <p>模拟考试交卷后只更新 {@code mock_pass_count} 与 {@code last_mock_score}，
 * 其余列（学习状态、AI 建议、教练确认等）由后续模块维护。</p>
 */
@Data
@TableName("student_subject_status")
public class StudentSubjectStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 学员 ID */
    private Long studentId;

    /** 科目：SUBJECT_1 ~ SUBJECT_4 */
    private String subjectCode;

    /** 状态：NOT_STARTED / LEARNING / READY_FOR_EXAM / EXAM_PASSED / EXAM_FAILED */
    private String status;

    /** 模拟考连续及格次数 */
    private Integer mockPassCount;

    /** 最近一次模拟考成绩 */
    private Integer lastMockScore;

    /** 题库完成率百分比（DECIMAL(5,2)） */
    private BigDecimal studyProgress;

    /** 系统建议：READY / NEED_MORE / NEED_HELP */
    private String examReadySuggest;

    /** AI 生成的建议文本 */
    private String aiSuggest;

    /** AI 建议生成时间 */
    private LocalDateTime aiSuggestTime;

    /** 教练确认：CONFIRMED_READY / NOT_READY / NEED_HELP */
    private String coachConfirmStatus;

    /** 教练备注 */
    private String coachRemark;

    /** 正式考试结果：PASS / FAIL */
    private String examResult;

    /** 正式考试日期 */
    private LocalDate examDate;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
