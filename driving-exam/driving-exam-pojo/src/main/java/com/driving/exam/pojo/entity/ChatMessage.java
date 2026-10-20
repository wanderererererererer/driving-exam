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
 * 学员-教练聊天消息实体，对应 {@code chat_message}。
 *
 * <p>{@code sender_role} 只应为 STUDENT / COACH；{@code is_read} 是接收方视角。</p>
 */
@Data
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long senderId;

    private String senderRole;

    /** 消息正文，数据库上限 1000 */
    private String content;

    @TableField("is_read")
    private Integer isRead;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
