package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识点树节点（章节练习导航用）。
 *
 * <p>由扁平的 knowledge_point 列表按 parent_id 组装而成，children 递归。</p>
 */
@Data
public class KPTreeNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 知识点名称 */
    private String name;

    /** 父知识点 ID，0 为根 */
    private Long parentId;

    /** 层级 */
    private Integer level;

    /** 排序 */
    private Integer sortOrder;

    /** 知识点描述（管理端编辑回显和 AI/展示素材使用） */
    private String description;

    /** 该知识点（含子孙知识点）下的题目数量；建树时由一次 GROUP BY 查询结果填充 */
    private Integer questionCount;

    /** 子节点 */
    private List<KPTreeNodeVO> children = new ArrayList<>();
}
