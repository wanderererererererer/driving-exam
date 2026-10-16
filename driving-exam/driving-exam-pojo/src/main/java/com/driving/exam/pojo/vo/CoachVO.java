package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 教练简要信息（学员端「我的教练列表」{@code GET /api/student/appointment/coaches} 的元素）。
 *
 * <p>来源：{@code coach_student}（{@code student_id = 当前学员} 且 {@code status = 1}）⋈
 * {@code sys_user}（{@code id = coach_id}，{@code role = 'COACH'}）。</p>
 *
 * <p><b>刻意不含</b> {@code username}（登录名对学员无意义）与 {@code bindTime}（无展示需求），
 * 只保留"学员要联系教练"所需的三个字段。</p>
 */
@Data
public class CoachVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 教练 ID（= sys_user.id） */
    private Long coachId;

    /** 教练姓名（sys_user.real_name） */
    private String realName;

    /** 教练手机号（sys_user.phone，用于"联系教练"） */
    private String phone;
}
