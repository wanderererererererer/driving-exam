package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.MockExam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 模拟考试 Mapper。
 *
 * <p>历史列表的"试卷标题"在 Service 层**一次 IN 查试卷后在内存映射**（防 N+1），
 * 故此处不需要 join 的自定义 SQL。</p>
 *
 * <p>另含一个**条件抢占更新** {@link #claimForSettle}：用于交卷 / 超时收尾的**并发幂等**——
 * 只有把考试从 {@code ONGOING} 抢成终态成功的那个请求才继续结算。</p>
 */
@Mapper
public interface MockExamMapper extends BaseMapper<MockExam> {

    /**
     * 条件抢占：**仅当考试仍为 {@code ONGOING} 时**才把状态改为本次结算的终态。
     *
     * <p>单条原子 SQL，依赖 {@code mock_exam} 主键的行锁：并发下只有一个请求影响 1 行、其余影响 0 行。
     * <b>不新增任何唯一键 / 索引</b>。必须在结算事务内执行——若结算后续异常回滚，本更新一并回滚
     * （状态自动回到 {@code ONGOING}）。</p>
     *
     * @param examId 考试 ID
     * @param status 本次结算的终态（{@code SUBMITTED} / {@code TIMEOUT}）
     * @return 影响行数（1 = 抢到并继续结算；0 = 已被他方结算或本非进行中）
     */
    @Update("UPDATE mock_exam SET status = #{status} WHERE id = #{examId} AND status = 'ONGOING'")
    int claimForSettle(@Param("examId") Long examId, @Param("status") String status);
}
