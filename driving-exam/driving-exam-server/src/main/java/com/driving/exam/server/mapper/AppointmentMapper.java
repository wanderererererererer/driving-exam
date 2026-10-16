package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预约 Mapper。当前仅用 BaseMapper 通用 CRUD。
 *
 * <p><b>无自定义 SQL</b>：列表要展示的"学员 / 教练姓名与电话"由 Service 在**分页查出预约之后**，
 * 收集两侧 ID、**一次 {@code IN} 查 {@code sys_user}** 再内存映射——**不走 join**。
 * 这样避免自定义 SQL 里还要手工处理逻辑删除条件与字段拼接。</p>
 *
 * <p>时段冲突检测（占用 = {@code status IN (PENDING, CONFIRMED)}）用
 * {@code selectCount} + {@code LambdaQueryWrapper} 即可，也不需要自定义 SQL。</p>
 */
@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
