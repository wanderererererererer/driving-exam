package com.driving.exam.server.interceptor;

import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.ApiResponse;
import com.driving.exam.server.config.PathRoleRule;
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
 * <p>流程：放行 OPTIONS → 解析并校验 token（失败 401）→ 按 {@link PathRoleRule}
 * 校验角色（失败 403）→ 通过后把 userId / username / role 放入 request 属性。</p>
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
    private final UrlPathHelper urlPathHelper = new UrlPathHelper();

    public JwtInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper, PathRoleRule pathRoleRule) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.pathRoleRule = pathRoleRule;
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
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);

        // 4. 接口级鉴权：按路径规则校验角色
        String path = urlPathHelper.getPathWithinApplication(request);
        if (!pathRoleRule.isAllowed(path, role)) {
            return writeJson(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
        }

        // 5. 通过：写入身份属性
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_USERNAME, username);
        request.setAttribute(ATTR_ROLE, role);
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
