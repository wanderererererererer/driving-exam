package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.ExamPaperQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 试卷题目关联 Mapper。当前仅用 BaseMapper 通用 CRUD。
 *
 * <p>按 {@code paper_id} 取题（含每题 {@code score} 与 {@code sort_order}）用 LambdaQueryWrapper 即可，
 * 无需自定义 SQL。另提供题目是否被任何已创建考试引用的只读计数，供管理员变更题库前做一致性保护。</p>
 */
@Mapper
public interface ExamPaperQuestionMapper extends BaseMapper<ExamPaperQuestion> {

    /**
     * 统计引用该题目的试卷中有多少场已创建的模拟考试。
     *
     * <p>只要计数大于 0，说明该题已经进入某场考试的试卷结构；管理员修改题干、选项、答案或状态
     * 都可能影响该场考试/历史报告，因此调用方应拒绝变更。</p>
     */
    @Select("SELECT COUNT(*) FROM exam_paper_question epq "
            + "JOIN mock_exam e ON e.paper_id = epq.paper_id "
            + "WHERE epq.question_id = #{questionId}")
    Long countExamUsageByQuestionId(@Param("questionId") Long questionId);
}
