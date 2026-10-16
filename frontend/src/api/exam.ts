import { http } from '@/utils/request'
import type { ExamJudgeVO, ExamRecordVO, ExamReportVO, ExamVO, PageResult, PaperVO } from '@/types'

/** 模拟考试模块接口前缀 */
const BASE = '/student/exam'

/**
 * 1. 可用试卷列表（按科目）。
 *
 * <p>后端 `GET /api/student/exam/papers?subjectCode=SUBJECT_1`：{@code subjectCode} 为**必填**请求参数
 * （缺参后端返回 1001）；只返回该科目下**已启用的模拟卷**，并按 {@code exam_paper.id} 升序。</p>
 *
 * <p>每份试卷带 {@code ongoingExamId}：若该学员在这份试卷上已有「进行中」的考试则填其 examId，
 * 否则为 {@code null}。前端据此把按钮显示为「继续考试」/「开始考试」。</p>
 */
export function getPapersApi(subjectCode: string) {
  return http.get<PaperVO[]>(`${BASE}/papers`, { subjectCode })
}

/**
 * 2. 开始考试（或断点续考）。
 *
 * <p>后端 `POST /api/student/exam/start`，body {@code { paperId }}。**注意**：若该学员在这份试卷上
 * 已有进行中的考试——未超时则**直接返回该场**（断点续考）；已超时则先收尾旧的、**再新建一场**返回。
 * 因此本接口**不保证每次都会新建考试**，返回值可能是「已有考试」。</p>
 */
export function startExamApi(paperId: number) {
  return http.post<ExamVO>(`${BASE}/start`, { paperId })
}

/**
 * 3. 考试历史（仅已结束的考试，按交卷时间倒序分页）。
 *
 * <p>后端 `GET /api/student/exam/history?subjectCode=&pageNum=&pageSize=`：{@code subjectCode} 为**必填**；
 * 分页参数 {@code pageNum}(默认1) / {@code pageSize}(默认10，后端上限 20)。返回 {@code PageResult<ExamRecordVO>}。</p>
 */
export function getExamHistoryApi(subjectCode: string, pageNum = 1, pageSize = 10) {
  return http.get<PageResult<ExamRecordVO>>(`${BASE}/history`, { subjectCode, pageNum, pageSize })
}

/**
 * 4. 断点续考 / 按 examId 恢复考试。
 *
 * <p>后端 `GET /api/student/exam/{examId}`，返回 {@link ExamVO}（题目 / 剩余秒数 / 已答 / 状态）。
 * <b>名义 GET，但若后端判定"已超时"会触发惰性收尾（写库）</b>，此时返回的 {@code status} 为 `TIMEOUT`。
 * 不存在或非本人返回 `2104`。</p>
 */
export function getExamApi(examId: number) {
  return http.get<ExamVO>(`${BASE}/${examId}`)
}

/**
 * 5. 暂存单题答案（考试中）。
 *
 * <p>后端 `POST /api/student/exam/{examId}/answer`，body {@code ExamAnswerDTO { questionId, userAnswer }}；
 * 返回 Void（data=null）。<b>仅 status=ONGOING 可写</b>（已交卷/超时 → 2102），且**不判分**。</p>
 *
 * <p>后端 upsert 为 `INSERT ... ON DUPLICATE KEY UPDATE`（**无版本号**）——同一题的并发写入是"后到者胜"，
 * 故**调用方需对同一题串行化**（见 {@code ExamAnswerView} 的保存队列），否则快速切换选项可能被旧请求覆盖。</p>
 */
export function saveExamAnswerApi(examId: number, questionId: number, userAnswer: string) {
  return http.post<void>(`${BASE}/${examId}/answer`, { questionId, userAnswer })
}

/**
 * 5.5 单题判题（考试中即时反馈答题卡正误）。
 *
 * <p>后端 `POST /api/student/exam/{examId}/judge`，body `{ questionId }`。判题用**服务器已保存**的答案，
 * 规则与交卷一致（`AnswerGrader`）；**只回 `{questionId, answered, isCorrect}`，不回标准答案/解析**。
 * 限制：仅进行中可判（否则 2102）；题目不属于本卷 → 1001；不存在/非本人 → 2104。</p>
 */
export function judgeExamAnswerApi(examId: number, questionId: number) {
  return http.post<ExamJudgeVO>(`${BASE}/${examId}/judge`, { questionId })
}

/**
 * 6. 交卷判分（**幂等**：已交卷直接返回既有报告，不报错、不重复写库）。
 *
 * <p>后端 `POST /api/student/exam/{examId}/submit`，body `ExamSubmitDTO { answers? }`（可空）。
 * 逐题答案已由 {@link saveExamAnswerApi} 存到服务器（`mock_exam_answer`），故这里 body 传**空对象**
 * → 服务端从草稿表读答案。是否超时由后端判定（超时同样判分、状态记 `TIMEOUT`）。返回 `ExamReportVO`。</p>
 */
export function submitExamApi(examId: number) {
  return http.post<ExamReportVO>(`${BASE}/${examId}/submit`, {})
}

/**
 * 7. 成绩报告（按 examId 重新取得**已交卷**考试的成绩）。
 *
 * <p>后端 `GET /api/student/exam/{examId}/report` → `ExamReportVO`；**进行中（ONGOING）返回 2105**，
 * 故仅在考试已结束（SUBMITTED / TIMEOUT）时调用——用于**刷新成绩页 / 直接打开已交卷考试**。</p>
 */
export function getExamReportApi(examId: number) {
  return http.get<ExamReportVO>(`${BASE}/${examId}/report`)
}
