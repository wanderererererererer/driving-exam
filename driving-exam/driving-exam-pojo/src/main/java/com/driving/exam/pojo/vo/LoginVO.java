package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录成功返回。
 */
@Data
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT token */
    private String token;

    private Long userId;

    private String username;

    private String realName;

    /** 角色：STUDENT / COACH / ADMIN */
    private String role;
}
