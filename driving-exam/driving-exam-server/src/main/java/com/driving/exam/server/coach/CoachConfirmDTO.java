package com.driving.exam.server.coach;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 教练确认建议的请求体。
 *
 * <p>合法值与数据库注释及 {@code doc/data-conventions.md} 一致：
 * {@code CONFIRMED_READY} / {@code NOT_READY} / {@code NEED_HELP}。</p>
 */
@Data
public class CoachConfirmDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "confirmStatus 不能为空")
    @Pattern(regexp = "CONFIRMED_READY|NOT_READY|NEED_HELP",
            message = "confirmStatus 必须为 CONFIRMED_READY / NOT_READY / NEED_HELP")
    private String confirmStatus;
}
