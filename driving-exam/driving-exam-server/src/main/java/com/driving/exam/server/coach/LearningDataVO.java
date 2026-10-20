package com.driving.exam.server.coach;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.vo.AnalysisOverviewVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.KPStatVO;
import com.driving.exam.pojo.vo.WrongBookSummaryVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 教练端 · 学员学习数据（{@code GET /api/coach/students/{id}/learning-data}）。
 *
 * <p>该 VO 只承载数据，不重新计算统计口径；各字段均直接来自既有 Service：</p>
 * <ul>
 *   <li>{@link AnalysisOverviewVO}：学情总览，含正确率与已答/总题数；</li>
 *   <li>{@link WrongBookSummaryVO}：错题本分类统计（待攻克 / 高频错 / 已攻克 / 合计）；</li>
 *   <li>{@link KPStatVO}：仅保留 {@code isWeak=true} 的薄弱知识点；</li>
 *   <li>{@link ExamRecordVO}：已结束模拟考试，分页与排序沿用考试历史接口。</li>
 * </ul>
 *
 * <p><b>科目范围</b>：既有 {@code AnalysisService} 与 {@code ExamService.history} 均按科目统计，
 * 因此本接口要求显式传 {@code subjectCode}，不自行拼装"全科汇总"口径。</p>
 */
@Data
public class LearningDataVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学员用户 ID（由已验证的 coach_student 绑定记录反查，不来自前端） */
    private Long studentId;

    /** 统计科目：SUBJECT_1 ~ SUBJECT_4 */
    private String subjectCode;

    /** 学情总览：正确率、已答题数、题目总数、进度、短板数 */
    private AnalysisOverviewVO overview;

    /** 错题本分类统计；口径与学员端错题本 tab 角标一致 */
    private WrongBookSummaryVO wrongBookSummary;

    /** 薄弱知识点（仅 isWeak=true；按现有掌握度升序） */
    private List<KPStatVO> weakKnowledgePoints;

    /** 已结束模拟考试历史（仅 SUBMITTED/TIMEOUT，按交卷时间倒序） */
    private PageResult<ExamRecordVO> mockExamHistory;
}
