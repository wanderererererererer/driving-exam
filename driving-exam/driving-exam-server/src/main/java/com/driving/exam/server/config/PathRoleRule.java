package com.driving.exam.server.config;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 路径 → 角色 访问规则表。
 *
 * <p>由 {@code JwtInterceptor} 读取：按顺序匹配，先命中者生效；
 * 未命中任何规则的路径表示"仅要求登录、不做角色限制"。</p>
 *
 * <p>后续新增模块（如需其它前缀）时，只需在 {@link #RULES} 里追加一行。</p>
 */
@Component
public class PathRoleRule {

    /** 任意已登录角色均可访问 */
    public static final String ANY = "*";

    /** 一条规则：路径模式(Ant 风格) → 允许的角色 */
    public record Rule(String pattern, String role) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("/api/auth/**", ANY),
            new Rule("/api/common/**", ANY),
            new Rule("/api/student/**", "STUDENT"),
            new Rule("/api/coach/**", "COACH"),
            new Rule("/api/admin/**", "ADMIN")
    );

    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * 命中规则所要求的角色；未命中返回 {@code null}（不做角色限制）。
     */
    public String resolveRequiredRole(String path) {
        for (Rule rule : RULES) {
            if (matcher.match(rule.pattern(), path)) {
                return rule.role();
            }
        }
        return null;
    }

    /**
     * 该角色能否访问该路径。
     */
    public boolean isAllowed(String path, String role) {
        String required = resolveRequiredRole(path);
        return required == null || ANY.equals(required) || required.equals(role);
    }
}
