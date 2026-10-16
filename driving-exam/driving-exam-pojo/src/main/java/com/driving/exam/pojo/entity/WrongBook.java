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
 * 错题本实体，对应表 wrong_book。
 *
 * <p>唯一键 uk_user_question(user_id, question_id) 保证每人每题一条。</p>
 */
@Data
@TableName("wrong_book")
public class WrongBook implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 科目 */
    private String subjectCode;

    /** 题目 ID */
    private Long questionId;

    /** 错误次数 */
    private Integer wrongCount;

    /** 最近错误时间 */
    private LocalDateTime lastWrongTime;

    /** 是否已掌握：1 是，0 否 */
    private Integer mastered;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
