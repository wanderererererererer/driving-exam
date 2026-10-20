package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 预约 Mapper。
 *
 * <p>列表使用 BaseMapper + Service 层批量查用户；创建预约用教练行锁串行化，见
 * {@code UserMapper.selectByIdForUpdate}。状态流转使用按归属锁定的行查询，
 * 避免两个并发审批/取消同时读到旧状态后互相覆盖。</p>
 */
@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {

    /** 学员所属预约行锁；不存在或不属于当前学员返回 null。 */
    @Select("SELECT * FROM appointment WHERE id = #{id} AND student_id = #{studentId} "
            + "AND deleted = 0 FOR UPDATE")
    Appointment selectStudentOwnedForUpdate(@Param("id") Long id,
                                            @Param("studentId") Long studentId);

    /** 教练所属预约行锁；不存在或不属于当前教练返回 null。 */
    @Select("SELECT * FROM appointment WHERE id = #{id} AND coach_id = #{coachId} "
            + "AND deleted = 0 FOR UPDATE")
    Appointment selectCoachOwnedForUpdate(@Param("id") Long id,
                                          @Param("coachId") Long coachId);
}
