package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.AiChatRecord;
import com.driving.exam.pojo.vo.AiSessionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI 问答记录 Mapper。
 *
 * <p>单条查询/删除/历史分页可用 BaseMapper；<b>会话分组</b>需要自定义 SQL（GROUP BY）。</p>
 */
@Mapper
public interface AiChatRecordMapper extends BaseMapper<AiChatRecord> {

    /**
     * 按会话分组查询当前用户的会话列表（时间倒序）。
     *
     * <p>用<b>显式 offset/limit</b> 分页而非 MyBatis-Plus 的 {@code IPage}：本查询含
     * {@code GROUP BY}，自动 count 优化在分组场景下容易产生疑义，这里改为 count 与数据两条独立 SQL，
     * 结果完全确定。</p>
     *
     * <p>{@code first_question} 用<b>相关子查询</b>取"该会话时间最早的一条"的 question
     * （不能用 {@code GROUP_CONCAT}——它默认会被 1024 字节截断）。
     * 子查询里按 {@code #{userId}} 过滤而非引用外层 {@code r.user_id}：MySQL 8 默认
     * {@code ONLY_FULL_GROUP_BY}，引用未分组的外层列会被判为非法；而外层本已按 user_id 过滤，
     * 用参数等价且合法。</p>
     *
     * @param offset 偏移量（从 0 开始）
     * @param limit  每页条数
     */
    @Select("SELECT r.session_id AS session_id, "
            + "(SELECT r2.question FROM ai_chat_record r2 "
            + " WHERE r2.user_id = #{userId} AND r2.session_id = r.session_id "
            + " ORDER BY r2.create_time ASC, r2.id ASC LIMIT 1) AS first_question, "
            + "MAX(r.create_time) AS last_time, "
            + "COUNT(*) AS message_count "
            + "FROM ai_chat_record r "
            + "WHERE r.user_id = #{userId} AND r.session_id IS NOT NULL "
            + "GROUP BY r.session_id "
            + "ORDER BY last_time DESC "
            + "LIMIT #{offset}, #{limit}")
    List<AiSessionVO> selectSessions(@Param("userId") Long userId,
                                     @Param("offset") long offset,
                                     @Param("limit") long limit);

    /** 统计当前用户的会话数（与 {@link #selectSessions} 的 GROUP BY 口径一致：排除 session_id 为空）。 */
    @Select("SELECT COUNT(DISTINCT session_id) FROM ai_chat_record "
            + "WHERE user_id = #{userId} AND session_id IS NOT NULL")
    long countSessions(@Param("userId") Long userId);
}

