package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.ExamPaper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 试卷 Mapper。
 *
 * <p>除通用 CRUD 外，提供开考时的行锁查询：同一试卷的并发“开始考试”在数据库层串行化，
 * 避免两次请求同时查不到进行中考试后各自新建一场。</p>
 */
@Mapper
public interface ExamPaperMapper extends BaseMapper<ExamPaper> {

    /** 开考事务内锁定试卷行；逻辑删除的试卷不返回。 */
    @Select("SELECT * FROM exam_paper WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ExamPaper selectByIdForUpdate(@Param("id") Long id);
}
