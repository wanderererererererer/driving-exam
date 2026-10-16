package com.driving.exam.server.integration.deepseek.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 调用结果 —— <b>业务层与 AI 基础设施之间的唯一边界类型</b>。
 *
 * <p>业务层（Day 13 智能问答、Day 18 教练建议）只认识这个类，
 * 不接触任何 HTTP / OpenAI 协议细节；将来换模型供应商或改流式，
 * 只要这个类的字段不变，业务代码就不用动。</p>
 *
 * <p>字段刻意只保留**落库所需**的四项，<b>不含 totalTokens</b>——
 * 它可由前两者相加得到（避免"同一事实两份数据"）。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResult {

    /** AI 回答正文 */
    private String content;

    /** 服务端返回的**实际**模型名（用于落库与计费统计，如 deepseek-flash） */
    private String model;

    /** 输入 Token 数 → {@code ai_chat_record.prompt_tokens} */
    private Integer promptTokens;

    /** 输出 Token 数 → {@code ai_chat_record.completion_tokens} */
    private Integer completionTokens;
}
