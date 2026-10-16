package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 问答结果（{@code POST /api/student/ai/chat} 的返回）。
 */
@Data
public class AiAnswerVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** AI 回答正文 */
    private String answer;

    /** 落库后的记录 ID（前端可用于删除该条记录） */
    private Long recordId;

    /** 服务端返回的实际模型（如 deepseek-flash） */
    private String model;

    /** 输入 Token 数 */
    private Integer promptTokens;

    /** 输出 Token 数 */
    private Integer completionTokens;

    /** 记录创建时间（即本次提问时间） */
    private LocalDateTime createTime;
}
