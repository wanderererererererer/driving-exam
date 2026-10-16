package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.QuestionOption;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目选项 Mapper。当前仅用 BaseMapper 通用 CRUD。
 */
@Mapper
public interface QuestionOptionMapper extends BaseMapper<QuestionOption> {
}
