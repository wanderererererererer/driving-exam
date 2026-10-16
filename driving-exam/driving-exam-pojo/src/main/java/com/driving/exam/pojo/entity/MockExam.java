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
 * 模拟考试实例实体，对应表 {@code mock_exam}。
 *
 * <p>流水表：<b>无 {@code deleted}、无 {@code update_time}</b>，仅 {@code create_time} 自动填充。</p>
 *
 * <p>注意：<b>{@code start_time} 无数据库默认值</b>（NOT NULL），插入时**必须显式赋值**。</p>
 */
@Data
@TableName("mock_exam")
public class MockExam implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 科目 */
    private String subjectCode;

    /** 试卷 ID */
    private Long paperId;

    /** 开始时间（**插入时必须赋值**） */
    private LocalDateTime startTime;

    /** 交卷时间（进行中为空） */
    private LocalDateTime submitTime;

    /** 实际用时（秒） */
    private Integer duration;

    /** 得分 */
    private Integer score;

    /** 满分（开考时从试卷复制） */
    private Integer totalScore;

    /** 及格分（开考时从试卷复制） */
    private Integer passScore;

    /** 状态：ONGOING / SUBMITTED / TIMEOUT */
    private String status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
