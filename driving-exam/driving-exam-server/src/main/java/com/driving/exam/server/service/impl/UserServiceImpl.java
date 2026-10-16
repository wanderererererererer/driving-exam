package com.driving.exam.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户 Service 实现。
 *
 * <p>继承 ServiceImpl 直接获得通用 CRUD 能力（save / getById / updateById / page 等）。</p>
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
