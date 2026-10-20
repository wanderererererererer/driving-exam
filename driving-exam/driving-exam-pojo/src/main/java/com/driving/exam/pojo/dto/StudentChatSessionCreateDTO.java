package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 学员发起/恢复与指定教练的会话。
 */
@Data
public class StudentChatSessionCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "教练ID不能为空")
    private Long coachId;
}
