package com.driving.exam.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.driving.exam.pojo.entity.User;

/**
 * 用户 Service。
 *
 * <p>当前仅继承 MyBatis-Plus 的通用 CRUD；登录、注册等业务方法
 * （密码校验、JWT 生成等）待 Controller 阶段再补充。</p>
 */
public interface UserService extends IService<User> {
}
