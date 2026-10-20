<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useAiChatStore, type ChatMessage } from '@/store/aiChat'

/**
 * 对话消息列表：用户气泡右对齐、AI 气泡左对齐；AI 消息支持**打字机**逐字显示。
 *
 * <p><b>打字机是显示逻辑，只在本组件内</b>（store 只负责完整回答与 `typing` 标记）。</p>
 *
 * <p><b>为什么用 setTimeout 递归而不是 setInterval</b>：递归在每次 tick 后重新计时，
 * 长回答也不会因事件循环堆积而"越跑越快"；且结束时天然停止，无需再清 interval。</p>
 *
 * <p><b>计时器必须 onUnmounted 清理</b>（Day 11 的 600ms 双跳教训：组件销毁后回调仍在跑）。</p>
 */
const store = useAiChatStore()

/** 打字机每字间隔（毫秒） */
const TYPE_INTERVAL_MS = 30

/** recordId → 已显示字符数（响应式 Map：驱动模板重渲染） */
const shownLength = reactive(new Map<number, number>())
/** recordId → 计时器句柄（普通 Map：不参与渲染） */
const timers = new Map<number, number>()

const scrollEl = ref<HTMLElement | null>(null)

/* ============================== 滚动 ============================== */

function isNearBottom(): boolean {
  const el = scrollEl.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  const el = scrollEl.value
  if (el) el.scrollTop = el.scrollHeight
}

/** 只在用户"贴近底部"时跟随滚动，避免打断向上翻阅历史 */
async function scrollIfNearBottom(): Promise<void> {
  if (isNearBottom()) await scrollToBottom()
}

/* ============================== 打字机 ============================== */

function stopTimer(recordId: number): void {
  const t = timers.get(recordId)
  if (t !== undefined) {
    window.clearTimeout(t)
    timers.delete(recordId)
  }
}

/** 组件销毁时清掉所有挂起的计时器（防止销毁后回调继续跑） */
function clearAllTimers(): void {
  for (const t of timers.values()) window.clearTimeout(t)
  timers.clear()
}

function startTyping(msg: ChatMessage): void {
  const id = msg.recordId
  // 无 recordId（理论上只可能是历史回填，且其 typing 为假）→ 不打字，直接落定
  if (id === undefined) {
    msg.typing = false
    return
  }
  if (timers.has(id)) return // 已在打字中，避免重复调度
  if (shownLength.get(id) === undefined) shownLength.set(id, 0)

  const tick = (): void => {
    const shown = shownLength.get(id) ?? 0
    if (shown >= msg.content.length) {
      stopTimer(id)
      msg.typing = false // 打字完成，交给普通渲染
      void scrollIfNearBottom()
      return
    }
    shownLength.set(id, shown + 1)
    void scrollIfNearBottom()
    timers.set(id, window.setTimeout(tick, TYPE_INTERVAL_MS))
  }
  timers.set(id, window.setTimeout(tick, TYPE_INTERVAL_MS))
}

/** 点击气泡：立即显示全文（跳过动画） */
function finishTyping(msg: ChatMessage): void {
  if (msg.role !== 'ai' || !msg.typing) return
  const id = msg.recordId
  if (id !== undefined) {
    stopTimer(id)
    shownLength.set(id, msg.content.length)
  }
  msg.typing = false
  void scrollToBottom()
}

/** 扫描需要打字的 AI 消息（幂等：已在打字的会被 timers.has 挡掉） */
function ensureTyping(): void {
  for (const m of store.messages) {
    if (m.role === 'ai' && m.typing && (m.recordId === undefined || !timers.has(m.recordId))) {
      startTyping(m)
    }
  }
}

/** 渲染文本：打字中的 AI 消息只显示前 N 个字符 */
function displayContent(msg: ChatMessage): string {
  if (msg.role !== 'ai' || !msg.typing) return msg.content
  const id = msg.recordId
  if (id === undefined) return msg.content
  return msg.content.slice(0, shownLength.get(id) ?? 0)
}

/* ============================== 生命周期 ============================== */

// 新消息推入（数组变长）→ 启动打字 + 滚到底
watch(
  () => store.messages.length,
  () => {
    ensureTyping()
    void scrollToBottom()
  },
)

onMounted(() => {
  ensureTyping()
  void scrollToBottom()
})

onUnmounted(clearAllTimers)
</script>

<template>
  <div ref="scrollEl" class="cml">
    <el-empty
      v-if="store.messages.length === 0"
      description="有什么驾考问题，尽管问我～"
    />

    <div
      v-for="(msg, idx) in store.messages"
      :key="idx"
      class="cml__row"
      :class="msg.role === 'user' ? 'cml__row--user' : 'cml__row--ai'"
    >
      <div
        class="cml__bubble"
        :class="msg.role === 'user' ? 'cml__bubble--user' : 'cml__bubble--ai'"
        @click="finishTyping(msg)"
      >
        <span class="cml__text">{{ displayContent(msg) }}</span>
        <span v-if="msg.typing" class="cml__caret">▍</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cml {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.cml__row {
  display: flex;
  margin-bottom: 12px;
}
.cml__row--user {
  justify-content: flex-end;
}
.cml__row--ai {
  justify-content: flex-start;
}
.cml__bubble {
  max-width: 76%;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.6;
  white-space: pre-wrap; /* 保留 AI 回答里的换行 */
  word-break: break-word;
  cursor: default;
}
.cml__bubble--user {
  background: #7597d5;
  color: #fff;
  border-top-right-radius: 2px;
}
.cml__bubble--ai {
  background: #f2f4f7;
  color: #263449;
  border-top-left-radius: 2px;
  cursor: pointer; /* 提示"可点立即显示全文" */
}
.cml__caret {
  animation: cml-blink 0.9s steps(1) infinite;
  margin-left: 1px;
}
@keyframes cml-blink {
  50% {
    opacity: 0;
  }
}
</style>
