package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.ExamPaperQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷题目关联 Mapper。当前仅用 BaseMapper 通用 CRUD。
 *
 * <p>按 {@code paper_id} 取题（含每题 {@code score} 与 {@code sort_order}）用 LambdaQueryWrapper 即可，
 * 无需自定义 SQL。</p>
 */
@Mapper
public interface ExamPaperQuestionMapper extends BaseMapper<ExamPaperQuestion> {
}
