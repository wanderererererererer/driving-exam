package com.driving.exam.server.mapper;

import com.driving.exam.pojo.vo.TrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学情分析 Mapper —— <b>只放"聚合 / 投影"查询</b>。
 *
 * <p><b>实体列表不在此重复定义</b>，由 Service 复用现有 BaseMapper（见 {@code doc/analysis-design.md} §3.2）：</p>
 * <ul>
 *   <li>知识点树 → {@code KnowledgePointMapper}</li>
 *   <li>题目（投影 id / knowledge_point_id / difficulty）→ {@code QuestionMapper}</li>
 *   <li>答题记录（投影 question_id / is_correct / answer_time）→ {@code AnswerRecordMapper}</li>
 *   <li>错题本 → {@code WrongBookMapper}</li>
 * </ul>
 * 这些"取列表"的查询用 {@code LambdaQueryWrapper.select(...)} 只取所需列，避免拉回 {@code content}/{@code analysis} 等大字段。
 *
 * <p>自定义 SQL 统一用<b>注解</b>（本模块仅 4 条短查询，与 {@code WrongBookMapper}/{@code AiChatRecordMapper} 的既有风格一致）。</p>
 */
@Mapper
public interface AnalysisMapper {

    /** 该科目题目总数（启用且未删除）。 */
    @Select("SELECT COUNT(*) FROM question "
            + "WHERE subject_code = #{subjectCode} AND status = 1 AND deleted = 0")
    int countTotalQuestions(@Param("subjectCode") String subjectCode);

    /**
     * 去重已答题数（**同一道题答多次只算一次**）。
     *
     * <p>用 {@code COUNT(DISTINCT question_id)} 而非 {@code COUNT(*)}：口径是
     * "进度 = 已学**题**数 / 总**题**数"，同一题刷 N 次只算 1 题。
     * ⚠️ <b>勿改成 {@code COUNT(*)}</b>——那会变成"答题次数"，与进度语义不符。</p>
     */
    @Select("SELECT COUNT(DISTINCT question_id) FROM answer_record "
            + "WHERE user_id = #{userId} AND subject_code = #{subjectCode}")
    int countAnsweredQuestions(@Param("userId") Long userId,
                               @Param("subjectCode") String subjectCode);

    /**
     * 总体正确率 = 答对次数 / 总答题次数。
     *
     * <p>用 {@code AVG(is_correct)}（0/1 列的均值即正确率）一条 SQL 得出，避免把 sum 与 count 分别取回再相除。</p>
     *
     * <p><b>空记录语义</b>：用户答过题（哪怕全错）→ 返回 {@code 0.0}；用户**完全没答过** → {@code AVG} 为 NULL、
     * 本方法返回 {@code null}。这样"没答过"与"全错"在数据层就可区分，具体呈现由 Service 决定
     * （本项目保留 null，前端显示"未开始"而非"0%"）。</p>
     */
    @Select("SELECT AVG(is_correct) FROM answer_record "
            + "WHERE user_id = #{userId} AND subject_code = #{subjectCode}")
    Double avgCorrectRate(@Param("userId") Long userId,
                          @Param("subjectCode") String subjectCode);

    /**
     * 按天聚合的趋势点（**只含"有答题"的日期**，不补空白日）。
     *
     * <p>{@code SUM(is_correct) / COUNT(*)} 直接算出当日正确率（MySQL 的 {@code /} 返回小数，不会整除）；
     * {@code date} 是 MySQL 关键字，用反引号转义。{@code mockScore} 不查询，保持 null。</p>
     *
     * <p>结果按 {@code List<TrendPointVO>} 直接映射（列别名 {@code correct_rate}/{@code answered_count}
     * 由 {@code map-underscore-to-camel-case} 转成 {@code correctRate}/{@code answeredCount}）。</p>
     */
    @Select("SELECT DATE(answer_time) AS `date`, "
            + "SUM(is_correct) / COUNT(*) AS correct_rate, "
            + "COUNT(*) AS answered_count "
            + "FROM answer_record "
            + "WHERE user_id = #{userId} AND subject_code = #{subjectCode} "
            + "AND answer_time >= #{from} "
            + "GROUP BY DATE(answer_time) "
            + "ORDER BY `date`")
    List<TrendPointVO> selectDailyTrend(@Param("userId") Long userId,
                                        @Param("subjectCode") String subjectCode,
                                        @Param("from") LocalDateTime from);
}
