package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 成绩报告的逐题结果（交卷后展示，**含正确答案、选项与解析**）。
 *
 * <p>报告页只有题干是不够的——题干通常不含选项内容，只给"正确答案：A"用户无从对照。
 * 因此这里带上 {@code options}，让报告能完整渲染"题干 + 四个选项 + 你的答案 + 解析"。</p>
 */
@Data
public class ExamReportItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long questionId;

    /** 题型：SINGLE / MULTIPLE / JUDGE（报告页用于显示题型标签） */
    private String questionType;

    /** 题干 */
    private String content;

    /** 选项列表（**复用刷题模块的 {@link QuestionOptionVO}**；判断题没有选项，为空） */
    private List<QuestionOptionVO> options;

    /** 用户答案；未作答时为 null */
    private String userAnswer;

    /** 正确答案 */
    private String correctAnswer;

    /**
     * 是否答对。
     *
     * <p>同样用包装类型 {@code Boolean}——理由同 {@link ExamReportVO#getIsPass()}（JSON 键须为 {@code isCorrect}）。
     * 未作答按 {@code false}。</p>
     */
    private Boolean isCorrect;

    /** 解析 */
    private String analysis;
}
