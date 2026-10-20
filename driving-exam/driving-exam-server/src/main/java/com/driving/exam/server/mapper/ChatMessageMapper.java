package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 聊天消息数据访问。消息不做物理删除，按会话和自增 ID 游标查询。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 首屏：最近 N 条，按 id 倒序返回，服务层再反转为正序。
     */
    @Select("SELECT id, session_id, sender_id, sender_role, content, is_read, create_time " +
            "FROM chat_message WHERE session_id = #{sessionId} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<ChatMessage> selectLatest(@Param("sessionId") Long sessionId,
                                   @Param("limit") int limit);

    /**
     * 下拉更早消息：id 小于 beforeId，先倒序取 N 条，服务层再反转。
     */
    @Select("SELECT id, session_id, sender_id, sender_role, content, is_read, create_time " +
            "FROM chat_message WHERE session_id = #{sessionId} AND id < #{beforeId} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<ChatMessage> selectBefore(@Param("sessionId") Long sessionId,
                                   @Param("beforeId") Long beforeId,
                                   @Param("limit") int limit);

    /**
     * 轮询新消息：id 大于 lastId，按 id 正序返回。
     */
    @Select("SELECT id, session_id, sender_id, sender_role, content, is_read, create_time " +
            "FROM chat_message WHERE session_id = #{sessionId} AND id > #{lastId} " +
            "ORDER BY id ASC LIMIT #{limit}")
    List<ChatMessage> selectAfter(@Param("sessionId") Long sessionId,
                                  @Param("lastId") Long lastId,
                                  @Param("limit") int limit);

    /**
     * 将当前会话中对方发来的未读消息标记为已读。
     */
    @Update("UPDATE chat_message SET is_read = 1 " +
            "WHERE session_id = #{sessionId} AND sender_role <> #{readerRole} AND is_read = 0")
    int markRead(@Param("sessionId") Long sessionId,
                 @Param("readerRole") String readerRole);
}
