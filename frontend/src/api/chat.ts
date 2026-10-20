import { http } from '@/utils/request'
import type { ChatMessageVO, ChatRole, ChatSessionVO } from '@/types'

/**
 * 教练-学员聊天接口（学员 / 教练共用同一套封装）。
 *
 * <p>身份由后端 JWT 识别，前端**不传** `senderId` / `senderRole` / 用户 ID。
 * 角色只决定路径前缀：`student → /student/chat`、`coach → /coach/chat`。</p>
 */

export interface ChatMessageQuery {
  /** 轮询新消息：当前已知最大消息 ID；首屏不要传 */
  lastId?: number
  /** 加载更早消息：当前最小消息 ID；与 lastId 互斥 */
  beforeId?: number
  /** 每页条数；后端默认 20、上限 50 */
  pageSize?: number
}

function base(role: ChatRole): string {
  return `/${role}/chat`
}

/** 获取或创建与指定对方用户的会话；必须先有有效绑定，否则后端返回 3102 */
export function getOrCreateChatSessionApi(role: ChatRole, peerId: number) {
  const body = role === 'student' ? { coachId: peerId } : { studentId: peerId }
  return http.post<ChatSessionVO>(`${base(role)}/sessions`, body)
}

/** 会话列表（含解绑后的历史会话，canSend=false） */
export function listChatSessionsApi(role: ChatRole) {
  return http.get<ChatSessionVO[]>(`${base(role)}/sessions`)
}

/**
 * 查询消息。
 *
 * <p>不传游标时返回最近一页；`lastId` 查询更新消息，`beforeId` 查询更早消息，
 * 两者不能同时传。返回按消息 ID 正序。</p>
 */
export function getChatMessagesApi(
  role: ChatRole,
  sessionId: number,
  params: ChatMessageQuery = {},
  signal?: AbortSignal,
) {
  return http.get<ChatMessageVO[]>(
    `${base(role)}/sessions/${sessionId}/messages`,
    params,
    { signal },
  )
}

/** 发送纯文本消息；请求体只有 content，身份由 JWT 决定 */
export function sendChatMessageApi(role: ChatRole, sessionId: number, content: string) {
  return http.post<ChatMessageVO>(`${base(role)}/sessions/${sessionId}/messages`, { content })
}

/** 将当前会话中对方发来的消息标记为已读，并清零自己的未读数 */
export function markChatSessionReadApi(role: ChatRole, sessionId: number) {
  return http.put<void>(`${base(role)}/sessions/${sessionId}/read`)
}
