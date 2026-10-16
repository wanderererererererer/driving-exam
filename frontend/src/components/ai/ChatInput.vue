<script setup lang="ts">
import { ref } from 'vue'
import { useAiChatStore } from '@/store/aiChat'

/**
 * 输入区：多行输入框 + 发送按钮。
 *
 * <p>Enter 发送 / Shift+Enter 换行；发送中（store.loading）禁用输入与按钮，防止并发提问。</p>
 */
const props = defineProps<{ subjectCode?: string }>()

const store = useAiChatStore()
const text = ref('')

async function onSend(): Promise<void> {
  const q = text.value.trim()
  if (!q || store.loading) return
  text.value = '' // 乐观清空：不等接口返回
  try {
    await store.send(q, props.subjectCode)
  } catch {
    // 错误提示由 axios 拦截器统一弹出（ElMessage）；此处仅吞掉 rejection，避免未捕获告警
  }
}

function onKeydown(e: KeyboardEvent): void {
  // isComposing：中文输入法候选时按 Enter 是"上屏"，不应发送
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    void onSend()
  }
}
</script>

<template>
  <div class="ci">
    <el-input
      v-model="text"
      type="textarea"
      :rows="2"
      resize="none"
      :disabled="store.loading"
      placeholder="输入你的问题，Enter 发送 / Shift+Enter 换行"
      @keydown="onKeydown"
    />
    <el-button
      type="primary"
      class="ci__send"
      :loading="store.loading"
      :disabled="store.loading || !text.trim()"
      @click="onSend"
    >
      发送
    </el-button>
  </div>
</template>

<style scoped>
.ci {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}
.ci__send {
  height: 40px;
}
</style>
