package com.driving.exam.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约实体，对应表 {@code appointment}。
 *
 * <p><b>只服务科二 / 科三</b>（{@code subject_code} 取 {@code SUBJECT_2} / {@code SUBJECT_3}）：
 * 学员向<b>自己已绑定且绑定有效</b>的教练发起练车预约，教练审批（通过 / 拒绝），完成后标记。</p>
 *
 * <p><b>主数据表</b>：有 {@code deleted} → 加 {@link TableLogic}（逻辑删除）；
 * {@code create_time} / {@code update_time} 由 {@code MyMetaObjectHandler} 自动填充。</p>
 *
 * <p><b>{@code status} 在本实体里是 {@code String}</b>（实体层只映射数据库列，与
 * {@code MockExam.status} / {@code StudentSubjectStatus.status} 一致）；
 * 取值集合见枚举 {@link com.driving.exam.pojo.enums.AppointmentStatus}，对外 VO 使用该枚举。
 * 数据库列已有默认值 {@code 'PENDING'}，创建时由 Service 显式赋值。</p>
 *
 * <p><b>{@code time_slot}</b> 在库里是自由文本（{@code varchar(50)}，<b>无字典表</b>）；
 * 本项目<b>用 DTO 的 {@code @Pattern} 把它约束为一组固定时段</b>（如
 * {@code 08:00-10:00}），以保证"时段冲突"判定可靠。</p>
 */
@Data
@TableName("appointment")
public class Appointment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 学员 ID（发起人） */
    private Long studentId;

    /** 教练 ID（被预约人，必须是该学员已绑定且 status=1 的教练） */
    private Long coachId;

    /** 科目：SUBJECT_2 / SUBJECT_3 */
    private String subjectCode;

    /** 预约日期（只到"日"，不含时间；序列化格式 yyyy-MM-dd） */
    private LocalDate appointmentDate;

    /** 时间段（固定取值之一，如 08:00-10:00） */
    private String timeSlot;

    /** 状态：PENDING / CONFIRMED / CANCELLED / REJECTED / COMPLETED（见 AppointmentStatus） */
    private String status;

    /** 学员备注 */
    private String remark;

    /** 教练备注（**拒绝原因**也记在这里） */
    private String coachRemark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
