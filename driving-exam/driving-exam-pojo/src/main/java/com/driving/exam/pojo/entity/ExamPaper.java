package com.driving.exam.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 试卷实体，对应表 {@code exam_paper}。
 *
 * <p>主数据表：有 {@code deleted}（逻辑删除）、{@code create_time} / {@code update_time}。</p>
 *
 * <p><b>考试参数一律读本表字段</b>（{@code duration} 分钟 / {@code question_count} /
 * {@code total_score} / {@code pass_score}），代码里**不写死** 100/45/90 —— 题库扩充后只改数据。</p>
 */
@Data
@TableName("exam_paper")
public class ExamPaper implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 科目：SUBJECT_1 / SUBJECT_4 */
    private String subjectCode;

    /** 试卷标题 */
    private String title;

    /** 类型：MOCK / CHAPTER（本期只用 MOCK） */
    private String paperType;

    /** 总分 */
    private Integer totalScore;

    /** 及格分 */
    private Integer passScore;

    /** 考试时长（**分钟**） */
    private Integer duration;

    /** 题目数量 */
    private Integer questionCount;

    /** 状态：1 启用，0 停用 */
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
