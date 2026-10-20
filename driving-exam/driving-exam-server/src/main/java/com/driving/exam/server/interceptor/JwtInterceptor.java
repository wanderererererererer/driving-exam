package com.driving.exam.server.interceptor;

import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.server.config.PathRoleRule;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 登录校验 + 接口级角色鉴权拦截器。
 *
 * <p>流程：放行 OPTIONS → 解析并校验 token（失败 401）→ <b>按 userId 重新读取 sys_user</b>
 * （账号被删除/禁用则 401；token 中的角色与数据库当前角色不一致则 401）→
 * 按 {@link PathRoleRule} 校验当前角色（失败 403）→ 写入 request 属性。</p>
 *
 * <p>这样禁用账号或修改角色会立刻使旧 token 失去业务访问能力，而不是继续信任 token 内的旧状态。</p>
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USERNAME = "username";
    public static final String ATTR_ROLE = "role";

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final PathRoleRule pathRoleRule;
    private final UserMapper userMapper;
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public JwtInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper, PathRoleRule pathRoleRule,
                          UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.pathRoleRule = pathRoleRule;
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 1. 放行跨域预检
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 2. 取 token
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();

        // 3. 校验 token
        if (!jwtUtil.validateToken(token)) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        Claims claims;
        Long userId;
        try {
            claims = jwtUtil.parseToken(token);
            userId = Long.valueOf(claims.getSubject());
        } catch (RuntimeException e) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.TOKEN_INVALID);
        }

        // 4. 重新读取账号状态：token 只证明签发时身份，不能证明账号现在仍然可用
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            // 不存在与逻辑删除统一按未认证处理，不泄露账号是否存在
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }
        if (currentUser.getStatus() == null || currentUser.getStatus() != 1) {
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ACCOUNT_DISABLED);
        }

        String tokenRole = claims.get("role", String.class);
        String currentRole = currentUser.getRole();
        if (tokenRole == null || currentRole == null || !tokenRole.equals(currentRole)) {
            // 角色被管理员修改后，旧 token 不再代表当前账号，要求重新登录
            return writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.ROLE_MISMATCH);
        }

        // 5. 接口级鉴权：使用数据库当前角色，而不是仅信任 token 中的旧角色
        String path = urlPathHelper.getPathWithinApplication(request);
        if (!pathRoleRule.isAllowed(path, currentRole)) {
            return writeJson(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
        }

        // 6. 通过：写入身份属性
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_USERNAME, currentUser.getUsername());
        request.setAttribute(ATTR_ROLE, currentRole);
        return true;
    }

    /** 写出指定 HTTP 状态 + 统一 ApiResponse JSON，中断请求 */
    private boolean writeJson(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
        return false;
    }
}
