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
 * 学员-教练会话实体，对应 {@code chat_session}。
 *
 * <p>本表没有 {@code deleted} 列，也没有会话状态列；同一对
 * {@code (student_id, coach_id)} 由唯一键保证只有一个会话。</p>
 */
@Data
@TableName("chat_session")
public class ChatSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long coachId;

    /** 最后一条消息摘要，数据库上限 500 */
    private String lastMessage;

    private LocalDateTime lastMessageTime;

    /** 学员未读数 */
    private Integer studentUnread;

    /** 教练未读数 */
    private Integer coachUnread;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
