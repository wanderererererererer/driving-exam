package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.ChatSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话数据访问。唯一键冲突交给 INSERT IGNORE 处理，避免并发创建时抛异常。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 幂等创建会话；若 uk_student_coach 已存在则不插入。
     *
     * @return 1 表示插入，0 表示会话已存在
     */
    @Insert("INSERT IGNORE INTO chat_session " +
            "(student_id, coach_id, create_time, update_time) " +
            "VALUES (#{studentId}, #{coachId}, NOW(), NOW())")
    int insertIgnore(@Param("studentId") Long studentId,
                     @Param("coachId") Long coachId);

    /**
     * 发送消息时锁定会话行，保证未读数与最后消息摘要的并发更新串行化。
     */
    @Select("SELECT id, student_id, coach_id, last_message, last_message_time, " +
            "student_unread, coach_unread, create_time, update_time " +
            "FROM chat_session WHERE id = #{id} FOR UPDATE")
    ChatSession selectByIdForUpdate(@Param("id") Long id);
}
