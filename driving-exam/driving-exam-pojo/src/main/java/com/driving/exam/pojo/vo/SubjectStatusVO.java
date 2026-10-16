package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 学员某科目的进度视图（教练端「学员状态详情」{@code GET /api/coach/students/{id}/status} 的元素）。
 *
 * <p>来源：{@code student_subject_status}（按 {@code student_id} + {@code subject_code} 定位）。</p>
 *
 * <p><b>四科补齐</b>：教练端固定返回 {@code SUBJECT_1}~{@code SUBJECT_4} 四项；库中缺失的科目
 * <b>在内存中按默认值补齐</b>（{@code status = "NOT_STARTED"}、{@code mockPassCount = 0}、
 * {@code lastMockScore = null}）——<b>查询操作绝不为此写库</b>。这是刻意的：该表当前只在
 * 模考交卷时被 {@code ExamFinalizer} upsert，若查询顺手插行会造成"只看一眼就产生数据"。</p>
 *
 * <p><b>刻意不返回 {@code studyProgress}</b>：数据库该列虽已建（{@code decimal(5,2)}），
 * 但目前<b>没有任何代码写入它 → 恒为 0.00</b>，返回给前端会被误读为"该学员完全没学"。
 * 待"题库完成率"的计算口径确定并落地后再加（见 {@code doc/coach-student-design.md} §1.4 与 Q11）。</p>
 */
@Data
public class SubjectStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 科目：SUBJECT_1 / SUBJECT_2 / SUBJECT_3 / SUBJECT_4 */
    private String subjectCode;

    /** 学习阶段：NOT_STARTED / LEARNING / READY_FOR_EXAM / EXAM_PASSED / EXAM_FAILED（缺失科目为 NOT_STARTED） */
    private String status;

    /** 模拟考连续及格次数（缺失科目为 0） */
    private Integer mockPassCount;

    /** 最近一次模拟考成绩（缺失科目或从未模考为 {@code null}） */
    private Integer lastMockScore;
}
