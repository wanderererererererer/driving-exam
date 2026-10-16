package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.MockExamAnswer;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 考试暂存答案 Mapper（断点续考）。
 *
 * <p>普通增删改查用 BaseMapper；<b>暂存</b>用自定义 upsert。</p>
 */
@Mapper
public interface MockExamAnswerMapper extends BaseMapper<MockExamAnswer> {

    /**
     * 暂存一道题的答案（upsert）。
     *
     * <p>利用唯一键 {@code uk_exam_question(exam_id, question_id)}：不存在则插入，已存在则覆盖答案。
     * 采用 {@code INSERT ... ON DUPLICATE KEY UPDATE} 而非"先查后改"——单条 SQL 原子，
     * 避免并发下重复插入。</p>
     *
     * <p>注意：这里**不判分**，只存用户答案；判分在交卷时统一做。</p>
     *
     * @return 影响行数（插入=1，更新=2）
     */
    @Insert("INSERT INTO mock_exam_answer (exam_id, question_id, user_answer, create_time, update_time) "
            + "VALUES (#{examId}, #{questionId}, #{userAnswer}, NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE user_answer = #{userAnswer}, update_time = NOW()")
    int upsertAnswer(@Param("examId") Long examId,
                     @Param("questionId") Long questionId,
                     @Param("userAnswer") String userAnswer);
}
