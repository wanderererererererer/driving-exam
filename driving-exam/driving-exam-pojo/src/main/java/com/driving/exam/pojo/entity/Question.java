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
 * 题目实体，对应表 question。
 */
@Data
@TableName("question")
public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 科目：SUBJECT_1 / SUBJECT_4 */
    private String subjectCode;

    /** 知识点 ID */
    private Long knowledgePointId;

    /** 题型：SINGLE / MULTIPLE / JUDGE */
    private String questionType;

    /** 题干 */
    private String content;

    /** 正确答案（如 A、AB、对/错）—— 仅后端使用，不下发前端 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 难度 1~5 */
    private Integer difficulty;

    /** 图片/视频 URL */
    private String mediaUrl;

    /** 状态：1 启用，0 停用 */
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0 未删，1 已删 */
    @TableLogic
    private Integer deleted;
}
