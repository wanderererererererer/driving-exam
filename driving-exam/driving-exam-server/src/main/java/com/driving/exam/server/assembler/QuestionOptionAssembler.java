package com.driving.exam.server.assembler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driving.exam.pojo.entity.QuestionOption;
import com.driving.exam.pojo.vo.QuestionOptionVO;
import com.driving.exam.server.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 题目选项装配器 —— <b>防 N+1 的公共组件</b>。
 *
 * <p>多个模块都需要"给一批题目装配选项"：刷题（顺序 / 章节 / 随机）、错题重练、模拟考试的
 * 交卷报告。这类逻辑若各自实现，极易演变成"循环查库"的 N+1；因此抽成本组件统一复用。</p>
 *
 * <p>本类是**从 {@code QuestionServiceImpl} 的私有 {@code loadOptions} 原样抽取**而来，
 * 行为完全一致（同一条件、同一排序、同样不映射 {@code isCorrect}）。</p>
 */
@Component
public class QuestionOptionAssembler {

    private final QuestionOptionMapper questionOptionMapper;

    public QuestionOptionAssembler(QuestionOptionMapper questionOptionMapper) {
        this.questionOptionMapper = questionOptionMapper;
    }

    /**
     * 批量装配题目选项 —— <b>防 N+1 的关键方法</b>。
     *
     * <p><b>为什么用一次 IN 查询，而不是循环查库：</b>
     * 如果对每道题单独查一次选项（例如在循环里 selectByQuestionId），
     * N 道题就会产生 N 次数据库往返，即经典的 N+1 问题——请求数随题量线性增长，
     * 网络与连接开销成倍放大。</p>
     *
     * <p>这里改为：把当前批次所有 questionId 收集起来，用<b>一次</b> {@code IN} 查询取回全部选项，
     * 再在内存里按 {@code question_id} 分组。无论这一页有多少道题，都只多 1 次查询。</p>
     *
     * @param questionIds 当前批次题目 id 集合；为 null / 空返回空 Map（不抛异常）
     * @return 题目 id -> 该题选项 VO 列表（已按 sort_order 排好）
     */
    public Map<Long, List<QuestionOptionVO>> loadByQuestionIds(Collection<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .in(QuestionOption::getQuestionId, questionIds)
                        .orderByAsc(QuestionOption::getSortOrder)
                        .orderByAsc(QuestionOption::getId));

        Map<Long, List<QuestionOptionVO>> map = new HashMap<>();
        for (QuestionOption o : options) {
            QuestionOptionVO vo = new QuestionOptionVO();
            vo.setOptionKey(o.getOptionKey());
            vo.setOptionContent(o.getOptionContent());
            // 注意：刻意不映射 isCorrect，避免答案泄露
            map.computeIfAbsent(o.getQuestionId(), k -> new ArrayList<>()).add(vo);
        }
        return map;
    }
}
