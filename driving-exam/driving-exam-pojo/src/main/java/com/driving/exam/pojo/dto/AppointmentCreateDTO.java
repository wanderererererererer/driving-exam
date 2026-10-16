package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 创建预约入参（{@code POST /api/student/appointment}）。
 *
 * <p><b>科目</b>：本模块只服务科二 / 科三，故 {@code subjectCode} 用 {@code @Pattern} 限定为
 * {@code SUBJECT_2} 或 {@code SUBJECT_3}。</p>
 *
 * <p><b>时段</b>：库里 {@code time_slot} 是自由文本、<b>无字典表</b>；这里用 {@code @Pattern}
 * 把它约束为<b>固定四时段</b>。若不加约束，{@code 8:00-10:00} 与 {@code 08:00-10:00} 会被判成
 * 两个不同时段，导致"时段冲突"（3002）形同虚设。</p>
 *
 * <p><b>日期</b>：{@code @FutureOrPresent} 表示"今天或将来"——<b>今天可以通过</b>（学员允许当天约当天），
 * 只有早于今天的日期才被拒绝。</p>
 *
 * <p>注：{@code @Pattern} 是<b>整串匹配</b>（内部用 {@code Matcher.matches()}），故正则无需写 {@code ^...$}；
 * 正则中的 {@code -} 与 {@code :} 在字符类之外均为字面量，无需转义。</p>
 */
@Data
public class AppointmentCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教练 ID（必须是该学员已绑定且 status=1 的教练） */
    @NotNull(message = "教练ID不能为空")
    private Long coachId;

    /** 科目：SUBJECT_2 / SUBJECT_3 */
    @NotBlank(message = "科目不能为空")
    @Pattern(regexp = "SUBJECT_2|SUBJECT_3", message = "科目只能是 SUBJECT_2 或 SUBJECT_3")
    private String subjectCode;

    /** 预约日期（今天或将来） */
    @NotNull(message = "预约日期不能为空")
    @FutureOrPresent(message = "不能预约过去的日期")
    private LocalDate appointmentDate;

    /** 时间段（固定四选一） */
    @NotBlank(message = "时间段不能为空")
    @Pattern(regexp = "08:00-10:00|10:00-12:00|14:00-16:00|16:00-18:00",
            message = "时间段不在可选范围内")
    private String timeSlot;

    /** 学员备注（对应 varchar(255)），可空 */
    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;
}
