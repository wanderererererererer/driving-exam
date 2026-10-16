package com.driving.exam.pojo.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题目（列表 / 刷题展示用）。
 *
 * <p><b>刻意不含 answer 与 analysis</b>，正确答案与解析只在提交判分后由
 * {@code AnswerResultVO} 下发。</p>
 */
@Data
public class QuestionVO implements Serializable {

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

    /** 选项（判断题可为空，前端用"对/错"两个按钮呈现） */
    private List<QuestionOptionVO> options;
}
