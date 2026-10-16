package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.KnowledgePoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识点 Mapper。当前仅用 BaseMapper 通用 CRUD。
 */
@Mapper
public interface KnowledgePointMapper extends BaseMapper<KnowledgePoint> {
}
