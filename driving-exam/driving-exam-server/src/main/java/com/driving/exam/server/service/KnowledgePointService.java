package com.driving.exam.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.vo.KPTreeNodeVO;

import java.util.List;

/**
 * 知识点 Service。
 */
public interface KnowledgePointService extends IService<KnowledgePoint> {

    /**
     * 取某科目的知识点树（章节练习导航用）。
     *
     * @param subjectCode 科目：SUBJECT_1 / SUBJECT_4
     * @return 根节点列表（parent_id = 0），children 递归；questionCount 为含子孙的题目合计
     */
    List<KPTreeNodeVO> getKnowledgeTree(String subjectCode);
}
