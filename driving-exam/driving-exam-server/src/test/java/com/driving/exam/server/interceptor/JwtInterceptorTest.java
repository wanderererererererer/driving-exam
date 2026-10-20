package com.driving.exam.server.interceptor;

import com.driving.exam.pojo.entity.User;
import com.driving.exam.server.config.PathRoleRule;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.utils.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link JwtInterceptor} 的账号状态/角色复查与路径权限测试。
 *
 * <p>使用真实 {@link JwtUtil}、{@link PathRoleRule}，只模拟数据库用户查询；
 * 不启动 Spring、不连数据库。验证的是拦截器业务边界，不是浏览器登录验收。</p>
 */
@DisplayName("JWT 拦截器账号状态与角色复查（离线）")
class JwtInterceptorTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final long USER_ID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JwtUtil jwtUtil;
    private UserMapper userMapper;
    private JwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 3600);
        userMapper = mock(UserMapper.class);
        interceptor = new JwtInterceptor(jwtUtil, objectMapper, new PathRoleRule(), userMapper);
    }

    @Test
    @DisplayName("正常账号：通过并写入数据库当前用户名/角色")
    void activeUserPasses() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(user("student01", "STUDENT", 1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = request("/api/student/study/questions/sequence",
                token("student01", "STUDENT"));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(JwtInterceptor.ATTR_USER_ID)).isEqualTo(USER_ID);
        assertThat(request.getAttribute(JwtInterceptor.ATTR_USERNAME)).isEqualTo("student01");
        assertThat(request.getAttribute(JwtInterceptor.ATTR_ROLE)).isEqualTo("STUDENT");
    }

    @Test
    @DisplayName("账号被禁用：旧 token 返回 401 + 1102，不进入业务接口")
    void disabledUserRejectedEvenWithValidToken() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(user("student01", "STUDENT", 0));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/api/student/study/questions/sequence",
                token("student01", "STUDENT")), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(bodyCode(response)).isEqualTo(1102);
    }

    @Test
    @DisplayName("角色被修改：旧 token 返回 401 + 1106，不按旧角色放行")
    void changedRoleRejectedEvenWithValidToken() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(user("student01", "COACH", 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/api/student/study/questions/sequence",
                token("student01", "STUDENT")), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(bodyCode(response)).isEqualTo(1106);
    }

    @Test
    @DisplayName("账号当前角色与 token 一致但跨角色访问：返回 403")
    void crossRolePathRejected() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(user("student01", "STUDENT", 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/api/admin/dashboard",
                token("student01", "STUDENT")), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(bodyCode(response)).isEqualTo(403);
    }

    @Test
    @DisplayName("缺少 token 或 token 无效：返回 401")
    void missingOrInvalidTokenRejected() throws Exception {
        MockHttpServletResponse missing = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/student/study/questions/sequence", null),
                missing, new Object())).isFalse();
        assertThat(missing.getStatus()).isEqualTo(401);
        assertThat(bodyCode(missing)).isEqualTo(401);

        MockHttpServletResponse invalid = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request("/api/student/study/questions/sequence", "not-a-jwt"),
                invalid, new Object())).isFalse();
        assertThat(invalid.getStatus()).isEqualTo(401);
        assertThat(bodyCode(invalid)).isEqualTo(401);
    }

    @Test
    @DisplayName("账号不存在或已逻辑删除：返回 401，不泄露存在性")
    void missingUserRejected() throws Exception {
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/api/student/study/questions/sequence",
                token("student01", "STUDENT")), response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(bodyCode(response)).isEqualTo(401);
    }

    private String token(String username, String role) {
        return jwtUtil.generateToken(USER_ID, username, role);
    }

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        return request;
    }

    private int bodyCode(MockHttpServletResponse response) throws Exception {
        JsonNode root = objectMapper.readTree(response.getContentAsString());
        return root.path("code").asInt();
    }

    private static User user(String username, String role, int status) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(username);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
