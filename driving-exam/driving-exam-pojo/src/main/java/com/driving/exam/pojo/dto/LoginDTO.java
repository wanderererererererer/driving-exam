package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录请求参数。
 */
@Data
public class LoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（明文，后端用 BCrypt 校验） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录角色：STUDENT / COACH / ADMIN（用于校验登录入口是否匹配） */
    @NotBlank(message = "角色不能为空")
    private String role;
}
