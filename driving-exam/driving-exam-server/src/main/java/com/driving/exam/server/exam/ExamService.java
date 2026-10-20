package com.driving.exam.server.exam;

import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.dto.ExamAnswerDTO;
import com.driving.exam.pojo.dto.ExamSubmitDTO;
import com.driving.exam.pojo.vo.ExamJudgeVO;
import com.driving.exam.pojo.vo.ExamRecordVO;
import com.driving.exam.pojo.vo.ExamReportVO;
import com.driving.exam.pojo.vo.ExamVO;
import com.driving.exam.pojo.vo.PaperVO;

import java.util.List;

/**
 * 模拟考试 Service（学员端）。
 *
 * <p>差异于 {@code QuestionService} 等实体 Service：本接口是**业务流程 Service**，
 * 不继承 {@code IService}。</p>
 *
 * <p><b>错误码约定</b>（详见各方法）：{@code PAPER_NOT_FOUND(2101)} 试卷不存在/停用；
 * {@code EXAM_SUBMITTED(2102)} 考试已交卷；{@code EXAM_NOT_FOUND(2104)} 考试不存在或不属于当前用户
 * （越权与不存在**返回同一错误码**，不泄露存在性）。</p>
 */
public interface ExamService {

    /**
     * 可用试卷列表（按科目）。
     *
     * <p>每份试卷补 {@code ongoingExamId}：若该学员在这份试卷上已有**进行中**的考试，填其 id，
     * 供前端显示「继续考试」。</p>
     */
    List<PaperVO> papers(Long userId, String subjectCode);

    /**
     * 开始考试（或断点续考）。
     *
     * <p>逻辑：若该学员在这份试卷上已有进行中的考试——
     * 未超时则**直接返回它**（断点续考）；已超时则先收尾（判分落库、置 TIMEOUT），**再新建一份**返回。</p>
     *
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code PAPER_NOT_FOUND(2101)} 试卷不存在或已停用
     */
    ExamVO start(Long userId, Long paperId);

    /**
     * 断点续考：取某场考试的当前状态（题目、剩余时间、已答）。
     *
     * <p><b>⚠️ 本方法名义上是 GET（只读语义），但"发现已超时"时会触发惰性收尾（写库）</b>——
     * 由 {@link ExamFinalizer} 以 {@code isTimeout=true} 交卷。返回的 {@code status} 会是
     * {@code TIMEOUT}，前端据此跳报告页。这是刻意的"惰性超时判定"，避免引入定时任务。</p>
     *
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code EXAM_NOT_FOUND(2104)} 考试不存在或不属于当前用户
     */
    ExamVO resume(Long userId, Long examId);

    /**
     * 考试中暂存单题答案。
     *
     * <p>校验归属、{@code status=ONGOING}、题目属于当前试卷，并在服务端判定超时后先收尾再拒绝。
     * <b>不判分、不返回对错</b>；请求延迟到达时以服务端时间为准。</p>
     *
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code EXAM_NOT_FOUND(2104)} 考试不存在或不属于当前用户；
     *         {@code EXAM_SUBMITTED(2102)} 考试已交卷/已超时；
     *         {@code PARAM_ERROR(1001)} 题目不属于当前试卷
     */
    void answer(Long userId, Long examId, ExamAnswerDTO dto);

    /**
     * 交卷判分。
     *
     * <p><b>幂等</b>：若考试已 {@code SUBMITTED / TIMEOUT}，**直接返回既有报告**（不报错、不重复写库）。</p>
     *
     * <p>答案集 = 暂存草稿 ∪ 请求体（请求体优先）；超时由后端判定，超时同样判分、仅状态记 {@code TIMEOUT}。</p>
     *
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code EXAM_NOT_FOUND(2104)} 考试不存在或不属于当前用户
     */
    ExamReportVO submit(Long userId, Long examId, ExamSubmitDTO dto);

    /**
     * 成绩报告（含逐题解析）。
     *
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code EXAM_NOT_FOUND(2104)} 考试不存在或不属于当前用户；
     *         {@code EXAM_ONGOING(2105)} 考试仍在进行中（未交卷无报告）
     */
    ExamReportVO report(Long userId, Long examId);

    /**
     * 单题判题（考试中即时反馈答题卡正误）。
     *
     * <p>取该题**服务器已保存**的答案（{@code mock_exam_answer}），用与交卷同一套
     * {@code AnswerGrader} 规则与题目标准答案比对，**只回正误**，不回标准答案 / 解析。</p>
     *
     * @throws com.driving.exam.common.exception.BusinessException
     *         {@code EXAM_NOT_FOUND(2104)} 考试不存在或不属于当前用户；
     *         {@code EXAM_SUBMITTED(2102)} 考试已交卷/超时（非进行中）；
     *         {@code PARAM_ERROR(1001)} 题目不属于当前试卷
     */
    ExamJudgeVO judge(Long userId, Long examId, Long questionId);

    /**
     * 考试历史（仅已结束的考试，按交卷时间倒序分页）。
     *
     * @param pageSize 后端会强制上限（默认 20）
     */
    PageResult<ExamRecordVO> history(Long userId, String subjectCode, int pageNum, int pageSize);

    /**
     * 扫描并收尾已超时的进行中考试（供定时任务调用）。
     *
     * <p>逐场调用现有 {@link ExamFinalizer}，由其条件抢占保证与手动交卷并发时只有一方结算。
     * 本方法不是用户接口，不改变任何人的考试归属；只处理服务端已经超过截止时间的考试。</p>
     *
     * @param limit 单次最多处理多少场
     * @return 实际完成收尾的场次数
     */
    int finalizeExpiredExams(int limit);
}
