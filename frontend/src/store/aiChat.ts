import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { AiAnswerVO, AiSessionVO } from '@/types'
import { chatApi, deleteSessionApi, getSessionMessagesApi, getSessionsApi } from '@/api/ai'
import { getSessionId, resetSessionId } from '@/utils/session'

/** 会话列表每页条数 */
const SESSION_PAGE_SIZE = 10

/** 对话消息（仅前端展示用，role 与后端 messages 的 'assistant' 无关） */
export interface ChatMessage {
  /** 'user' 用户提问 / 'ai' AI 回答 */
  role: 'user' | 'ai'
  content: string
  /** AI 消息：落库记录 ID */
  recordId?: number
  /** AI 消息：是否正在打字机显示中（组件的显示标记，store 不管动画） */
  typing?: boolean
}

/**
 * 智能问答 store（组合式写法）。
 *
 * <p><b>会话模型</b>：历史按会话组织（列表 → 进入某会话看全部消息）。
 * 点击历史会话会把 `sessionId` **切到该会话**，之后的新提问追加到同一会话；
 * 只有「开始新会话」才换新 UUID。</p>
 *
 * <p><b>打字机不在此层</b>：store 只负责完整回答与 `typing` 标记。</p>
 *
 * <p><b>错误处理</b>：接口层拦截器已统一 `ElMessage`，此处只做 loading 收尾并向上抛。</p>
 */
export const useAiChatStore = defineStore('aiChat', () => {
  /* ============================== state ============================== */
  const messages = ref<ChatMessage[]>([])
  const loading = ref(false)

  /** 历史抽屉的会话列表（与主区 messages 分离） */
  const sessionList = ref<AiSessionVO[]>([])
  /** 已加载的会话页数（从 0 开始） */
  const sessionPageNum = ref(0)
  const sessionHasMore = ref(true)

  /** 当前会话 ID（持久化在 localStorage，与登录态同模式） */
  const sessionId = ref(getSessionId())

  /* ============================== 发送提问 ============================== */
  /** 发送提问。成功返回 AI 回答正文（供打字机组件使用）；失败时异常向上抛。 */
  async function send(question: string, subjectCode?: string): Promise<string> {
    messages.value.push({ role: 'user', content: question })
    loading.value = true
    try {
      const res: AiAnswerVO = await chatApi({ question, subjectCode, sessionId: sessionId.value })
      messages.value.push({ role: 'ai', content: res.answer, recordId: res.recordId, typing: true })
      return res.answer
    } finally {
      loading.value = false
    }
  }

  /* ============================== 会话列表 ============================== */
  /** 内部：拉取会话列表（reset=true 重置为第一页；false 追加下一页） */
  async function fetchSessions(reset: boolean): Promise<void> {
    const nextPage = reset ? 1 : sessionPageNum.value + 1
    const res = await getSessionsApi({ pageNum: nextPage, pageSize: SESSION_PAGE_SIZE })
    if (reset) {
      sessionList.value = res.records
    } else {
      sessionList.value.push(...res.records)
    }
    sessionPageNum.value = nextPage
    sessionHasMore.value = nextPage < res.pages
  }

  /** 抽屉打开时调用：重置并加载第一页 */
  async function loadSessions(): Promise<void> {
    await fetchSessions(true)
  }

  /** 加载更多（pageNum 累加，不重置） */
  async function loadMoreSessions(): Promise<void> {
    await fetchSessions(false)
  }

  /* ============================== 会话消息 ============================== */
  /** 拉取某会话的全部消息，替换主区 messages（user + ai 交替） */
  async function loadSessionMessages(sid: string): Promise<void> {
    const records = await getSessionMessagesApi(sid)
    messages.value = records.flatMap((r) => [
      { role: 'user' as const, content: r.question },
      { role: 'ai' as const, content: r.answer, recordId: r.id },
    ])
  }

  /**
   * 点击历史会话：主区切到该会话。
   *
   * <p>先加载消息、成功后再切 `sessionId`——若加载失败则不切，避免"消息没变但 sessionId 已跳"的错乱。</p>
   */
  async function loadFromSession(session: AiSessionVO): Promise<void> {
    await loadSessionMessages(session.sessionId)
    sessionId.value = session.sessionId
  }

  /* ============================== 删除 ============================== */
  /** 删除整个会话；若删的是**当前正在看的**会话，则清空主区并换新 sessionId */
  async function deleteSession(sid: string): Promise<void> {
    await deleteSessionApi(sid)
    sessionList.value = sessionList.value.filter((s) => s.sessionId !== sid)
    if (sid === sessionId.value) {
      messages.value = []
      sessionId.value = resetSessionId()
    }
  }

  /* ============================== 会话 ============================== */
  /** 开始新会话：清空消息区 + 换一个 sessionId */
  function startNewSession(): void {
    messages.value = []
    sessionId.value = resetSessionId()
  }

  return {
    messages,
    loading,
    sessionList,
    sessionPageNum,
    sessionHasMore,
    sessionId,
    send,
    loadSessions,
    loadMoreSessions,
    loadSessionMessages,
    loadFromSession,
    deleteSession,
    startNewSession,
  }
})
