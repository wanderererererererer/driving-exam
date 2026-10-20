<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { listMyCoachesApi } from '@/api/appointment'
import { listMyStudentsApi } from '@/api/coachStudent'
import {
  getChatMessagesApi,
  getOrCreateChatSessionApi,
  listChatSessionsApi,
  markChatSessionReadApi,
  sendChatMessageApi,
} from '@/api/chat'
import type {
  ChatMessageVO,
  ChatRole,
  ChatSenderRole,
  ChatSessionVO,
  CoachVO,
  StudentVO,
} from '@/types'

/**
 * 教练-学员聊天页（学员端 / 教练端共用）。
 *
 * <p>路由通过 props 传入 `role`，只改变文案、对方选择来源和 API 前缀，
 * 不维护两套页面逻辑。</p>
 *
 * <p>消息加载遵循后端游标规则：首屏取最近一页，`beforeId` 加载更早消息，
 * `lastId` 轮询新消息；消息按 ID 去重并保持正序。轮询使用递归 setTimeout，
 * 切换会话、组件卸载时清理，旧请求通过 AbortController + 会话序号双重隔离。</p>
 */
const props = defineProps<{ role: ChatRole }>()

const PAGE_SIZE = 20
const POLL_PAGE_SIZE = 50
const POLL_INTERVAL_MS = 4000
const POLL_MAX_FAILURES = 3
const MAX_MESSAGE_LENGTH = 1000
const COACH_STUDENT_PAGE_SIZE = 20
const MAX_COACH_STUDENT_PAGES = 20

const isStudent = computed(() => props.role === 'student')
const ownRole = computed<ChatSenderRole>(() => (isStudent.value ? 'STUDENT' : 'COACH'))
const title = computed(() => (isStudent.value ? '教练聊天' : '学员消息'))
const subtitle = computed(() =>
  isStudent.value ? '与绑定教练沟通练车安排' : '与绑定学员沟通学习安排',
)
const peerNoun = computed(() => (isStudent.value ? '教练' : '学员'))
const newDialogTitle = computed(() => `发起${peerNoun.value}会话`)

interface PeerOption {
  id: number
  label: string
  phone: string | null
}

/* ------------------------------- 会话列表 ------------------------------- */

const sessions = ref<ChatSessionVO[]>([])
const sessionsLoading = ref(false)
const sessionsError = ref(false)
const activeSessionId = ref<number | null>(null)

const activeSession = computed(
  () => sessions.value.find((session) => session.id === activeSessionId.value) ?? null,
)

let sessionsSeq = 0

/**
 * 当前"用户主动查看"的会话 ID。
 * 自动选中首个会话只用于展示历史消息，不算用户查看；
 * 只有用户点击会话、重试进入、或发起会话成功后，才允许触发已读。
 */
const viewedSessionId = ref<number | null>(null)

/**
 * 当前会话的已读请求是否成功。
 * viewedSessionId 只表示用户主动看过；本状态单独表示服务端已读成功，
 * 失败时保持 false，用户再次点击当前会话可以重试。
 */
const activeSessionRead = ref(false)

async function loadSessions(options: { silent?: boolean; autoSelect?: boolean } = {}): Promise<void> {
  const seq = ++sessionsSeq
  if (!options.silent) {
    sessionsLoading.value = true
    sessionsError.value = false
  }

  try {
    const data = await listChatSessionsApi(props.role)
    if (seq !== sessionsSeq) return

    sessions.value = data
    if (data.length === 0) {
      clearActiveSession()
      return
    }

    const activeExists =
      activeSessionId.value !== null && data.some((session) => session.id === activeSessionId.value)
    if (!activeExists && options.autoSelect !== false) {
      // 自动选中仅展示消息，不标记已读。
      await selectSession(data[0], false, false)
    }
  } catch {
    if (seq !== sessionsSeq || options.silent) return
    // 错误提示由 request.ts 拦截器统一弹出；这里只落地错误态。
    sessions.value = []
    sessionsError.value = true
    clearActiveSession()
  } finally {
    if (seq === sessionsSeq && !options.silent) {
      sessionsLoading.value = false
    }
  }
}

async function reloadSessions(): Promise<void> {
  await loadSessions()
}

/* ------------------------------- 消息区 ------------------------------- */

const messages = ref<ChatMessageVO[]>([])
const messagesLoading = ref(false)
const messagesError = ref(false)
const loadingOlder = ref(false)
const noMore = ref(false)
const sending = ref(false)
const input = ref('')
const sendError = ref('')
const pollError = ref(false)
const messageScroll = ref<HTMLElement | null>(null)

let conversationSeq = 0
let messageAbort: AbortController | null = null
let pollTimer: number | null = null
let pollFailures = 0

const lastMessageId = computed(() =>
  messages.value.length > 0 ? messages.value[messages.value.length - 1].id : 0,
)
const canSend = computed(() => activeSession.value?.canSend === true)
const canSubmit = computed(
  () => canSend.value && !sending.value && input.value.trim().length > 0,
)

function isOwnMessage(message: ChatMessageVO): boolean {
  return message.senderRole === ownRole.value
}

function mergeMessages(incoming: ChatMessageVO[], mode: 'replace' | 'append' | 'prepend'): void {
  const byId = new Map<number, ChatMessageVO>()
  if (mode !== 'replace') {
    for (const message of messages.value) byId.set(message.id, message)
  }
  for (const message of incoming) byId.set(message.id, message)
  messages.value = [...byId.values()].sort((a, b) => a.id - b.id)
}

/** 发送/收到消息后立即更新左侧摘要，随后再用服务端列表校准顺序与未读数 */
function updateActiveSummary(message: ChatMessageVO): void {
  const target = activeSession.value
  if (!target || target.id !== message.sessionId) return
  target.lastMessage = message.content
  target.lastMessageTime = message.createTime
}

function isNearBottom(): boolean {
  const el = messageScroll.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

async function scrollToBottom(force = false): Promise<void> {
  await nextTick()
  const el = messageScroll.value
  if (el && (force || isNearBottom())) {
    el.scrollTop = el.scrollHeight
  }
}

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

function abortMessageRequest(): void {
  messageAbort?.abort()
  messageAbort = null
}

function clearActiveSession(): void {
  conversationSeq += 1
  stopPolling()
  abortMessageRequest()
  activeSessionId.value = null
  viewedSessionId.value = null
  activeSessionRead.value = false
  messages.value = []
  messagesLoading.value = false
  messagesError.value = false
  loadingOlder.value = false
  noMore.value = false
  sending.value = false
  input.value = ''
  sendError.value = ''
  pollError.value = false
  pollFailures = 0
}

async function selectSession(session: ChatSessionVO, force = false, markRead = false): Promise<void> {
  if (!force && activeSessionId.value === session.id) return

  stopPolling()
  abortMessageRequest()
  const seq = ++conversationSeq
  const controller = new AbortController()
  messageAbort = controller

  activeSessionId.value = session.id
  viewedSessionId.value = markRead ? session.id : null
  activeSessionRead.value = false
  messages.value = []
  messagesLoading.value = true
  messagesError.value = false
  loadingOlder.value = false
  noMore.value = false
  sending.value = false
  input.value = ''
  sendError.value = ''
  pollError.value = false
  pollFailures = 0

  try {
    const data = await getChatMessagesApi(
      props.role,
      session.id,
      { pageSize: PAGE_SIZE },
      controller.signal,
    )
    if (seq !== conversationSeq) return

    mergeMessages(data, 'replace')
    noMore.value = data.length < PAGE_SIZE
    messagesLoading.value = false
    await scrollToBottom(true)
    if (markRead) {
      // 仅用户主动查看时标记已读；成功后重新拉列表获取服务端最新未读数。
      const notified = await markReadIfNeeded(session.id, seq)
      if (notified && seq === conversationSeq) {
        void loadSessions({ silent: true })
      }
    }
    startPolling(session.id, seq)
  } catch {
    if (controller.signal.aborted || seq !== conversationSeq) return
    messagesError.value = true
    messagesLoading.value = false
  } finally {
    if (seq === conversationSeq) messagesLoading.value = false
  }
}

function onSessionClick(session: ChatSessionVO): void {
  if (session.id === activeSessionId.value) {
    // 点击当前自动选中的会话：补一次主动已读，否则用户"查看"不会生效。
    // 只有已读接口成功才置 activeSessionRead；失败后再次点击仍可重试。
    if (!activeSessionRead.value) {
      viewedSessionId.value = session.id
      void markReadIfNeeded(session.id, conversationSeq).then((notified) => {
        if (notified && activeSessionId.value === session.id) {
          void loadSessions({ silent: true })
        }
      })
    }
    return
  }
  void selectSession(session, false, true)
}

async function retryMessages(): Promise<void> {
  const session = activeSession.value
  if (session) await selectSession(session, true, true)
}

function startPolling(sessionId: number, seq: number): void {
  stopPolling()
  pollFailures = 0
  pollError.value = false
  schedulePoll(sessionId, seq)
}

function schedulePoll(sessionId: number, seq: number): void {
  stopPolling()
  pollTimer = window.setTimeout(() => {
    void pollNewMessages(sessionId, seq)
  }, POLL_INTERVAL_MS)
}

async function pollNewMessages(sessionId: number, seq: number): Promise<void> {
  if (seq !== conversationSeq || activeSessionId.value !== sessionId) return

  const controller = messageAbort
  if (!controller || controller.signal.aborted) return

  try {
    const data = await getChatMessagesApi(
      props.role,
      sessionId,
      { lastId: lastMessageId.value, pageSize: POLL_PAGE_SIZE },
      controller.signal,
    )
    if (seq !== conversationSeq || activeSessionId.value !== sessionId) return

    pollFailures = 0
    pollError.value = false
    if (data.length === 0) return

    const shouldFollow = isNearBottom()
    mergeMessages(data, 'append')
    const latest = data[data.length - 1]
    if (latest) updateActiveSummary(latest)
    if (shouldFollow) await scrollToBottom(true)

    if (
      viewedSessionId.value === sessionId &&
      data.some((message) => message.senderRole !== ownRole.value && !message.isRead)
    ) {
      // 这一批里有新的对方未读消息，当前批次尚未确认已读；
      // 已读失败时保持 false，用户再次点击当前会话仍可重试。
      activeSessionRead.value = false
      // 先等待已读提交，再刷新会话列表；避免 GET 先于 PUT 返回旧未读数。
      await markReadIfNeeded(sessionId, seq)
      if (seq !== conversationSeq || activeSessionId.value !== sessionId) return
    }
    // 会话摘要、排序与未读数以服务端为准；静默刷新不改变当前选中会话。
    void loadSessions({ silent: true })
  } catch {
    if (controller.signal.aborted || seq !== conversationSeq) return
    pollFailures += 1
    if (pollFailures >= POLL_MAX_FAILURES) {
      pollError.value = true
      stopPolling()
    }
  } finally {
    if (
      seq === conversationSeq &&
      activeSessionId.value === sessionId &&
      pollFailures < POLL_MAX_FAILURES
    ) {
      schedulePoll(sessionId, seq)
    }
  }
}

function retryPolling(): void {
  if (!activeSession.value) return
  pollFailures = 0
  pollError.value = false
  startPolling(activeSession.value.id, conversationSeq)
}

async function loadOlder(): Promise<void> {
  const session = activeSession.value
  if (!session || loadingOlder.value || noMore.value || messages.value.length === 0) return

  const firstId = messages.value[0].id
  const seq = conversationSeq
  const el = messageScroll.value
  const previousHeight = el?.scrollHeight ?? 0
  const previousTop = el?.scrollTop ?? 0

  loadingOlder.value = true
  try {
    const data = await getChatMessagesApi(
      props.role,
      session.id,
      { beforeId: firstId, pageSize: PAGE_SIZE },
      messageAbort?.signal,
    )
    if (seq !== conversationSeq || activeSessionId.value !== session.id) return
    if (data.length === 0) {
      noMore.value = true
      return
    }

    mergeMessages(data, 'prepend')
    if (data.length < PAGE_SIZE) noMore.value = true

    await nextTick()
    if (el) {
      el.scrollTop = el.scrollHeight - previousHeight + previousTop
    }
  } catch {
    // 取消（切换会话/卸载）静默；其他错误已由拦截器提示。
  } finally {
    if (seq === conversationSeq) loadingOlder.value = false
  }
}

async function markReadIfNeeded(sessionId: number, seq: number): Promise<boolean> {
  try {
    await markChatSessionReadApi(props.role, sessionId)

    const target = sessions.value.find((session) => session.id === sessionId)
    if (target) target.unreadCount = 0

    if (seq !== conversationSeq || activeSessionId.value !== sessionId) return true
    activeSessionRead.value = true
    for (const message of messages.value) {
      if (message.senderRole !== ownRole.value) message.isRead = true
    }
    return true
  } catch {
    // 已读失败不伪造本地状态；activeSessionRead 保持 false，允许再次点击重试。
    return false
  }
}

/* -------------------------------- 发送 -------------------------------- */

async function sendMessage(): Promise<void> {
  const session = activeSession.value
  if (!session || !session.canSend || sending.value) return

  const content = input.value.trim()
  if (!content) {
    ElMessage.warning('消息内容不能为空')
    return
  }
  if ([...content].length > MAX_MESSAGE_LENGTH) {
    ElMessage.warning(`消息内容不能超过 ${MAX_MESSAGE_LENGTH} 字`)
    return
  }

  const sessionId = session.id
  const seq = conversationSeq
  sending.value = true
  sendError.value = ''

  try {
    const message = await sendChatMessageApi(props.role, sessionId, content)
    if (seq !== conversationSeq || activeSessionId.value !== sessionId) return

    mergeMessages([message], 'append')
    updateActiveSummary(message)
    input.value = ''
    await scrollToBottom(true)
    void loadSessions({ silent: true })
  } catch {
    if (seq === conversationSeq && activeSessionId.value === sessionId) {
      sendError.value = '发送失败，请稍后重试'
      // 发送失败可能由解绑造成：刷新会话，canSend 以服务端结果为准。
      await loadSessions({ silent: true })
      if (activeSession.value && !activeSession.value.canSend) {
        sendError.value = '当前绑定已解除，只能查看历史消息'
      }
    }
  } finally {
    sending.value = false
  }
}

function onKeydown(event: KeyboardEvent): void {
  // 中文输入法候选回车不发送；Shift+Enter 保留换行。
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void sendMessage()
  }
}

/* ------------------------------ 发起新会话 ------------------------------ */

const newDialogVisible = ref(false)
const peerOptions = ref<PeerOption[]>([])
const peerLoading = ref(false)
const peerError = ref(false)
const selectedPeerId = ref<number | null>(null)
const creating = ref(false)
let peerSeq = 0

async function loadPeerOptions(): Promise<void> {
  const seq = ++peerSeq
  peerLoading.value = true
  peerError.value = false
  peerOptions.value = []

  try {
    const options = isStudent.value
      ? (await listMyCoachesApi()).map(coachOption)
      : await loadStudentOptions()
    if (seq !== peerSeq) return
    peerOptions.value = options
  } catch {
    if (seq !== peerSeq) return
    peerError.value = true
  } finally {
    if (seq === peerSeq) peerLoading.value = false
  }
}

function coachOption(coach: CoachVO): PeerOption {
  const name = coach.realName || `教练 #${coach.coachId}`
  return {
    id: coach.coachId,
    label: coach.phone ? `${name}（${coach.phone}）` : name,
    phone: coach.phone,
  }
}

async function loadStudentOptions(): Promise<PeerOption[]> {
  const options: PeerOption[] = []
  let page = 1
  let pages = 1

  // 教练学员接口每页上限 20；循环拉取，避免只显示第一页学员。
  while (page <= pages && page <= MAX_COACH_STUDENT_PAGES) {
    const result = await listMyStudentsApi({
      pageNum: page,
      pageSize: COACH_STUDENT_PAGE_SIZE,
    })
    options.push(...result.records.map(studentOption))
    pages = result.pages
    page += 1
  }
  return options
}

function studentOption(student: StudentVO): PeerOption {
  const name = student.realName || student.username || `学员 #${student.studentId}`
  return {
    id: student.studentId,
    label: student.phone ? `${name}（${student.phone}）` : name,
    phone: student.phone,
  }
}

function openNewSession(): void {
  newDialogVisible.value = true
  selectedPeerId.value = null
  void loadPeerOptions()
}

async function confirmNewSession(): Promise<void> {
  if (selectedPeerId.value === null || creating.value) return

  creating.value = true
  try {
    const created = await getOrCreateChatSessionApi(props.role, selectedPeerId.value)
    newDialogVisible.value = false
    await loadSessions({ silent: true, autoSelect: false })

    let target = sessions.value.find((session) => session.id === created.id)
    if (!target) {
      sessions.value = [created, ...sessions.value]
      target = created
    }
    await selectSession(target, true, true)
  } catch {
    // 无有效绑定返回 3102，拦截器已提示；保留弹窗供用户重新选择。
  } finally {
    creating.value = false
  }
}

/* ------------------------------- 展示辅助 ------------------------------- */

function displayPeerName(session: ChatSessionVO): string {
  return session.peerName || `${peerNoun.value} #${session.peerUserId}`
}

function peerInitial(session: ChatSessionVO): string {
  return Array.from(displayPeerName(session))[0] || '聊'
}

function todayString(): string {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

function formatListTime(value: string | null): string {
  if (!value) return ''
  const [date, time = ''] = value.split(' ')
  if (date === todayString()) return time.slice(0, 5)
  return `${date.slice(5)} ${time.slice(0, 5)}`
}

function formatMessageTime(value: string): string {
  if (!value) return ''
  const [date, time = ''] = value.split(' ')
  if (date === todayString()) return time.slice(0, 5)
  return `${date.slice(5)} ${time.slice(0, 5)}`
}

/* ------------------------------- 生命周期 ------------------------------- */

function onWindowFocus(): void {
  // 解绑/重新绑定可能发生在其他页面；回到本页时刷新会话，恢复或禁用发送。
  if (document.visibilityState === 'visible' && activeSession.value && !activeSession.value.canSend) {
    void loadSessions({ silent: true })
  }
}

onMounted(() => {
  window.addEventListener('focus', onWindowFocus)
  void loadSessions()
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', onWindowFocus)
  conversationSeq += 1
  sessionsSeq += 1
  peerSeq += 1
  stopPolling()
  abortMessageRequest()
})
</script>

<template>
  <div class="chat-view">
    <div class="chat-view__head">
      <div>
        <h2 class="chat-view__title">{{ title }}</h2>
        <p class="chat-view__subtitle">{{ subtitle }}</p>
      </div>
      <div class="chat-view__actions">
        <el-button :icon="Refresh" :loading="sessionsLoading" @click="reloadSessions">
          刷新
        </el-button>
        <el-button type="primary" plain :icon="Plus" @click="openNewSession">
          发起会话
        </el-button>
      </div>
    </div>

    <div class="chat-view__body">
      <!-- 左侧：会话列表 -->
      <aside class="session-pane">
        <div class="session-pane__head">
          <span>会话列表</span>
          <span class="session-pane__count">{{ sessions.length }}</span>
        </div>

        <div class="session-pane__scroll">
          <el-skeleton v-if="sessionsLoading" :rows="4" animated class="session-pane__state" />

          <el-empty v-else-if="sessionsError" description="会话加载失败">
            <el-button type="primary" plain @click="reloadSessions">重新加载</el-button>
          </el-empty>

          <el-empty v-else-if="sessions.length === 0" description="暂无会话">
            <el-button type="primary" plain @click="openNewSession">发起会话</el-button>
          </el-empty>

          <div v-else class="session-list">
            <button
              v-for="session in sessions"
              :key="session.id"
              type="button"
              class="session-item"
              :class="{ 'session-item--active': session.id === activeSessionId }"
              @click="onSessionClick(session)"
            >
              <div class="session-item__top">
                <span class="session-item__name">{{ displayPeerName(session) }}</span>
                <span class="session-item__time">{{ formatListTime(session.lastMessageTime) }}</span>
              </div>
              <div class="session-item__bottom">
                <span class="session-item__summary">{{ session.lastMessage || '暂无消息' }}</span>
                <el-badge
                  v-if="session.unreadCount > 0"
                  :value="session.unreadCount"
                  :max="99"
                  class="session-item__badge"
                />
              </div>
              <span v-if="!session.canSend" class="session-item__readonly">仅可查看历史</span>
            </button>
          </div>
        </div>
      </aside>

      <!-- 右侧：聊天区 -->
      <section class="chat-main">
        <template v-if="activeSession">
          <header class="chat-main__head">
            <div class="chat-main__avatar">{{ peerInitial(activeSession) }}</div>
            <div class="chat-main__peer">
              <div class="chat-main__name">{{ displayPeerName(activeSession) }}</div>
              <div class="chat-main__phone">
                {{ activeSession.peerPhone || '暂无联系方式' }}
              </div>
            </div>
            <el-tag :type="activeSession.canSend ? 'success' : 'info'" size="small">
              {{ activeSession.canSend ? '可发送消息' : '仅查看历史' }}
            </el-tag>
          </header>

          <div ref="messageScroll" class="message-scroll">
            <div v-if="messagesLoading" class="message-state">
              <el-skeleton :rows="5" animated />
            </div>

            <el-empty v-else-if="messagesError" description="消息加载失败">
              <el-button type="primary" plain @click="retryMessages">重新加载</el-button>
            </el-empty>

            <template v-else>
              <div v-if="messages.length > 0" class="message-older">
                <el-button
                  link
                  type="primary"
                  :loading="loadingOlder"
                  :disabled="noMore"
                  @click="loadOlder"
                >
                  {{ noMore ? '已加载全部' : '加载更早消息' }}
                </el-button>
              </div>

              <el-empty
                v-if="messages.length === 0"
                description="暂无消息，发条消息开始聊天吧"
              />

              <div
                v-for="message in messages"
                :key="message.id"
                class="message-row"
                :class="isOwnMessage(message) ? 'message-row--own' : 'message-row--peer'"
              >
                <div
                  class="message-bubble"
                  :class="isOwnMessage(message) ? 'message-bubble--own' : 'message-bubble--peer'"
                >
                  {{ message.content }}
                </div>
                <div class="message-meta">
                  <span>{{ formatMessageTime(message.createTime) }}</span>
                </div>
              </div>
            </template>

            <div v-if="pollError" class="poll-error">
              <span>实时消息更新已暂停。</span>
              <el-button link type="primary" @click="retryPolling">恢复轮询</el-button>
            </div>
          </div>

          <div class="composer">
            <div v-if="!activeSession.canSend" class="composer__notice">
              当前绑定已解除，只能查看历史消息；重新绑定后可继续发送。
            </div>
            <div v-else-if="sendError" class="composer__error">{{ sendError }}</div>

            <el-input
              v-model="input"
              type="textarea"
              :rows="3"
              resize="none"
              :disabled="!activeSession.canSend || sending"
              :placeholder="
                activeSession.canSend
                  ? '输入消息，Enter 发送 / Shift+Enter 换行'
                  : '当前只能查看历史消息'
              "
              @keydown="onKeydown"
            />
            <div class="composer__actions">
              <span class="composer__hint">消息将以纯文本发送</span>
              <el-button
                type="primary"
                :loading="sending"
                :disabled="!canSubmit"
                @click="sendMessage"
              >
                发送
              </el-button>
            </div>
          </div>
        </template>

        <el-empty v-else description="请选择左侧会话，或发起一个新的会话">
          <el-button type="primary" plain @click="openNewSession">发起会话</el-button>
        </el-empty>
      </section>
    </div>

    <el-dialog
      v-model="newDialogVisible"
      :title="newDialogTitle"
      width="420px"
      :close-on-click-modal="!creating"
    >
      <div v-if="peerLoading" class="dialog-state">
        <el-skeleton :rows="3" animated />
      </div>

      <el-empty v-else-if="peerError" description="可会话列表加载失败">
        <el-button type="primary" plain @click="loadPeerOptions">重新加载</el-button>
      </el-empty>

      <el-empty v-else-if="peerOptions.length === 0" :description="`暂无可会话${peerNoun}`" />

      <el-form v-else label-position="top">
        <el-form-item :label="`选择${peerNoun}`">
          <el-select
            v-model="selectedPeerId"
            filterable
            clearable
            :placeholder="`请选择${peerNoun}`"
            style="width: 100%"
          >
            <el-option
              v-for="option in peerOptions"
              :key="option.id"
              :label="option.label"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="creating" @click="newDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="selectedPeerId === null || creating"
          @click="confirmNewSession"
        >
          开始会话
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  min-height: 540px;
  overflow: hidden;
  background: #fff;
  border-radius: 6px;
}

.chat-view__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #dce3ec;
}

.chat-view__title {
  margin: 0;
  color: #263449;
  font-size: 18px;
  font-weight: 600;
}

.chat-view__subtitle {
  margin: 4px 0 0;
  color: #687589;
  font-size: 12px;
}

.chat-view__actions {
  display: flex;
  gap: 8px;
}

.chat-view__body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.session-pane {
  display: flex;
  flex-direction: column;
  width: 300px;
  min-width: 240px;
  border-right: 1px solid #dce3ec;
  background: #fff;
}

.session-pane__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  color: #263449;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid #f1f4f9;
}

.session-pane__count {
  color: #687589;
  font-size: 12px;
  font-weight: 400;
}

.session-pane__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.session-pane__state {
  padding: 14px;
}

.session-list {
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 4px;
}

.session-item {
  position: relative;
  display: block;
  width: 100%;
  padding: 10px;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  font: inherit;
  transition: background 0.15s, border-color 0.15s;
}

.session-item:hover {
  background: #f5f7fa;
}

.session-item--active {
  background: #e8effb;
  border-color: #d9ecff;
}

.session-item__top,
.session-item__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.session-item__top {
  margin-bottom: 6px;
}

.session-item__name {
  overflow: hidden;
  color: #263449;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__time {
  flex: none;
  color: #a8abb2;
  font-size: 11px;
}

.session-item__bottom {
  min-width: 0;
}

.session-item__summary {
  overflow: hidden;
  color: #687589;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__badge {
  flex: none;
}

.session-item__readonly {
  display: inline-block;
  margin-top: 5px;
  color: #a8abb2;
  font-size: 11px;
}

.chat-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  background: #f8fafc;
}

.chat-main__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #dce3ec;
}

.chat-main__avatar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  background: #7597d5;
  border-radius: 50%;
}

.chat-main__peer {
  flex: 1;
  min-width: 0;
}

.chat-main__name {
  overflow: hidden;
  color: #263449;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-main__phone {
  margin-top: 2px;
  color: #687589;
  font-size: 12px;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px 20px 18px;
}

.message-state {
  padding: 20px;
}

.message-older {
  padding: 2px 0 12px;
  text-align: center;
}

.message-row {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 12px;
}

.message-row--own {
  align-items: flex-end;
}

.message-bubble {
  max-width: min(70%, 640px);
  padding: 9px 12px;
  color: #263449;
  font-size: 14px;
  line-height: 1.55;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border-radius: 8px;
}

.message-bubble--peer {
  background: #fff;
  border: 1px solid #e8edf3;
  border-top-left-radius: 2px;
}

.message-bubble--own {
  color: #fff;
  background: #7597d5;
  border-top-right-radius: 2px;
}

.message-meta {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  color: #a8abb2;
  font-size: 11px;
}

.message-row--own .message-meta {
  justify-content: flex-end;
}

.poll-error {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 8px;
  color: #c98f4a;
  font-size: 12px;
}

.composer {
  flex: none;
  padding: 10px 16px 12px;
  background: #fff;
  border-top: 1px solid #dce3ec;
}

.composer__notice,
.composer__error {
  margin-bottom: 8px;
  padding: 7px 10px;
  font-size: 12px;
  border-radius: 4px;
}

.composer__notice {
  color: #687589;
  background: #f2f4f7;
}

.composer__error {
  color: #c96a6a;
  background: #fbeded;
}

.composer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.composer__hint {
  color: #a8abb2;
  font-size: 12px;
}

.dialog-state {
  padding: 8px 0;
}

@media (max-width: 760px) {
  .session-pane {
    width: 230px;
    min-width: 190px;
  }

  .message-scroll {
    padding-right: 12px;
    padding-left: 12px;
  }

  .message-bubble {
    max-width: 82%;
  }
}
</style>
