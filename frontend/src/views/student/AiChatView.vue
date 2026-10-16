<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import ChatMessageList from '@/components/ai/ChatMessageList.vue'
import ChatInput from '@/components/ai/ChatInput.vue'
import HistoryDrawer from '@/components/ai/HistoryDrawer.vue'
import { useAiChatStore } from '@/store/aiChat'

/**
 * 学员端 · 智能问答页。
 *
 * <p>路由 {@code /student/ai}（可选 query {@code subjectCode}，从科目首页跳入时带上，
 * 用于向 AI 注入科目上下文）。</p>
 *
 * <p>容器职责：编排三个子组件 + 解析科目上下文；打字机等显示逻辑都在
 * {@link ChatMessageList} 内，不在本页也不在 store。</p>
 */
const route = useRoute()
const store = useAiChatStore()

/** 来自 query 的科目（无则 undefined，AI 按通用提问处理） */
const subjectCode = computed<string | undefined>(() => {
  const sc = route.query.subjectCode
  return typeof sc === 'string' && sc ? sc : undefined
})

const drawerVisible = ref(false)

/** 开始新会话：清空主消息区 + 换 sessionId（:key 变化会重建消息列表，连带清掉打字计时器） */
function onNewSession(): void {
  store.startNewSession()
}
</script>

<template>
  <div class="aiv">
    <div class="aiv__head">
      <h2 class="aiv__title">智能问答</h2>
      <div class="aiv__actions">
        <el-button @click="drawerVisible = true">历史记录</el-button>
        <el-button type="primary" plain @click="onNewSession">开始新会话</el-button>
      </div>
    </div>

    <div class="aiv__body">
      <!-- :key 绑定 sessionId：切换会话时强制重建，销毁子组件里的打字计时器 -->
      <ChatMessageList :key="store.sessionId" />
      <ChatInput :subject-code="subjectCode" />
    </div>

    <HistoryDrawer v-model="drawerVisible" />
  </div>
</template>

<style scoped>
.aiv {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}
.aiv__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.aiv__title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.aiv__actions {
  display: flex;
  gap: 10px;
}
.aiv__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0; /* 允许内部滚动区正确收缩 */
}
</style>
