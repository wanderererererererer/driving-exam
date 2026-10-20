import axios from 'axios'
import { http } from '@/utils/request'
import { getToken } from '@/utils/auth'
import type {
  AppointmentStatus,
  AppointmentVO,
  KPTreeNodeVO,
  PageResult,
} from '@/types'

/**
 * 管理员端接口。
 *
 * <p>管理员身份由 JWT 识别；后端 `/api/admin/**` 由路径规则限制为 ADMIN。
 * 预约接口只读；不提供审批/拒绝/完成/取消。</p>
 */

const BASE = '/admin'

/* ============================== 类型 ============================== */

export interface AdminDashboardVO {
  userCount: number
  questionCount: number
  appointmentCount: number
  aiChatCount: number
}

export interface AdminAiStatsVO {
  totalCount: number
  todayCount: number
  distinctUsers: number
}

export type AdminRole = 'STUDENT' | 'COACH' | 'ADMIN'

export interface AdminUserVO {
  id: number
  username: string
  realName: string | null
  phone: string | null
  avatar: string | null
  role: AdminRole
  status: number
}

export interface AdminUserCreateParams {
  username: string
  password: string
  realName?: string
  phone?: string
  role: AdminRole
  status?: number
}

export interface AdminUserUpdateParams {
  realName?: string
  phone?: string
  role?: AdminRole
  status?: number
}

export interface AdminBindingVO {
  bindId: number
  coachId: number
  coachName: string | null
  coachPhone: string | null
  studentId: number
  studentName: string | null
  studentPhone: string | null
  status: number
  bindTime: string
}

export interface AdminKnowledgePointParams {
  subjectCode: string
  parentId: number
  name: string
  sortOrder?: number
  description?: string
}

export interface AdminQuestionOptionVO {
  optionKey: string
  optionContent: string
  isCorrect: number
  sortOrder: number
}

export interface AdminQuestionVO {
  id: number
  subjectCode: string
  knowledgePointId: number
  questionType: 'SINGLE' | 'MULTIPLE' | 'JUDGE'
  content: string
  answer: string
  analysis: string | null
  difficulty: number
  mediaUrl: string | null
  status: number
  options: AdminQuestionOptionVO[]
}

export interface AdminQuestionOptionParams {
  optionKey: string
  optionContent: string
  sortOrder?: number
}

export interface AdminQuestionParams {
  subjectCode: string
  knowledgePointId: number
  questionType: 'SINGLE' | 'MULTIPLE' | 'JUDGE'
  content: string
  answer: string
  analysis?: string
  difficulty?: number
  mediaUrl?: string
  status?: number
  options: AdminQuestionOptionParams[]
}

export interface AdminPaperQuestionVO {
  questionId: number
  score: number
  sortOrder: number
}

export interface AdminPaperVO {
  id: number
  subjectCode: string
  title: string
  paperType: string
  totalScore: number
  passScore: number
  duration: number
  questionCount: number
  status: number
  questions?: AdminPaperQuestionVO[]
}

export interface AdminPaperParams {
  subjectCode: string
  title: string
  paperType?: string
  totalScore?: number
  passScore?: number
  duration?: number
  status?: number
  questions: AdminPaperQuestionVO[]
}

export interface AdminImportErrorVO {
  row: number
  field: string
  message: string
}

export interface AdminImportResultVO {
  total: number
  success: number
  failed: number
  errors: AdminImportErrorVO[]
}

/* ============================== 概览 / AI 统计 ============================== */

export function getAdminDashboardApi() {
  return http.get<AdminDashboardVO>(`${BASE}/dashboard`)
}

export function getAdminAiStatsApi() {
  return http.get<AdminAiStatsVO>(`${BASE}/ai/stats`)
}

/* ============================== 用户 ============================== */

export function listAdminUsersApi(params: {
  role?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AdminUserVO>>(`${BASE}/users`, params)
}

export function getAdminUserApi(id: number) {
  return http.get<AdminUserVO>(`${BASE}/users/${id}`)
}

export function createAdminUserApi(data: AdminUserCreateParams) {
  return http.post<void>(`${BASE}/users`, data)
}

export function updateAdminUserApi(id: number, data: AdminUserUpdateParams) {
  return http.put<void>(`${BASE}/users/${id}`, data)
}

export function setAdminUserStatusApi(id: number, status: number) {
  return http.put<void>(`${BASE}/users/${id}/status`, { status })
}

/* ============================== 绑定 ============================== */

export function listAdminBindingsApi(params: {
  status?: number
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AdminBindingVO>>(`${BASE}/bindings`, params)
}

export function createAdminBindingApi(data: { coachId: number; studentId: number }) {
  return http.post<void>(`${BASE}/bindings`, data)
}

export function unbindAdminApi(bindId: number) {
  return http.delete<void>(`${BASE}/bindings/${bindId}`)
}

/* ============================== 知识点 ============================== */

export function getAdminKnowledgeTreeApi(subjectCode: string) {
  return http.get<KPTreeNodeVO[]>(`${BASE}/knowledge-points`, { subjectCode })
}

export function createAdminKnowledgePointApi(data: AdminKnowledgePointParams) {
  return http.post<void>(`${BASE}/knowledge-points`, data)
}

export function updateAdminKnowledgePointApi(id: number, data: AdminKnowledgePointParams) {
  return http.put<void>(`${BASE}/knowledge-points/${id}`, data)
}

export function deleteAdminKnowledgePointApi(id: number) {
  return http.delete<void>(`${BASE}/knowledge-points/${id}`)
}

/* ============================== 题库 ============================== */

export function listAdminQuestionsApi(params: {
  subjectCode?: string
  type?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AdminQuestionVO>>(`${BASE}/questions`, params)
}

export function getAdminQuestionApi(id: number) {
  return http.get<AdminQuestionVO>(`${BASE}/questions/${id}`)
}

export function createAdminQuestionApi(data: AdminQuestionParams) {
  return http.post<number>(`${BASE}/questions`, data)
}

export function updateAdminQuestionApi(id: number, data: AdminQuestionParams) {
  return http.put<void>(`${BASE}/questions/${id}`, data)
}

export function setAdminQuestionStatusApi(id: number, status: number) {
  return http.put<void>(`${BASE}/questions/${id}/status`, { status })
}

export function deleteAdminQuestionApi(id: number) {
  return http.delete<void>(`${BASE}/questions/${id}`)
}

export function importAdminQuestionsApi(file: File) {
  const form = new FormData()
  form.append('file', file)
  // 不手动设置 Content-Type，让浏览器生成 multipart boundary
  return http.post<AdminImportResultVO>(`${BASE}/questions/import`, form)
}

/** 模板下载返回二进制，不能走 ApiResponse 拦截器，单独使用 Axios。 */
export function downloadQuestionTemplateApi() {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return axios
    .get(`${base}${BASE}/questions/template`, {
      responseType: 'blob',
      headers: { Authorization: `Bearer ${getToken()}` },
    })
    .then((res) => res.data as Blob)
}

/* ============================== 试卷 ============================== */

export function listAdminPapersApi(params: {
  subjectCode?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AdminPaperVO>>(`${BASE}/papers`, params)
}

export function getAdminPaperApi(id: number) {
  return http.get<AdminPaperVO>(`${BASE}/papers/${id}`)
}

export function createAdminPaperApi(data: AdminPaperParams) {
  return http.post<number>(`${BASE}/papers`, data)
}

export function updateAdminPaperApi(id: number, data: AdminPaperParams) {
  return http.put<void>(`${BASE}/papers/${id}`, data)
}

export function setAdminPaperStatusApi(id: number, status: number) {
  return http.put<void>(`${BASE}/papers/${id}/status`, { status })
}

/* ============================== 预约（只读） ============================== */

export function listAdminAppointmentsApi(params: {
  status?: AppointmentStatus
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AppointmentVO>>(`${BASE}/appointments`, params)
}
