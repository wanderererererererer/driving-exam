package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.AnswerRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答题记录 Mapper。当前仅用 BaseMapper 通用 CRUD。
 */
@Mapper
public interface AnswerRecordMapper extends BaseMapper<AnswerRecord> {
}
