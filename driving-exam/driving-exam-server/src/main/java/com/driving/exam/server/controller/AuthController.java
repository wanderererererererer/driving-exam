package com.driving.exam.server.controller;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.pojo.dto.LoginDTO;
import com.driving.exam.pojo.dto.PasswordUpdateDTO;
import com.driving.exam.pojo.dto.ProfileUpdateDTO;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.vo.LoginVO;
import com.driving.exam.pojo.vo.UserVO;
import com.driving.exam.server.interceptor.JwtInterceptor;
import com.driving.exam.server.service.UserService;
import com.driving.exam.server.utils.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与个人中心接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /** 登录 */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        User user = userService.lambdaQuery()
                .eq(User::getUsername, dto.getUsername())
                .one();

        // 用户不存在 / 密码错误，统一返回 1101，避免暴露账号是否存在
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 角色不匹配（走错登录入口）
        if (!user.getRole().equals(dto.getRole())) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        // 账号禁用
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setRole(user.getRole());
        return ApiResponse.success(vo);
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return ApiResponse.success(toVO(user));
    }

    /** 修改个人资料 */
    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
                                           @RequestBody ProfileUpdateDTO dto) {
        User update = new User();
        update.setId(userId);
        update.setRealName(dto.getRealName());
        update.setPhone(dto.getPhone());
        update.setAvatar(dto.getAvatar());
        userService.updateById(update);
        return ApiResponse.success();
    }

    /** 修改密码 */
    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(@RequestAttribute(JwtInterceptor.ATTR_USER_ID) Long userId,
                                            @RequestBody @Valid PasswordUpdateDTO dto) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPwd(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_ERROR);
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(dto.getNewPwd()));
        userService.updateById(update);
        return ApiResponse.success();
    }

    /** User -> UserVO（剔除 password） */
    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}
