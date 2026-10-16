package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改个人资料入参（字段为 null 表示不修改）。
 */
@Data
public class ProfileUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 头像 URL */
    private String avatar;
}
