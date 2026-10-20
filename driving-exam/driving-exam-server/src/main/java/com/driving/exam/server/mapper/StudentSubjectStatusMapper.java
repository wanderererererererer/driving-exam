package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.StudentSubjectStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 学员科目进度 Mapper。
 *
 * <p>模拟考试交卷（{@code ExamFinalizer}）按 {@code (student_id, subject_code)} 查，
 * 有则更新、无则插入——用 {@code selectOne} + {@code updateById} / {@code insert} 即可，无自定义 SQL。</p>
 *
 * <p>教练端改「科目阶段 / 备注」另走本接口的自定义 <b>upsert</b>（见 {@link #upsertCoachFields}），
 * 原因是该场景需要"先按唯一键原子更新、不存在则插入"，且要保证"<b>未传的字段不改</b>"。</p>
 */
@Mapper
public interface StudentSubjectStatusMapper extends BaseMapper<StudentSubjectStatus> {

    /**
     * 写入教练可改的两个字段：{@code status}（学习阶段）与 {@code coach_remark}（教练备注）。
     *
     * <p>利用唯一键 {@code uk_student_subject(student_id, subject_code)}：
     * 记录不存在则插入，已存在则只更新本次传入的字段。</p>
     *
     * <p><b>★ {@code null} 语义</b>：入参为 {@code null} 表示"<b>不改这一列</b>"，用
     * {@code COALESCE} 落到原值；因此本方法<b>不能把某一列清空</b>——这是刻意的
     * （见 {@code doc/coach-student-design.md} §八 Q12）。插入分支里 {@code status} 缺省落
     * {@code 'NOT_STARTED'}，与数据库默认值一致。</p>
     *
     * <p><b>为什么用 upsert 而不是"先查后改"</b>：并发下两个请求可能都判定为"不存在"，
     * 第二个 {@code INSERT} 会撞唯一键。单条 {@code INSERT ... ON DUPLICATE KEY UPDATE} 自身原子，
     * 由唯一键兜底，<b>不会产生重复行</b>——与 {@code MockExamAnswerMapper.upsertAnswer()}、
     * {@code WrongBookMapper.upsertWrong()} 是同一写法。</p>
     *
     * <p><b>为什么 {@code create_time} / {@code update_time} 要显式 {@code NOW()}</b>：
     * 注解式 SQL 不经过 MyBatis-Plus 的自动填充（{@code MyMetaObjectHandler} 只对实体
     * {@code insert} / {@code updateById} 生效），必须自己刷新。</p>
     *
     * <p><b>为什么可空参数要带 {@code jdbcType=VARCHAR}</b>：MyBatis 对 {@code null} 参数若无法推断
     * JDBC 类型会抛设置参数异常；项目已有同样处理（见 {@code WrongBookMapper.summarize} 的
     * {@code #{subjectCode,jdbcType=VARCHAR}}）。</p>
     *
     * @param studentId   学员用户 ID（必填）
     * @param subjectCode 科目（必填，{@code SUBJECT_1}~{@code SUBJECT_4}）
     * @param status      学习阶段；{@code null} = 不改（插入时为 {@code NOT_STARTED}）
     * @param coachRemark 教练备注；{@code null} = 不改
     * @return 影响行数（插入=1，更新=2；与 MySQL 的 {@code ON DUPLICATE KEY UPDATE} 语义一致）
     */
    @Insert("INSERT INTO student_subject_status (student_id, subject_code, status, coach_remark, create_time, update_time) "
            + "VALUES (#{studentId}, #{subjectCode}, COALESCE(#{status,jdbcType=VARCHAR}, 'NOT_STARTED'), "
            + "#{coachRemark,jdbcType=VARCHAR}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "status = COALESCE(#{status,jdbcType=VARCHAR}, status), "
            + "coach_remark = COALESCE(#{coachRemark,jdbcType=VARCHAR}, coach_remark), "
            + "update_time = NOW()")
    int upsertCoachFields(@Param("studentId") Long studentId,
                          @Param("subjectCode") String subjectCode,
                          @Param("status") String status,
                          @Param("coachRemark") String coachRemark);

    /**
     * 覆盖写入一次学习建议（AI 成功或规则降级）。
     *
     * <p>按 {@code (student_id, subject_code)} 唯一键 upsert；不触碰
     * {@code coach_confirm_status}，因为教练确认与 AI 建议是独立职责。
     * AI 失败时调用方传 {@code aiSuggest=null} / {@code aiSuggestTime=null}，
     * 从而不会留下“本次 AI 成功”的痕迹。</p>
     *
     * @param examReadySuggest 规则建议（READY / NEED_MORE / NEED_HELP）
     * @param aiSuggest        AI 建议文本；规则降级时为 null
     * @param aiSuggestTime    AI 生成时间；规则降级时为 null
     */
    @Insert("INSERT INTO student_subject_status "
            + "(student_id, subject_code, exam_ready_suggest, ai_suggest, ai_suggest_time, create_time, update_time) "
            + "VALUES (#{studentId}, #{subjectCode}, #{examReadySuggest,jdbcType=VARCHAR}, "
            + "#{aiSuggest,jdbcType=VARCHAR}, #{aiSuggestTime,jdbcType=TIMESTAMP}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "exam_ready_suggest = #{examReadySuggest,jdbcType=VARCHAR}, "
            + "ai_suggest = #{aiSuggest,jdbcType=VARCHAR}, "
            + "ai_suggest_time = #{aiSuggestTime,jdbcType=TIMESTAMP}, "
            + "update_time = NOW()")
    int upsertAiSuggestion(@Param("studentId") Long studentId,
                           @Param("subjectCode") String subjectCode,
                           @Param("examReadySuggest") String examReadySuggest,
                           @Param("aiSuggest") String aiSuggest,
                           @Param("aiSuggestTime") LocalDateTime aiSuggestTime);

    /**
     * 教练独立确认/修改确认状态；不要求已存在 AI 或规则建议。
     *
     * <p>不存在行时创建（status 走数据库默认值 NOT_STARTED），存在时只改
     * {@code coach_confirm_status}，不触碰建议文本、时间或其他学情字段。</p>
     */
    @Insert("INSERT INTO student_subject_status "
            + "(student_id, subject_code, coach_confirm_status, create_time, update_time) "
            + "VALUES (#{studentId}, #{subjectCode}, #{confirmStatus,jdbcType=VARCHAR}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "coach_confirm_status = #{confirmStatus,jdbcType=VARCHAR}, "
            + "update_time = NOW()")
    int upsertCoachConfirm(@Param("studentId") Long studentId,
                           @Param("subjectCode") String subjectCode,
                           @Param("confirmStatus") String confirmStatus);
}
