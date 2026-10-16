package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.Question;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目 Mapper。当前仅用 BaseMapper 通用 CRUD。
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
