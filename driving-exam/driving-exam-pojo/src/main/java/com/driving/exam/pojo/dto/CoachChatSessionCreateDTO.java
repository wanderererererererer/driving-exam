package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 教练发起/恢复与指定学员的会话。
 */
@Data
public class CoachChatSessionCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "学员ID不能为空")
    private Long studentId;
}
