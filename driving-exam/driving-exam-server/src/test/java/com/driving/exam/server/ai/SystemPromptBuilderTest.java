package com.driving.exam.server.ai;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link SystemPromptBuilder} 效果验证测试。
 *
 * <p>本测试只打印拼装结果、不做断言——目的就是<b>肉眼看提示词长什么样</b>。</p>
 *
 * <p><b>运行方式</b>：在 IDEA 里逐个方法点 ▶ 运行；或临时去掉类上的 {@code @Disabled}。
 * 需要 MySQL 可连通（{@code @SpringBootTest} 会启动完整上下文）。</p>
 *
 * <p>⚠️ 加 {@code @Disabled} 的原因：这是<b>一次性观感验证</b>，不是回归测试；
 * 若每次 {@code mvn test} 都跑，会白白拖慢构建且引入对数据库的依赖。</p>
 */
@SpringBootTest
@Disabled("手动运行：仅打印提示词效果。去掉本注解或在 IDEA 中单独运行某个方法。")
class SystemPromptBuilderTest {

    @Autowired
    private SystemPromptBuilder systemPromptBuilder;

    @Test
    void testWithoutSubject() {
        System.out.println("========== testWithoutSubject（传 null） ==========");
        System.out.println(systemPromptBuilder.buildSystemPrompt(null));
        System.out.println("========== end ==========");
    }

    @Test
    void testWithSubject1() {
        System.out.println("========== testWithSubject1（传 SUBJECT_1） ==========");
        System.out.println(systemPromptBuilder.buildSystemPrompt("SUBJECT_1"));
        System.out.println("========== end ==========");
    }

    @Test
    void testWithInvalidSubject() {
        System.out.println("========== testWithInvalidSubject（传 XXX，应只返回固定部分） ==========");
        System.out.println(systemPromptBuilder.buildSystemPrompt("XXX"));
        System.out.println("========== end ==========");
    }
}
