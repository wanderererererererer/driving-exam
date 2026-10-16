package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷 Mapper。当前仅用 BaseMapper 通用 CRUD。
 *
 * <p>试卷数量极少（演示卷 1 份），无自定义 SQL 需求。</p>
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {
}
