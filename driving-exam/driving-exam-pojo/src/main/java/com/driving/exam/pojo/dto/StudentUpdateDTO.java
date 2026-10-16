package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改学员科目阶段 / 备注入参（{@code PUT /api/coach/students/{id}}）。
 *
 * <p><b>落点</b>：{@code coach_student} 表<b>没有</b>"备注"与"科目阶段"列，故本接口写入的是
 * <b>{@code student_subject_status}</b> 表（其 {@code coach_remark varchar(500)} 与
 * {@code status varchar(30)} 两列已存在，语义正是"该学员在某科目的学习阶段与教练备注"）。
 * 因此入参必须带 {@code subjectCode} 指明改哪一科。详见 {@code doc/coach-student-design.md} §〇 冲突 2。</p>
 *
 * <p><b>★ 两个可选字段至少要传一个</b>：{@code phaseStatus} 与 {@code coachRemark} 均为可选，
 * 但若<b>两个都不传</b>，本次请求没有任何修改内容——该判定由 <b>Service 手写判空</b>并抛既有的
 * {@code PARAM_ERROR(1001)}，不使用 Bean Validation 分组（与 {@link StudentBindDTO} 同一取舍）。</p>
 *
 * <p><b>写入语义（{@code COALESCE}）</b>：Mapper 侧用
 * {@code INSERT ... ON DUPLICATE KEY UPDATE} + {@code COALESCE(#{x}, x)}，
 * 因此<b>传 {@code null} 的字段表示"不修改"</b>；这也意味着<b>暂时无法把备注清空为 {@code null}</b>
 * （本次拍板接受，见设计文档 §八 Q12）。</p>
 *
 * <p>注：{@code @Pattern} 是<b>整串匹配</b>（内部用 {@code Matcher.matches()}），故正则无需写 {@code ^...$}。</p>
 */
@Data
public class StudentUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 要修改的科目：SUBJECT_1 / SUBJECT_2 / SUBJECT_3 / SUBJECT_4 */
    @NotBlank(message = "科目不能为空")
    @Pattern(regexp = "SUBJECT_1|SUBJECT_2|SUBJECT_3|SUBJECT_4",
            message = "科目只能是 SUBJECT_1 / SUBJECT_2 / SUBJECT_3 / SUBJECT_4")
    private String subjectCode;

    /** 学习阶段（可选；取值对应 {@code student_subject_status.status} 的 5 个枚举名） */
    @Pattern(regexp = "NOT_STARTED|LEARNING|READY_FOR_EXAM|EXAM_PASSED|EXAM_FAILED",
            message = "学习阶段取值不合法")
    private String phaseStatus;

    /** 教练备注（可选，可为 null；对应 varchar(500)） */
    @Size(max = 500, message = "备注不能超过 500 字")
    private String coachRemark;
}
