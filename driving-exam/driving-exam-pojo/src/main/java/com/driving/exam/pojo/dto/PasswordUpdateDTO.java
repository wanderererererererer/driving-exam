package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码入参。
 */
@Data
public class PasswordUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原密码 */
    @NotBlank(message = "原密码不能为空")
    private String oldPwd;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    private String newPwd;
}
