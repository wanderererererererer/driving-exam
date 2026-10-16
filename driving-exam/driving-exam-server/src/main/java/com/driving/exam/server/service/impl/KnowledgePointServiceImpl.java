package com.driving.exam.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.vo.KPTreeNodeVO;
import com.driving.exam.server.mapper.KnowledgePointMapper;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.service.KnowledgePointService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识点 Service 实现：内存建树，全程只有两次数据库交互（查询 + 分组统计），无 N+1。
 */
@Service
public class KnowledgePointServiceImpl extends ServiceImpl<KnowledgePointMapper, KnowledgePoint>
        implements KnowledgePointService {

    private final QuestionMapper questionMapper;

    public KnowledgePointServiceImpl(QuestionMapper questionMapper) {
        this.questionMapper = questionMapper;
    }

    @Override
    public List<KPTreeNodeVO> getKnowledgeTree(String subjectCode) {
        // 1. 一次查询：该科目的全部知识点（deleted=0 由 @TableLogic 自动附加）
        List<KnowledgePoint> points = lambdaQuery()
                .eq(KnowledgePoint::getSubjectCode, subjectCode)
                .orderByAsc(KnowledgePoint::getSortOrder)
                .orderByAsc(KnowledgePoint::getId)
                .list();
        if (points.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 一次统计：各知识点下"直接挂载"的题目数
        Map<Long, Integer> directCount = countQuestionsByKnowledgePoint(subjectCode);

        // 3. 转 VO（先写入直接题目数，稍后再累加成含子孙合计）
        Map<Long, KPTreeNodeVO> nodeMap = new LinkedHashMap<>();
        for (KnowledgePoint p : points) {
            KPTreeNodeVO vo = new KPTreeNodeVO();
            vo.setId(p.getId());
            vo.setName(p.getName());
            vo.setParentId(p.getParentId());
            vo.setLevel(p.getLevel());
            vo.setSortOrder(p.getSortOrder());
            vo.setQuestionCount(directCount.getOrDefault(p.getId(), 0));
            nodeMap.put(p.getId(), vo);
        }

        // 4. 按 parent_id 建立父子索引
        Map<Long, List<KPTreeNodeVO>> childrenMap = new HashMap<>();
        List<KPTreeNodeVO> roots = new ArrayList<>();
        for (KPTreeNodeVO vo : nodeMap.values()) {
            Long pid = vo.getParentId();
            if (pid == null || pid == 0L || !nodeMap.containsKey(pid)) {
                // parent_id = 0，或父节点缺失（脏数据）时，都按根节点处理，保证不丢节点
                roots.add(vo);
            } else {
                childrenMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(vo);
            }
        }

        // 5. 自顶向下挂 children，自底向上累加 questionCount（visited 防环）
        Set<Long> visited = new HashSet<>();
        for (KPTreeNodeVO root : roots) {
            attachAndSum(root, childrenMap, visited);
        }
        return roots;
    }

    /**
     * 递归挂载子节点并累加题目数，返回当前节点"含子孙"的题目合计。
     * visited 集合防止脏数据成环导致的无限递归。
     */
    private int attachAndSum(KPTreeNodeVO node, Map<Long, List<KPTreeNodeVO>> childrenMap, Set<Long> visited) {
        if (!visited.add(node.getId())) {
            return node.getQuestionCount() == null ? 0 : node.getQuestionCount();
        }
        List<KPTreeNodeVO> children = childrenMap.getOrDefault(node.getId(), Collections.emptyList());
        node.setChildren(children);

        int sum = node.getQuestionCount() == null ? 0 : node.getQuestionCount();
        for (KPTreeNodeVO child : children) {
            sum += attachAndSum(child, childrenMap, visited);
        }
        node.setQuestionCount(sum);
        return sum;
    }

    /**
     * 一次 GROUP BY 统计各知识点的题目数。
     * 返回 知识点ID -> 题目数（直接挂载，不含子孙）。
     */
    private Map<Long, Integer> countQuestionsByKnowledgePoint(String subjectCode) {
        List<Map<String, Object>> rows = questionMapper.selectMaps(
                new QueryWrapper<Question>()
                        .select("knowledge_point_id", "COUNT(*) AS cnt")
                        .eq("subject_code", subjectCode)
                        .eq("status", 1)
                        .groupBy("knowledge_point_id"));

        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object kpId = row.get("knowledge_point_id");
            Object cnt = row.get("cnt");
            if (kpId != null) {
                result.put(((Number) kpId).longValue(), cnt == null ? 0 : ((Number) cnt).intValue());
            }
        }
        return result;
    }
}
