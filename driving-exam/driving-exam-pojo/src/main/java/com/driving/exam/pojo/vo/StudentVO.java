package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学员视图（教练端「我的学员列表」{@code GET /api/coach/students} 的元素）。
 *
 * <p>来源：{@code coach_student}（{@code coach_id = 当前教练} 且 {@code status = 1}）⋈
 * {@code sys_user}（{@code id = student_id}）。姓名 / 电话由 Service 在<b>分页查出绑定记录之后</b>
 * 收集去重 ID、<b>一次 {@code IN} 查 {@code sys_user}</b> 再内存映射——<b>绝不逐条查</b>（防 N+1）。</p>
 *
 * <p><b>为什么单独建这个 VO，而不复用 {@link CoachVO}</b>：{@code CoachVO} 是"教练名片"
 * （{@code coachId / realName / phone}，供学员端选教练用）；学员列表需要的是
 * {@code bindId / studentId / username / bindTime}，<b>结构不同</b>，不是重复建设。</p>
 *
 * <p><b>{@code bindId} 与 {@code studentId} 的区别（易混，务必分清）</b>：
 * {@code bindId} 是<b>绑定关系记录</b>的主键（{@code coach_student.id}），
 * {@code studentId} 是<b>学员用户</b>的主键（{@code sys_user.id}）。
 * 教练端后续三个接口 —— {@code PUT /api/coach/students/{id}}、
 * {@code DELETE /api/coach/students/{id}}、{@code GET /api/coach/students/{id}/status} ——
 * 路径里的 {@code {id}} 一律是 <b>{@code coach_student.id}</b>，
 * 因此列表<b>必须</b>回传 {@code bindId} 才能驱动它们（前端不必再查库、也不必用 {@code studentId} 去猜）。</p>
 *
 * <p><b>刻意不返回</b>：{@code password}（绝不外泄）、{@code deleted}（内部逻辑删除字段，对外无意义）、
 * {@code avatar} 与绑定状态（列表只含有效绑定，返回状态无意义；见设计文档 §3.1）。</p>
 *
 * <p><b>姓名可能为 {@code null}</b>：{@code sys_user} 带 {@code @TableLogic}，被逻辑删除的账号查不出来；
 * 此时<b>保留记录、姓名电话留 {@code null}</b>——与预约模块"历史记录要完整"的口径一致。</p>
 *
 * @see com.driving.exam.pojo.vo.CoachVO
 */
@Data
public class StudentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 绑定记录 ID（= {@code coach_student.id}）——教练端三个按 id 操作的接口用它，<b>不是</b> {@code studentId} */
    private Long bindId;

    /** 学员用户 ID（= {@code coach_student.student_id} = {@code sys_user.id}） */
    private Long studentId;

    /** 学员登录名（{@code sys_user.username}） */
    private String username;

    /** 学员姓名（{@code sys_user.real_name}；账号被逻辑删除时可能为 {@code null}） */
    private String realName;

    /** 学员手机号（{@code sys_user.phone}，用于教练联系学员；可能为 {@code null}） */
    private String phone;

    /** 绑定时间（{@code coach_student.bind_time}；列表按此倒序，次级键为绑定记录 id） */
    private LocalDateTime bindTime;

    /**
     * 当前学习科目（从 student_subject_status 的业务状态推导）：
     * 按 READY_FOR_EXAM &gt; LEARNING &gt; EXAM_FAILED &gt; NOT_STARTED 选一科；
     * 没有进行中科目时为 null。不是"最近一次考试科目"。
     */
    private String currentSubjectCode;

    /** 当前学习科目的学习阶段（与 currentSubjectCode 同一行）；无进行中科目时为 null */
    private String currentPhaseStatus;

    /** 当前学习科目的教练确认状态；未设置或无进行中科目时为 null */
    private String coachConfirmStatus;
}
