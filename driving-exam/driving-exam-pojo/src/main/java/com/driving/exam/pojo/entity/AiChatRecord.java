package com.driving.exam.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 问答记录实体，对应表 {@code ai_chat_record}。
 *
 * <p><b>流水表</b>：无 {@code deleted} 列（因此<b>不加 {@code @TableLogic}</b>）、无 {@code update_time}，
 * 仅 {@code create_time} 自动填充。</p>
 *
 * <p>注意：该表<b>允许物理删除</b>（用户数据，用户有权删除自己的问答记录），
 * 边界见 {@code doc/data-conventions.md} §5.1。</p>
 */
@Data
@TableName("ai_chat_record")
public class AiChatRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 会话 ID（前端生成的 UUID，用于把同一轮对话的问答聚合；第一版可为空） */
    private String sessionId;

    /** 科目：SUBJECT_1~4 / COMMON */
    private String subjectCode;

    /** 用户问题 */
    private String question;

    /** AI 回答 */
    private String answer;

    /**
     * 引用来源 JSON —— 第一版留空，为 RAG 升级预留。
     *
     * <p><b>此处刻意保持 {@code String}，不加 {@code JacksonTypeHandler}</b>：
     * MySQL 的 JSON 列经 JDBC 驱动可直接与 String 互转（读时 {@code getString} 返回 JSON 文本，
     * 写时传入**合法 JSON 文本**即可）。</p>
     *
     * <p>而 {@code JacksonTypeHandler} 会把 JSON 反序列化为**字段的声明类型**——字段是 String 时：
     * ① 读取 JSON 对象会抛 {@code MismatchedInputException}；
     * ② 写入会多套一层引号，存成 JSON 字符串而非对象。
     * 这恰好在 RAG 上线（写入真实 JSON）时出问题。</p>
     *
     * <p>⚠️ <b>将来接 RAG 时的正确做法</b>：把本字段改为**强类型**（如专门的 {@code AiReferences} POJO
     * 或 {@code List<Object>}），再配 {@code @TableField(typeHandler = JacksonTypeHandler.class)}
     * 并在类上加 {@code @TableName(autoResultMap = true)}。</p>
     */
    private String referencesJson;

    /** 服务端返回的实际模型 ID（如 deepseek-flash） */
    private String model;

    /** 输入 Token 数 */
    private Integer promptTokens;

    /** 输出 Token 数 */
    private Integer completionTokens;

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
