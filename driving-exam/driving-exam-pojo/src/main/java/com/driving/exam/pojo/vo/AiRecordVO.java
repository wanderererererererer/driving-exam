package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 问答历史条目（{@code GET /api/student/ai/history} 的列表元素）。
 *
 * <p><b>列表返回完整的 {@code answer}</b>——因为本模块只有 3 个接口、没有"详情"接口，
 * 若列表不带回答，前端点开历史就看不到内容。代价是单页数据偏大（前端展示时自行截断）。</p>
 */
@Data
public class AiRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 用户问题 */
    private String question;

    /** AI 回答（完整） */
    private String answer;

    /** 会话 ID（为毕业设计"按会话分组"预留） */
    private String sessionId;

    /** 科目 */
    private String subjectCode;

    /** 实际模型 */
    private String model;

    private Integer promptTokens;

    private Integer completionTokens;

    /** 提问时间 */
    private LocalDateTime createTime;
}
