package com.driving.exam.server.integration.deepseek;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.server.integration.deepseek.dto.ChatMessage;
import com.driving.exam.server.integration.deepseek.dto.ChatResult;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DeepSeekClient} 连通性测试。
 *
 * <h3>⚠️ 手动运行，且会真实调用 API（消耗额度）</h3>
 *
 * <p>类上的 {@code @Disabled} 是**有意加的**：本测试会真实访问外部付费服务，
 * 若不加，每次 {@code mvn test} 都会联网调用、消耗额度，且在网络不通时让构建失败。
 * 常规构建**不应该**依赖外部服务。</p>
 *
 * <p><b>运行方式</b>：在 IDEA 里点方法左侧的 ▶ 单独运行；或临时去掉类上的 {@code @Disabled}。</p>
 *
 * <p><b>前置条件</b>：
 * ① 环境变量 {@code DEEPSEEK_API_KEY} 已设置；
 * ② MySQL 可连通——因为 {@code @SpringBootTest} 会启动**完整** Spring 上下文。</p>
 *
 * <p><b>为什么用 {@code @SpringBootTest} 而不是直接 new</b>：这样能顺带验证
 * {@code deepseek.*} 的**属性绑定**是否正确（如 {@code top-p → topP}、{@code read-timeout → readTimeout}）
 * —— 属性绑定恰恰是配置类最容易出错、又最不容易发现的地方。</p>
 */
@Slf4j
@SpringBootTest
@Disabled("手动运行：会真实调用 DeepSeek API，消耗额度。去掉本注解或在 IDEA 中单独运行该测试。")
class DeepSeekClientTest {

    @Autowired
    private DeepSeekClient deepSeekClient;

    /* ============================== 测试方法 ============================== */

    @Test
    void testSimpleChat() {
        long start = System.currentTimeMillis();
        ChatResult result = call("testSimpleChat", () -> deepSeekClient.chat("你好"));
        long cost = System.currentTimeMillis() - start;

        logResult("testSimpleChat", result, cost);
        assertNotNull(result.getContent(), "AI 应返回非空回答");
        assertNotNull(result.getPromptTokens(), "应返回 promptTokens（用于落库）");
        assertNotNull(result.getCompletionTokens(), "应返回 completionTokens（用于落库）");
    }

    @Test
    void testMultiMessage() {
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是驾考助手，回答务必简洁，控制在 50 字以内。"),
                ChatMessage.user("科目一考试有多少道题？"));

        long start = System.currentTimeMillis();
        ChatResult result = call("testMultiMessage", () -> deepSeekClient.chat(messages));
        long cost = System.currentTimeMillis() - start;

        logResult("testMultiMessage", result, cost);
        assertNotNull(result.getContent(), "多轮 messages 应返回非空回答");
    }

    /**
     * 长回答是否能在读超时内返回 —— 用来验证 {@code read-timeout = 60s} 够不够。
     *
     * <p>非流式请求下，服务端在生成期间不返回任何字节，
     * 所以这个值实际衡量的是"等待整段生成完成"的时间。</p>
     */
    @Test
    void testLongAnswerWithinReadTimeout() {
        long start = System.currentTimeMillis();
        ChatResult result = call("testLongAnswerWithinReadTimeout",
                () -> deepSeekClient.chat("请用大约 500 字介绍科目一考试的常见考点，分点说明。"));
        long cost = System.currentTimeMillis() - start;

        logResult("testLongAnswerWithinReadTimeout", result, cost);
        assertNotNull(result.getContent(), "长回答应返回非空内容");
        assertTrue(cost < 60_000, "本次耗时 " + cost + "ms，已超过 read-timeout(60s)，需要调整配置");
        if (cost > 30_000) {
            log.warn("本次生成耗时 {}ms，已接近 read-timeout(60s) 边界，建议关注是否需要上调", cost);
        }
    }

    /* ============================== 辅助 ============================== */

    /**
     * 把 {@link BusinessException} 翻译成**人能一眼看懂的排查提示**再抛出，
     * 避免控制台只看到一句"AI 服务调用失败"却不知道到底哪里错了。
     */
    private ChatResult call(String label, Supplier<ChatResult> action) {
        try {
            return action.get();
        } catch (BusinessException e) {
            String hint = switch (e.getCode()) {
                case 4001 -> "调用失败：网络不可达 / API Key 无效 / 服务端 5xx —— 请看上方 ERROR/WARN 日志的 status 与 body";
                case 4002 -> "读超时：模型生成超过了 read-timeout（当前 60s），可上调 deepseek.read-timeout";
                case 4003 -> "被限流或额度不足（HTTP 429）";
                default -> "未知错误码，请查看上方日志";
            };
            throw new AssertionError("[" + label + "] AI 调用失败 code=" + e.getCode() + " → " + hint, e);
        }
    }

    /**
     * 打印结果。
     *
     * <p>⚠️ 只打印结果与用量，<b>绝不打印配置对象</b>（{@code DeepSeekProperties} 含 API Key）。</p>
     */
    private void logResult(String label, ChatResult result, long cost) {
        log.info("""

                        ===== [{}] 调用成功 =====
                        耗时          : {} ms
                        返回 model    : {}          （服务端路由后的实际模型）
                        promptTokens  : {}
                        completionTokens: {}
                        回答内容      :
                        {}
                        ============================================""",
                label, cost, result.getModel(),
                result.getPromptTokens(), result.getCompletionTokens(), result.getContent());
    }
}
