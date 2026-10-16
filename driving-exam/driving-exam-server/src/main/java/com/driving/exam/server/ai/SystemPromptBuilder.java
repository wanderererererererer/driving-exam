package com.driving.exam.server.ai;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统提示词构建器：为 AI 问答拼装 system prompt。
 *
 * <p><b>为什么放 {@code server.ai} 而不是 {@code server.integration.deepseek}</b>：
 * 这段提示词是<b>业务</b>（驾考辅导的角色设定与规则），不是 DeepSeek 的<b>协议适配</b>。
 * 将来更换模型供应商、甚至升级为 RAG，这段提示词仍然沿用。</p>
 *
 * <p><b>为什么是 {@code @Component} 而非 static 工具类</b>：与项目既有做法一致
 * （{@code DeepSeekProperties}/{@code DeepSeekClient} 均为 Spring Bean），
 * 且保留了将来注入配置的余地（如从 yml 读取提示词版本、按科目配置不同规则）。
 * 提示词是会持续调优的内容，留一个"可被替换/覆盖"的入口比写死更稳妥。</p>
 */
@Component
public class SystemPromptBuilder {

    /**
     * 固定部分：角色定义 + 4 条规则。
     *
     * <p>用 {@code \n} 显式分行而非文本块（text block）：代码里换行位置一目了然，
     * 也不会因 IDE 缩进把空白带进提示词（空白会影响 token 与模型行为）。</p>
     */
    private static final String BASE_PROMPT =
            "你是驾考辅导助手，专门帮助学员准备驾照考试。\n"
                    + "规则：\n"
                    + "1. 只回答与驾考相关的问题，与驾考无关的礼貌拒绝\n"
                    + "2. 回答要简洁准确，控制在 200 字以内\n"
                    + "3. 不确定的内容明确说\"我不确定\"，不要编造答案\n"
                    + "4. 如果题目涉及具体法规，优先引用最新规定";

    /**
     * 科目代码 → 中文名称。
     *
     * <p><b>暂放本类内部</b>：目前只有这一处用到中文科目名。
     * 将来 {@code student_subject_status} 等接口也需要中文科目名时，
     * 再抽成公共枚举 / 工具类（届时改动点集中，迁移成本低）。</p>
     */
    private static final Map<String, String> SUBJECT_NAMES = Map.of(
            "SUBJECT_1", "科目一",
            "SUBJECT_2", "科目二",
            "SUBJECT_3", "科目三",
            "SUBJECT_4", "科目四");

    /**
     * 构建 system prompt。
     *
     * @param subjectCode 科目代码（如 {@code SUBJECT_1}）；可为空
     * @return 固定部分；若 {@code subjectCode} 可识别，则末尾追加一句科目上下文
     */
    public String buildSystemPrompt(String subjectCode) {
        String subjectName = (subjectCode == null) ? null : SUBJECT_NAMES.get(subjectCode);
        // 为空或未知科目：只返回固定部分（不追加动态上下文）
        if (subjectName == null) {
            return BASE_PROMPT;
        }
        return BASE_PROMPT
                + "\n\n该学员正在准备" + subjectName + "考试，回答时请侧重" + subjectName + "的知识点。";
    }
}
