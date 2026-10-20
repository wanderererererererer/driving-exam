import { http } from '@/utils/request'
import type {
  AiSuggestVO,
  CoachConfirmStatus,
  LearningDataVO,
  PageResult,
  StudentBindParams,
  StudentUpdateParams,
  StudentVO,
  SubjectCode,
  SubjectStatusVO,
  WrongBookCategory,
  WrongBookItemVO,
} from '@/types'

/**
 * 教练端 · 学员管理接口。
 *
 * <p>教练身份由 JWT 识别，前端不传 `userId`；路径变量 `{bindId}` 是
 * `coach_student.id`，不是学员的 `studentId`。</p>
 *
 * <p>列表/状态/学习详情（学情、错题、AI 建议）为本阶段页面使用；绑定/创建、改学情、解绑为
 * 既定写操作，遵循后端 `StudentBindDTO` / `StudentUpdateDTO` 的真实契约。</p>
 */

const BASE = '/coach/students'

/** 我的学员列表（分页；后端不支持关键字/科目筛选） */
export function listMyStudentsApi(params: { pageNum?: number; pageSize?: number }) {
  return http.get<PageResult<StudentVO>>(BASE, params)
}

/** 学员四科学情状态（只读；`bindId` 为 `coach_student.id`） */
export function getStudentStatusApi(bindId: number) {
  return http.get<SubjectStatusVO[]>(`${BASE}/${bindId}/status`)
}

/**
 * 绑定已有学员或创建学员账号并绑定（后端同一入口）。
 *
 * <p>`username` 已存在：仅绑定，姓名/手机号/密码可省略；
 * `username` 不存在：创建学员账号，此时 realName/phone/password 必填。</p>
 */
export function bindOrCreateStudentApi(data: StudentBindParams) {
  return http.post<void>(BASE, data)
}

/**
 * 修改学员某科目的学情阶段 / 教练备注。
 *
 * <p>`subjectCode` 必填，`phaseStatus` 与 `coachRemark` 至少传一个；
 * 未修改的可选字段应省略，不要传空字符串。</p>
 */
export function updateStudentSubjectApi(bindId: number, data: StudentUpdateParams) {
  return http.put<void>(`${BASE}/${bindId}`, data)
}

/** 解除绑定（软解绑；不删除历史数据，不级联取消预约） */
export function unbindStudentApi(bindId: number) {
  return http.delete<void>(`${BASE}/${bindId}`)
}

/** 学习数据（学情总览、错题汇总、薄弱知识点、模拟考历史） */
export function getStudentLearningDataApi(bindId: number, subjectCode: SubjectCode) {
  return http.get<LearningDataVO>(`${BASE}/${bindId}/learning-data`, { subjectCode })
}

/** 学员错题列表（分页；分类口径与学员端一致） */
export function getStudentWrongBookApi(
  bindId: number,
  params: {
    subjectCode?: SubjectCode
    category?: WrongBookCategory
    pageNum?: number
    pageSize?: number
  },
) {
  return http.get<PageResult<WrongBookItemVO>>(`${BASE}/${bindId}/wrong-book`, params)
}

/** 读取已保存的 AI/规则建议（无建议时 data=null；不触发生成） */
export function getStudentAiSuggestApi(bindId: number, subjectCode: SubjectCode) {
  return http.get<AiSuggestVO | null>(`${BASE}/${bindId}/ai-suggest`, { subjectCode })
}

/** 生成 AI 学习建议（AI 失败时后端返回规则降级结果，HTTP 仍为成功） */
export function generateStudentAiSuggestApi(bindId: number, subjectCode: SubjectCode) {
  return http.post<AiSuggestVO>(`${BASE}/${bindId}/ai-suggest`, undefined, {
    params: { subjectCode },
    // 后端 AI 读超时为 60s；前端默认 15s 会先中止，给足等待降级结果的时间
    timeout: 70000,
  })
}

/** 教练确认当前学习建议 */
export function confirmStudentSuggestionApi(
  bindId: number,
  subjectCode: SubjectCode,
  confirmStatus: CoachConfirmStatus,
) {
  return http.put<void>(
    `${BASE}/${bindId}/confirm`,
    { confirmStatus },
    { params: { subjectCode } },
  )
}

