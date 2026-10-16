package com.driving.exam.pojo.vo;

import com.driving.exam.pojo.enums.AppointmentStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约视图（学员端与教练端<b>共用</b>）。
 *
 * <p><b>为什么两侧字段都带</b>：学员端要显示"教练姓名 + 教练电话"，教练端要显示"学员姓名 + 学员电话"，
 * 用同一个 VO 服务两端，前端不必维护两个结构，后端也只维护一份。</p>
 *
 * <p><b>姓名 / 电话由 Service 填充</b>：分页查出 {@code appointment} 后，收集两侧 ID，
 * <b>一次 {@code IN} 查 {@code sys_user}</b> 后在内存映射——<b>绝不逐条查</b>（防 N+1）。
 * 用不到的一侧可为 {@code null}。</p>
 *
 * <p>{@code status} 用枚举 {@link AppointmentStatus}，Jackson 按 {@code name()} 输出 {@code "PENDING"} 等。
 * <b>不返回</b> {@code deleted}（内部逻辑删除字段，对外无意义）。</p>
 */
@Data
public class AppointmentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 预约 ID */
    private Long id;

    /** 学员 ID */
    private Long studentId;

    /** 学员姓名（教练端显示用） */
    private String studentName;

    /** 学员电话（教练端"联系学员"用） */
    private String studentPhone;

    /** 教练 ID */
    private Long coachId;

    /** 教练姓名（学员端显示用） */
    private String coachName;

    /** 教练电话（学员端"联系教练"用） */
    private String coachPhone;

    /** 科目：SUBJECT_2 / SUBJECT_3 */
    private String subjectCode;

    /** 预约日期（序列化 yyyy-MM-dd） */
    private LocalDate appointmentDate;

    /** 时间段 */
    private String timeSlot;

    /** 状态（枚举；前端按枚举名做中文映射） */
    private AppointmentStatus status;

    /** 学员备注 */
    private String remark;

    /** 教练备注（**拒绝原因**也记在这里） */
    private String coachRemark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 最近更新时间（学员可据此看到教练何时操作过） */
    private LocalDateTime updateTime;
}
