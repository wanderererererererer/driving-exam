package com.driving.exam.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.QuestionDetailVO;
import com.driving.exam.pojo.vo.QuestionOptionVO;
import com.driving.exam.pojo.vo.QuestionVO;
import com.driving.exam.server.assembler.QuestionOptionAssembler;
import com.driving.exam.server.mapper.KnowledgePointMapper;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.service.QuestionService;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目 Service 实现。
 *
 * <p>防 N+1 是本类的核心：题目列表查完后，选项一律**一次 IN 查询**批量取回，
 * 再在内存按 question_id 分组拼装，绝不循环查库。</p>
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    /** 随机练习单次可抽取的上限，防止一次拉取过多 */
    private static final int RANDOM_MAX = 100;

    private final QuestionOptionAssembler questionOptionAssembler;
    private final KnowledgePointMapper knowledgePointMapper;

    public QuestionServiceImpl(QuestionOptionAssembler questionOptionAssembler,
                               KnowledgePointMapper knowledgePointMapper) {
        this.questionOptionAssembler = questionOptionAssembler;
        this.knowledgePointMapper = knowledgePointMapper;
    }

    /* ============================ 顺序练习 ============================ */

    @Override
    public PageResult<QuestionVO> getSequenceQuestions(String subjectCode, int pageNum, int pageSize) {
        Page<Question> result = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getSubjectCode, subjectCode)
                        .eq(Question::getStatus, 1)
                        .orderByAsc(Question::getId));
        return toPageResult(result);
    }

    /* ============================ 章节练习 ============================ */

    @Override
    public PageResult<QuestionVO> getChapterQuestions(Long knowledgePointId, int pageNum, int pageSize) {
        KnowledgePoint kp = knowledgePointMapper.selectById(knowledgePointId);
        if (kp == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_POINT_NOT_FOUND);
        }
        // 收集"当前知识点 + 全部子孙知识点"的 id（含子孙，符合章节练习按父节点取题的需求）
        List<Long> kpIds = collectSelfAndDescendantIds(kp);

        Page<Question> result = page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Question>()
                        .in(Question::getKnowledgePointId, kpIds)
                        .eq(Question::getStatus, 1)
                        .orderByAsc(Question::getId));
        return toPageResult(result);
    }

    /**
     * 收集某知识点及其全部子孙知识点的 id。
     *
     * <p>实现：<b>一次</b>查出该科目全部知识点（只取 id / parent_id 两列）→ 内存构父子索引 →
     * 从根节点 BFS 收集。全程不逐层查库；visited 集合防止脏数据成环导致死循环。</p>
     */
    private List<Long> collectSelfAndDescendantIds(KnowledgePoint root) {
        List<KnowledgePoint> all = knowledgePointMapper.selectList(
                new LambdaQueryWrapper<KnowledgePoint>()
                        .select(KnowledgePoint::getId, KnowledgePoint::getParentId)
                        .eq(KnowledgePoint::getSubjectCode, root.getSubjectCode()));

        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (KnowledgePoint kp : all) {
            Long pid = kp.getParentId();
            if (pid != null && pid != 0L) {
                childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(kp.getId());
            }
        }

        List<Long> ids = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(root.getId());
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!visited.add(id)) {
                continue; // 防环
            }
            ids.add(id);
            for (Long child : childrenMap.getOrDefault(id, Collections.emptyList())) {
                queue.add(child);
            }
        }
        return ids;
    }

    /* ============================ 随机练习 ============================ */

    @Override
    public List<QuestionVO> getRandomQuestions(String subjectCode, int count) {
        if (count <= 0) {
            return Collections.emptyList();
        }

        // 1) 先取该科目全部题目 id。
        //    不用 ORDER BY RAND()：它对全表做随机排序，题量大时开销高且无法走索引。
        List<Object> idObjs = baseMapper.selectObjs(new QueryWrapper<Question>()
                .select("id")
                .eq("subject_code", subjectCode)
                .eq("status", 1));
        if (idObjs == null || idObjs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = idObjs.stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList());

        // 2) 内存洗牌后取前 count 个
        Collections.shuffle(ids);
        int take = Math.min(Math.min(count, RANDOM_MAX), ids.size());
        List<Long> chosen = new ArrayList<>(ids.subList(0, take));

        // 3) 一次 IN 查询取题目（再次过滤 status，防止取 id 与取题之间数据被改动）
        List<Question> found = baseMapper.selectList(new LambdaQueryWrapper<Question>()
                .in(Question::getId, chosen)
                .eq(Question::getStatus, 1));
        Map<Long, Question> byId = new HashMap<>();
        for (Question q : found) {
            byId.put(q.getId(), q);
        }
        // 按洗牌后的顺序还原
        List<Question> ordered = new ArrayList<>(take);
        for (Long id : chosen) {
            Question q = byId.get(id);
            if (q != null) {
                ordered.add(q);
            }
        }

        // 4) 装配 VO（复用 assemble：一次 IN 查询取选项，且按入参顺序映射，随机顺序得以保留）
        return assemble(ordered);
    }

    /* ============================ 题目详情 ============================ */

    @Override
    public QuestionDetailVO getQuestionDetail(Long questionId) {
        Question q = getById(questionId);
        if (q == null || q.getStatus() == null || q.getStatus() != 1) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }

        QuestionDetailVO vo = new QuestionDetailVO();
        vo.setId(q.getId());
        vo.setSubjectCode(q.getSubjectCode());
        vo.setQuestionType(q.getQuestionType());
        vo.setContent(q.getContent());
        vo.setDifficulty(q.getDifficulty());
        vo.setMediaUrl(q.getMediaUrl());
        vo.setKnowledgePointId(q.getKnowledgePointId());

        if (q.getKnowledgePointId() != null) {
            KnowledgePoint kp = knowledgePointMapper.selectById(q.getKnowledgePointId());
            vo.setKnowledgePointName(kp == null ? null : kp.getName());
        }
        vo.setOptions(questionOptionAssembler.loadByQuestionIds(Collections.singletonList(questionId))
                .getOrDefault(questionId, Collections.emptyList()));
        return vo;
    }

    /* ============================ 公共装配 ============================ */

    /**
     * 批量装配题目 VO（供本类内部与错题重练等模块复用）。
     *
     * <p>关键点：<b>返回顺序与入参顺序一致</b>——随机练习/错题重练依赖这一点保持打乱后的顺序。
     * 选项通过一次 IN 查询批量取回。</p>
     */
    @Override
    public List<QuestionVO> assemble(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<QuestionOptionVO>> optionMap = questionOptionAssembler.loadByQuestionIds(
                questions.stream().map(Question::getId).collect(Collectors.toList()));

        List<QuestionVO> list = new ArrayList<>(questions.size());
        for (Question q : questions) {
            list.add(toVO(q, optionMap.get(q.getId())));
        }
        return list;
    }

    private PageResult<QuestionVO> toPageResult(Page<Question> result) {
        List<QuestionVO> records = assemble(result.getRecords());
        return new PageResult<>(result.getTotal(), result.getPages(),
                result.getCurrent(), result.getSize(), records);
    }

    /** Question -> QuestionVO（只映射可公开字段） */
    private QuestionVO toVO(Question q, List<QuestionOptionVO> options) {
        QuestionVO vo = new QuestionVO();
        vo.setId(q.getId());
        vo.setSubjectCode(q.getSubjectCode());
        vo.setQuestionType(q.getQuestionType());
        vo.setContent(q.getContent());
        vo.setDifficulty(q.getDifficulty());
        vo.setMediaUrl(q.getMediaUrl());
        vo.setOptions(options == null ? Collections.emptyList() : options);
        return vo;
    }
}
