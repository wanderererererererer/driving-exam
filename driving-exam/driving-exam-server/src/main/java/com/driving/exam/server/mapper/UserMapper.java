package com.driving.exam.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.driving.exam.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper，对应 sys_user 表。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
