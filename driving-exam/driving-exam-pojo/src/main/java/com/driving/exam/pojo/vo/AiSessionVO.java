package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话（一个 {@code session_id} 聚合多条问答）——历史抽屉的列表元素。
 *
 * <p>由 {@code ai_chat_record} 按 {@code session_id} 分组聚合得到，
 * <b>不额外建表</b>（会话是问答记录的派生视图）。</p>
 *
 * <p>排除了 {@code session_id IS NULL} 的记录（早期未传会话 ID 的遗留数据）。</p>
 */
@Data
public class AiSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 ID */
    private String sessionId;

    /** 该会话**第一条**问题（按时间升序取首条），用作列表标题 */
    private String firstQuestion;

    /** 该会话**最后一条**记录的时间（用于倒序排序） */
    private LocalDateTime lastTime;

    /** 该会话包含几轮问答 */
    private Integer messageCount;
}
