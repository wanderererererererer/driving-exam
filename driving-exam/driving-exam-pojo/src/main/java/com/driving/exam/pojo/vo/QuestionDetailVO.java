package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题目详情（单题查看用）。
 *
 * <p>字段与 {@link QuestionVO} 一致，额外带知识点名称；同样
 * <b>不含 answer 与 analysis</b>。</p>
 */
@Data
public class QuestionDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 科目：SUBJECT_1 / SUBJECT_4 */
    private String subjectCode;

    /** 题型：SINGLE / MULTIPLE / JUDGE */
    private String questionType;

    /** 题干 */
    private String content;

    /** 难度 1~5 */
    private Integer difficulty;

    /** 图片/视频 URL，可空 */
    private String mediaUrl;

    /** 所属知识点 ID */
    private Long knowledgePointId;

    /** 所属知识点名称 */
    private String knowledgePointName;

    /** 选项（判断题可为空） */
    private List<QuestionOptionVO> options;
}
