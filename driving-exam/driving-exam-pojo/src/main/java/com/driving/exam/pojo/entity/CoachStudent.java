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
 * 教练-学员绑定关系实体，对应表 {@code coach_student}。
 *
 * <p>唯一键 {@code uk_coach_student(coach_id, student_id)} 保证"一对教练-学员"只一条。</p>
 *
 * <p><b>★ 该表没有 {@code deleted} 列 → 绝对不要加 {@code @TableLogic}</b>。
 * 若误加，MyBatis-Plus 会在 SQL 里拼出 {@code deleted = 0}，直接报
 * {@code Unknown column 'deleted'}。本表用 {@code status} 表达"有效 / 解除"：
 * <b>1 = 有效，0 = 解绑</b>（对应 {@code doc/data-conventions.md} §7 的通用状态位）。</p>
 *
 * <p><b>{@code bind_time}</b> 是"绑定时间"，语义等同创建时间；数据库列已有默认值
 * {@code CURRENT_TIMESTAMP}，因此<b>不交由 {@code MyMetaObjectHandler} 填充</b>
 * （该处理器按字段名只填充 {@code createTime} / {@code updateTime}），
 * 插入时保持为 {@code null}，由数据库默认值写入。</p>
 */
@Data
@TableName("coach_student")
public class CoachStudent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 教练 ID */
    private Long coachId;

    /** 学员 ID */
    private Long studentId;

    /** 绑定时间（依赖数据库默认值 CURRENT_TIMESTAMP，插入时不需要赋值） */
    private LocalDateTime bindTime;

    /** 状态：1 有效 / 0 解除（本表**无** deleted 列，解绑即为 0） */
    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
