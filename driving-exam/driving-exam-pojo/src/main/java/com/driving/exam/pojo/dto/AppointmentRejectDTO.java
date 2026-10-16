package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 拒绝预约入参（{@code PUT /api/coach/appointments/{id}/reject}）。
 *
 * <p>只有"拒绝原因"一个字段。Controller 侧用 {@code @RequestBody @Valid}（{@code required} 默认 true）：</p>
 * <ul>
 *   <li>前端传 {@code {}} → {@code coachRemark == null} → 服务端存 {@code null}（拒绝原因非必填）；</li>
 *   <li>前端传 {@code {"coachRemark":"时间冲突"}} → 正常存入 {@code coach_remark}；</li>
 *   <li>完全不传 body → {@code HttpMessageNotReadableException} → 全局处理器 → {@code 1001}
 *       （这是**刻意的**：连 body 都没有属于参数错误，不是"合法但不填原因"）。</li>
 * </ul>
 */
@Data
public class AppointmentRejectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拒绝原因（可为 null；对应 varchar(255)） */
    @Size(max = 255, message = "拒绝原因不能超过 255 字")
    private String coachRemark;
}
