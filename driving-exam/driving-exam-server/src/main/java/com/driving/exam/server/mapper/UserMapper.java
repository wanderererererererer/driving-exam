package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper，对应 sys_user 表。
 *
 * <p>除通用 CRUD 外，提供预约创建时锁定教练账号行的方法：同一教练的并发预约在
 * 数据库层串行化，避免“先查冲突、后插入”的竞态；逻辑删除账号不返回。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 预约事务内锁定教练账号行；不存在或已逻辑删除返回 null。 */
    @Select("SELECT id, username, real_name, phone, avatar, role, status, create_time, update_time, deleted "
            + "FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
