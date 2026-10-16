package com.driving.exam.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.entity.WrongBook;
import com.driving.exam.pojo.enums.WrongBookCategory;
import com.driving.exam.pojo.vo.QuestionVO;
import com.driving.exam.pojo.vo.WrongBookItemVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.mapper.WrongBookMapper;
import com.driving.exam.server.service.QuestionService;
import com.driving.exam.server.service.WrongBookService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 错题本 Service 实现。
 *
 * <p>分类不落库，全部由 {@code mastered} + {@code wrong_count} 推导；
 * 题目信息一律用<b>一次 IN 查询</b>批量取回后内存拼装，避免 N+1。</p>
 */
@Service
public class WrongBookServiceImpl implements WrongBookService {

    /** 「高频错」阈值：累计答错达到该次数即视为反复错（尚未攻克时归入高频错） */
    public static final int HIGH_FREQ_THRESHOLD = 3;

    private static final int MASTERED_YES = 1;
    private static final int MASTERED_NO = 0;

    private final WrongBookMapper wrongBookMapper;
    private final QuestionMapper questionMapper;
    private final QuestionService questionService;

    public WrongBookServiceImpl(WrongBookMapper wrongBookMapper,
                                QuestionMapper questionMapper,
                                QuestionService questionService) {
        this.wrongBookMapper = wrongBookMapper;
        this.questionMapper = questionMapper;
        this.questionService = questionService;
    }

    /* ============================ 列表 ============================ */

    @Override
    public PageResult<WrongBookItemVO> list(Long userId, String subjectCode, WrongBookCategory category,
                                            int pageNum, int pageSize) {
        LambdaQueryWrapper<WrongBook> wrapper = new LambdaQueryWrapper<WrongBook>()
                .eq(WrongBook::getUserId, userId)
                .eq(hasText(subjectCode), WrongBook::getSubjectCode, subjectCode);
        applyCategoryCondition(wrapper, category);
        applyCategoryOrder(wrapper, category);

        Page<WrongBook> page = wrongBookMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<WrongBookItemVO> items = assemble(page.getRecords());
        return new PageResult<>(page.getTotal(), page.getPages(),
                page.getCurrent(), page.getSize(), items);
    }

    /** 分类过滤条件（与 §8 的判定规则严格对应） */
    private void applyCategoryCondition(LambdaQueryWrapper<WrongBook> w, WrongBookCategory category) {
        if (category == null) {
            return; // 不传 = 全部
        }
        switch (category) {
            case MASTERED -> w.eq(WrongBook::getMastered, MASTERED_YES);
            case HIGH_FREQUENCY -> w.eq(WrongBook::getMastered, MASTERED_NO)
                    .ge(WrongBook::getWrongCount, HIGH_FREQ_THRESHOLD);
            case PENDING -> w.eq(WrongBook::getMastered, MASTERED_NO)
                    .lt(WrongBook::getWrongCount, HIGH_FREQ_THRESHOLD);
        }
    }

    /** 排序规则：高频错按错误次数倒序（最该练），其余按最近答错时间倒序 */
    private void applyCategoryOrder(LambdaQueryWrapper<WrongBook> w, WrongBookCategory category) {
        if (category == WrongBookCategory.HIGH_FREQUENCY) {
            w.orderByDesc(WrongBook::getWrongCount);
        } else {
            w.orderByDesc(WrongBook::getLastWrongTime);
        }
        // 次级排序：id 倒序，保证分页结果稳定（同值时不乱序）
        w.orderByDesc(WrongBook::getId);
    }

    /**
     * 把错题记录装配成 VO：一次 IN 查询题目信息，内存按 question_id 拼装。
     * 题目已被逻辑删除的条目会被跳过。
     */
    private List<WrongBookItemVO> assemble(List<WrongBook> books) {
        if (books == null || books.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> questionIds = books.stream()
                .map(WrongBook::getQuestionId)
                .distinct()
                .collect(Collectors.toList());

        // 一次 IN 查询；@TableLogic 会自动过滤已逻辑删除的题目
        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>().in(Question::getId, questionIds));
        Map<Long, Question> byId = new HashMap<>();
        for (Question q : questions) {
            byId.put(q.getId(), q);
        }

        List<WrongBookItemVO> items = new ArrayList<>(books.size());
        for (WrongBook b : books) {
            Question q = byId.get(b.getQuestionId());
            if (q == null) {
                continue; // 题目已删除：不展示
            }
            WrongBookItemVO vo = new WrongBookItemVO();
            vo.setId(b.getId());
            vo.setQuestionId(b.getQuestionId());
            vo.setQuestionType(q.getQuestionType());
            vo.setContent(q.getContent());
            vo.setWrongCount(b.getWrongCount());
            vo.setLastWrongTime(b.getLastWrongTime());
            vo.setMastered(b.getMastered());
            vo.setCategory(classify(b.getMastered(), b.getWrongCount()));
            items.add(vo);
        }
        return items;
    }

    /**
     * 分类推导：优先级 已攻克 &gt; 高频错 &gt; 待攻克（见 doc/data-conventions.md §8）。
     *
     * <p>暴露为 <b>public static</b>：它是本模块唯一的"判定规则"，纯函数无副作用，
     * 便于单元测试直接调用（无需构造 Service / 启动容器）。</p>
     *
     * <p>注意：阈值取自本类常量 {@link #HIGH_FREQ_THRESHOLD}，因此方法留在 server 侧，
     * 不放进 pojo 模块的枚举（避免 pojo 反向承担业务阈值）。</p>
     */
    public static WrongBookCategory classify(Integer mastered, Integer wrongCount) {
        if (mastered != null && mastered == MASTERED_YES) {
            return WrongBookCategory.MASTERED;
        }
        if (wrongCount != null && wrongCount >= HIGH_FREQ_THRESHOLD) {
            return WrongBookCategory.HIGH_FREQUENCY;
        }
        return WrongBookCategory.PENDING;
    }

    /* ============================ 统计 ============================ */

    @Override
    public WrongBookSummaryVO summary(Long userId, String subjectCode) {
        return wrongBookMapper.summarize(userId, subjectCode, HIGH_FREQ_THRESHOLD);
    }

    /* ============================ 重练抽题 ============================ */

    @Override
    public List<QuestionVO> retry(Long userId, String subjectCode, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        // 1) 优先取「高频错」
        List<Long> highFreq = queryUnmasteredIds(userId, subjectCode, true);
        Collections.shuffle(highFreq);

        List<Long> chosen = new ArrayList<>();
        if (highFreq.size() >= count) {
            chosen.addAll(highFreq.subList(0, count));
        } else {
            chosen.addAll(highFreq);
            // 2) 不够，从「待攻克」补齐
            List<Long> pending = queryUnmasteredIds(userId, subjectCode, false);
            Collections.shuffle(pending);
            int need = count - chosen.size();
            chosen.addAll(pending.subList(0, Math.min(need, pending.size())));
        }
        if (chosen.isEmpty()) {
            return Collections.emptyList(); // 没有可练的错题
        }

        // 3) 一次 IN 查询题目（过滤已停用），按抽中顺序还原后交给 assemble 装配
        List<Question> found = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, chosen)
                .eq(Question::getStatus, 1));
        Map<Long, Question> byId = new HashMap<>();
        for (Question q : found) {
            byId.put(q.getId(), q);
        }
        List<Question> ordered = new ArrayList<>(chosen.size());
        for (Long id : chosen) {
            Question q = byId.get(id);
            if (q != null) {
                ordered.add(q);
            }
        }
        return questionService.assemble(ordered);
    }

    /**
     * 查「未攻克」错题的题目 id 列表。
     *
     * @param highFrequency true=高频错（wrong_count >= 阈值）；false=待攻克（wrong_count &lt; 阈值）
     */
    private List<Long> queryUnmasteredIds(Long userId, String subjectCode, boolean highFrequency) {
        QueryWrapper<WrongBook> w = new QueryWrapper<WrongBook>()
                .select("question_id")
                .eq("user_id", userId)
                .eq("mastered", MASTERED_NO)
                .eq(hasText(subjectCode), "subject_code", subjectCode);
        if (highFrequency) {
            w.ge("wrong_count", HIGH_FREQ_THRESHOLD);
        } else {
            w.lt("wrong_count", HIGH_FREQ_THRESHOLD);
        }

        List<Object> objs = wrongBookMapper.selectObjs(w);
        if (objs == null || objs.isEmpty()) {
            return new ArrayList<>();
        }
        return objs.stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList());
    }

    /* ============================ 标记已掌握 ============================ */

    @Override
    public void markMastered(Long userId, Long wrongBookId) {
        int affected = wrongBookMapper.markMasteredById(wrongBookId, userId);
        if (affected == 0) {
            // "不存在"与"不属于当前用户"返回同一响应，不泄露记录是否存在
            throw new BusinessException(ErrorCode.WRONG_BOOK_NOT_FOUND);
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
