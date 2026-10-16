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
 * 答题记录实体，对应表 answer_record。
 *
 * <p>流水表：<b>无 deleted、无 update_time</b>，仅 create_time 自动填充。
 * answer_time 依赖数据库默认值 CURRENT_TIMESTAMP（实体为 null 时该列不参与 INSERT）。</p>
 */
@Data
@TableName("answer_record")
public class AnswerRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 科目 */
    private String subjectCode;

    /** 题目 ID */
    private Long questionId;

    /** 用户答案（归一化后） */
    private String userAnswer;

    /** 是否正确：1 是，0 否 */
    private Integer isCorrect;

    /** 来源：PRACTICE / EXAM */
    private String source;

    /** 模拟考试 ID（练习时为空） */
    private Long examId;

    /** 答题耗时（秒） */
    private Integer duration;

    /** 答题时间（依赖数据库默认值） */
    private LocalDateTime answerTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
