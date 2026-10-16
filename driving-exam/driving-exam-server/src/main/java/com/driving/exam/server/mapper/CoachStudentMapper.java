package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.CoachStudent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教练-学员绑定关系 Mapper。当前仅用 BaseMapper 通用 CRUD。
 *
 * <p><b>★ 重要：本表（{@code coach_student}）没有 {@code deleted} 列</b>，
 * 对应实体 {@link CoachStudent} 也刻意<b>没有</b> {@code @TableLogic}。因此：</p>
 * <ul>
 *   <li>查"有效绑定"要用 <b>{@code status = 1}</b> 过滤，<b>不要</b>指望框架自动过滤删除；</li>
 *   <li>解绑 = 把 {@code status} 置 {@code 0}（<b>不是</b>删除行）；</li>
 *   <li>若有人给实体补上 {@code @TableLogic}，MyBatis-Plus 会拼出 {@code deleted = 0}，
 *       直接报 {@code Unknown column 'deleted'}——<b>切勿这么做</b>。</li>
 * </ul>
 */
@Mapper
public interface CoachStudentMapper extends BaseMapper<CoachStudent> {
}
