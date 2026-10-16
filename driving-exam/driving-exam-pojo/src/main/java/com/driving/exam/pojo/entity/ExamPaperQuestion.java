package com.driving.exam.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 试卷题目关联实体，对应表 {@code exam_paper_question}。
 *
 * <p>纯关联表：<b>无 deleted、无 create_time / update_time</b>。
 * 唯一键 {@code uk_paper_question(paper_id, question_id)} 保证一份卷子一题一条。</p>
 *
 * <p><b>每题分值在 {@link #score}</b>（不是全局常量）：交卷判分时按此题的分累加。</p>
 */
@Data
@TableName("exam_paper_question")
public class ExamPaperQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 试卷 ID */
    private Long paperId;

    /** 题目 ID */
    private Long questionId;

    /** 本题分值 */
    private Integer score;

    /** 排序（试卷内题目顺序） */
    private Integer sortOrder;
}
