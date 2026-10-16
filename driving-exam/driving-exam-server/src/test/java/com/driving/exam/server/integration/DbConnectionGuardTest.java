package com.driving.exam.server.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库连接安全守卫（**只读**，仅测试作用域）。
 *
 * <h3>目的</h3>
 * 集成测试启动时**强制校验实际连接的是独立测试库**，防止误连正式库 `driving_exam`：
 * <ol>
 *   <li>配置层：解析 {@code spring.datasource.url} 的**目标库名**（{@code ?} 前、最后一个 {@code /} 后），
 *       必须**严格等于** {@code driving_exam_test}（严格相等，而非 {@code contains} 子串匹配，
 *       以免 {@code driving_exam_test_evil} 之类的名字被误放行）；</li>
 *   <li>实际连接层：以真实连接复核 {@code SELECT DATABASE()} 与 {@code SELECT CURRENT_USER()}。</li>
 * </ol>
 *
 * <h3>安全边界</h3>
 * <ul>
 *   <li><b>只读</b>：仅执行 {@code SELECT}，**不做** INSERT/UPDATE/DELETE/DDL/清理；</li>
 *   <li><b>不输出秘密</b>：断言失败信息不含口令等凭据；</li>
 *   <li><b>fail-fast</b>：URL / 数据库名 / 实际账号任一不符即断言失败；</li>
 *   <li>依赖 {@code application-test.yml}（{@code @ActiveProfiles("test")}）——该文件用**无默认值**占位符，
 *       环境变量缺失时本类**根本起不来**（上下文加载失败），**不会**回退到正式库。</li>
 * </ul>
 *
 * <h3>使用约定</h3>
 * 任何“会写数据库”的集成测试，都必须先确认本守卫通过（本类位于
 * {@code com.driving.exam.server.integration} 包，便于先于结算测试运行/核对）。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("DB 连接安全守卫（只读）")
class DbConnectionGuardTest {

    /** 期望的测试库名（严格匹配）。 */
    private static final String EXPECTED_DATABASE = "driving_exam_test";

    /** 期望的实际连接账号（严格匹配，排除 root）。 */
    private static final String EXPECTED_USER = "drv_test@localhost";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("必须连接独立测试库 driving_exam_test，且实际账号为 drv_test@localhost（非 root）")
    void connectionMustTargetIndependentTestDatabase() throws Exception {
        // 1) 配置层：JDBC URL 必须已配置，且其【目标库名】严格等于 driving_exam_test
        String url = environment.getProperty("spring.datasource.url");
        assertThat(url)
                .as("spring.datasource.url 必须已配置（若缺 TEST_DB_URL，上下文应已启动失败）")
                .isNotBlank();
        assertThat(extractDatabaseName(url))
                .as("JDBC URL 的目标库名必须严格等于 driving_exam_test（非子串匹配）")
                .isEqualTo(EXPECTED_DATABASE);

        // 2) 实际连接层：以真实连接复核（不只看配置字符串）
        try (Connection conn = dataSource.getConnection();
             Statement statement = conn.createStatement()) {

            try (ResultSet rs = statement.executeQuery("SELECT DATABASE()")) {
                assertThat(rs.next()).as("应能取到 SELECT DATABASE() 结果").isTrue();
                assertThat(rs.getString(1))
                        .as("实际连接的数据库必须是 driving_exam_test")
                        .isEqualTo(EXPECTED_DATABASE);
            }

            try (ResultSet rs = statement.executeQuery("SELECT CURRENT_USER()")) {
                assertThat(rs.next()).as("应能取到 SELECT CURRENT_USER() 结果").isTrue();
                assertThat(rs.getString(1))
                        .as("实际连接账号必须是 drv_test@localhost（不得为 root 或其他账号）")
                        .isEqualTo(EXPECTED_USER);
            }
        }
    }

    /**
     * 从 JDBC URL 中解析出**目标数据库名**（严格口径）：
     * 去掉 {@code ?} 之后的查询参数，再取最后一个 {@code /} 之后的片段。
     *
     * <p>例：{@code jdbc:mysql://localhost:3306/driving_exam_test?a=b} → {@code driving_exam_test}；
     * 无库名（如 {@code jdbc:mysql://localhost:3306/}）返回空串，因而不会与期望值相等、守卫失败。</p>
     */
    private static String extractDatabaseName(String url) {
        String withoutQuery = url;
        int query = url.indexOf('?');
        if (query >= 0) {
            withoutQuery = url.substring(0, query);
        }
        int slash = withoutQuery.lastIndexOf('/');
        return slash >= 0 ? withoutQuery.substring(slash + 1) : "";
    }
}
