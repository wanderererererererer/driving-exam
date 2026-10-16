import { http } from '@/utils/request'
import type { AiAnswerVO, AiRecordVO, AiSessionVO, PageResult } from '@/types'

/** AI 问答模块接口前缀 */
const BASE = '/student/ai'

/**
 * 1. 提问并获取 AI 回答。
 *
 * <p>`question` 后端限制 ≤500 字；`sessionId` 由前端传入（同一次对话持续复用），落库即归属该会话。
 * AI 调用失败/超限由后端返回对应 code，拦截器已统一弹提示。</p>
 */
export function chatApi(data: { question: string; subjectCode?: string; sessionId?: string }) {
  return http.post<AiAnswerVO>(`${BASE}/chat`, data)
}

/** 2. 会话列表（分页，按最后消息时间倒序） */
export function getSessionsApi(params: { pageNum: number; pageSize: number }) {
  return http.get<PageResult<AiSessionVO>>(`${BASE}/sessions`, params)
}

/** 3. 某个会话的全部消息（时间升序） */
export function getSessionMessagesApi(sessionId: string) {
  return http.get<AiRecordVO[]>(`${BASE}/sessions/${sessionId}`)
}

/** 4. 删除整个会话（物理删除） */
export function deleteSessionApi(sessionId: string) {
  return http.delete<void>(`${BASE}/sessions/${sessionId}`)
}
