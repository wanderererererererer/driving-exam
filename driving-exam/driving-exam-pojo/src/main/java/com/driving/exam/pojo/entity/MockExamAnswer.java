package com.driving.exam.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 考试暂存答案实体，对应表 {@code mock_exam_answer}。
 *
 * <p><b>用途：断点续考</b>。考试进行中（{@code mock_exam.status = ONGOING}）逐题暂存用户答案；
 * 交卷判分后答案会写入 {@code answer_record(source=EXAM)}，本表只是考试期间的草稿。</p>
 *
 * <p>唯一键 {@code uk_exam_question(exam_id, question_id)} 保证"一场考试一题一条"，
 * 暂存走 {@code INSERT ... ON DUPLICATE KEY UPDATE}（见 Mapper）。</p>
 *
 * <p>非主数据表：<b>无 {@code deleted}、不加 {@code @TableLogic}</b>。</p>
 */
@Data
@TableName("mock_exam_answer")
public class MockExamAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 模拟考试 ID（mock_exam.id） */
    private Long examId;

    /** 题目 ID */
    private Long questionId;

    /** 用户答案（暂存，未判分） */
    private String userAnswer;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
