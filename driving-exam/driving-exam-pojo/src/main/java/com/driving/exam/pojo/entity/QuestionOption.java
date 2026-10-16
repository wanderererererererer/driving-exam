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
 * 题目选项实体，对应表 question_option。
 *
 * <p>该表为流水/明细表：<b>无 deleted 逻辑删除、无 update_time</b>，仅 create_time 自动填充。</p>
 */
@Data
@TableName("question_option")
public class QuestionOption implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 题目 ID */
    private Long questionId;

    /** 选项标识：A / B / C / D */
    private String optionKey;

    /** 选项内容 */
    private String optionContent;

    /** 是否正确答案：1 是，0 否 —— 仅后端使用，不下发前端 */
    private Integer isCorrect;

    /** 排序 */
    private Integer sortOrder;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
