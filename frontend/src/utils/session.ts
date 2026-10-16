/**
 * AI 问答会话 ID 管理。
 *
 * <p>用 localStorage 作为**单一来源**，不进 Pinia：会话 ID 是**持久化**需求
 * （刷新/重开页面要保持同一会话），不是 UI 共享状态；与登录态同一个模式。</p>
 */

const SESSION_KEY = 'ai_chat_session_id'

/**
 * 取当前会话 ID：读不到就用 `crypto.randomUUID()` 生成一个并存下。
 *
 * <p>`crypto.randomUUID()` 需要安全上下文（https 或 localhost），本项目开发用 localhost，满足。</p>
 */
export function getSessionId(): string {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}

/**
 * 开始新会话：生成新 UUID 覆盖旧值。
 *
 * @returns 新的会话 ID（**需要返回值**，供 store 的 `sessionId = resetSessionId()` 直接赋值）
 */
export function resetSessionId(): string {
  const id = crypto.randomUUID()
  localStorage.setItem(SESSION_KEY, id)
  return id
}
