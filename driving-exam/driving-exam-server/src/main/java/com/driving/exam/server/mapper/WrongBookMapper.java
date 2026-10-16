package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.WrongBook;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 错题本 Mapper。
 */
@Mapper
public interface WrongBookMapper extends BaseMapper<WrongBook> {

    /**
     * 答错时归集错题（upsert）。
     *
     * <p>利用唯一键 {@code uk_user_question(user_id, question_id)}：
     * 不存在则插入 {@code wrong_count=1, mastered=0}；已存在则 {@code wrong_count+1}、
     * 刷新 {@code last_wrong_time}、并把 {@code mastered} 重置为 0（再次答错即视为未掌握）。</p>
     *
     * <p>采用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 而非"先查后改"：单条 SQL 原子完成，
     * 避免并发下两个请求都判定为"不存在"导致重复插入 / 计数不准。</p>
     *
     * @return 影响行数（插入=1，更新=2）
     */
    @Insert("INSERT INTO wrong_book (user_id, subject_code, question_id, wrong_count, last_wrong_time, mastered) "
            + "VALUES (#{userId}, #{subjectCode}, #{questionId}, 1, NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_time = NOW(), mastered = 0")
    int upsertWrong(@Param("userId") Long userId,
                    @Param("subjectCode") String subjectCode,
                    @Param("questionId") Long questionId);

    /**
     * 标记错题已掌握（错题重练答对时调用）。
     *
     * <p>按 {@code (user_id, question_id)} 定位，天然带**越权保护**：
     * 只会更新属于该用户的那条记录。</p>
     *
     * <p>{@code update_time} 需在 SQL 里显式刷新——注解式 SQL 不经过 MyBatis-Plus 的
     * 自动填充（MetaObjectHandler 只对实体 insert/update 生效）。</p>
     *
     * @return 影响行数（0 表示该用户没有这条错题记录）
     */
    @Update("UPDATE wrong_book SET mastered = 1, update_time = NOW() "
            + "WHERE user_id = #{userId} AND question_id = #{questionId}")
    int markMastered(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /**
     * 按**错题记录 id** 标记已掌握（对应接口 PUT /wrong-book/{id}/mastered）。
     *
     * <p>{@code user_id} 写进 WHERE 做**越权保护**：只能改自己名下的记录。
     * 影响行数为 0 时，"不存在"与"不属于当前用户"无法区分——这是**有意为之**，不泄露记录是否存在。</p>
     *
     * @return 影响行数（0 表示该记录不存在或不属于该用户）
     */
    @Update("UPDATE wrong_book SET mastered = 1, update_time = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId}")
    int markMasteredById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 一次聚合查询统计各分类数量（供前端 tab 角标）。
     *
     * <p>用 {@code CASE WHEN} 在一条 SQL 里同时算出三类，避免查三次。
     * 空结果集时 {@code SUM} 会返回 NULL，故用 {@code COALESCE} 兜底为 0。</p>
     *
     * @param subjectCode 为 null 表示不按科目过滤
     * @param threshold   "高频错"阈值（与 Service 层常量保持一致）
     */
    @Select("SELECT "
            + "COALESCE(SUM(CASE WHEN mastered = 1 THEN 1 ELSE 0 END), 0) AS mastered, "
            + "COALESCE(SUM(CASE WHEN mastered = 0 AND wrong_count >= #{threshold} THEN 1 ELSE 0 END), 0) AS highFrequency, "
            + "COALESCE(SUM(CASE WHEN mastered = 0 AND wrong_count < #{threshold} THEN 1 ELSE 0 END), 0) AS pending, "
            + "COUNT(*) AS total "
            + "FROM wrong_book "
            + "WHERE user_id = #{userId} "
            + "AND (#{subjectCode,jdbcType=VARCHAR} IS NULL OR subject_code = #{subjectCode,jdbcType=VARCHAR})")
    WrongBookSummaryVO summarize(@Param("userId") Long userId,
                                 @Param("subjectCode") String subjectCode,
                                 @Param("threshold") int threshold);
}
