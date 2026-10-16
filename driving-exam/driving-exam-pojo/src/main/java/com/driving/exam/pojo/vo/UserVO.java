package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户信息返回视图。
 *
 * <p>注意：<b>不含 password</b>，避免密码泄露到前端。</p>
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String realName;

    private String phone;

    private String avatar;

    /** 角色：STUDENT / COACH / ADMIN */
    private String role;

    /** 状态：1 正常，0 禁用 */
    private Integer status;
}
