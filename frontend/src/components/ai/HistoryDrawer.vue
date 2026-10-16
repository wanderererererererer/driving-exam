<script setup lang="ts">
import { watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useAiChatStore } from '@/store/aiChat'
import type { AiSessionVO } from '@/types'

/**
 * 会话历史抽屉：会话列表 + 分页加载 + 删除整个会话 + 点击回显。
 *
 * <p>每次**打开时**调 {@code loadSessions()}（内部重置后拉第一页），避免重复条目。</p>
 */
const visible = defineModel<boolean>({ required: true })

const store = useAiChatStore()

watch(visible, (open) => {
  if (open) {
    void loadFirstPage()
  }
})

async function loadFirstPage(): Promise<void> {
  try {
    await store.loadSessions()
  } catch {
    // 拦截器已提示；保持当前列表
  }
}

async function loadMore(): Promise<void> {
  try {
    await store.loadMoreSessions()
  } catch {
    // 拦截器已提示
  }
}

/** 点击某会话：主区切到该会话（并切 sessionId），成功后关闭抽屉 */
async function onPick(session: AiSessionVO): Promise<void> {
  try {
    await store.loadFromSession(session)
    visible.value = false
  } catch {
    // 加载失败则不关闭，便于用户重试
  }
}

/** 删除整个会话：二次确认后删除 */
async function onDelete(session: AiSessionVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后该会话所有对话将被清除，确定吗？', '提示', {
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }
  try {
    await store.deleteSession(session.sessionId)
  } catch {
    // 拦截器已提示
  }
}
</script>

<template>
  <el-drawer v-model="visible" title="历史会话" direction="rtl" size="380px">
    <div class="hd">
      <el-empty v-if="store.sessionList.length === 0" description="暂无历史会话" />

      <div v-else class="hd__list">
        <div
          v-for="s in store.sessionList"
          :key="s.sessionId"
          class="hd__item"
          @click="onPick(s)"
        >
          <div class="hd__q">{{ s.firstQuestion }}</div>
          <div class="hd__meta">
            <span class="hd__time">{{ s.lastTime }}</span>
            <span class="hd__count">{{ s.messageCount }} 轮对话</span>
            <el-button text type="danger" size="small" @click.stop="onDelete(s)">
              删除
            </el-button>
          </div>
        </div>
      </div>

      <div class="hd__more">
        <el-button v-if="store.sessionHasMore" text type="primary" @click="loadMore">
          加载更多
        </el-button>
        <span v-else class="hd__end">没有更多了</span>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.hd {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.hd__list {
  flex: 1;
  overflow-y: auto;
}
.hd__item {
  padding: 10px 4px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}
.hd__item:hover {
  background: #f5f7fa;
}
.hd__q {
  color: #303133;
  font-size: 14px;
  line-height: 1.5;
  /* 截断为最多两行 */
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.hd__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.hd__time {
  color: #909399;
  font-size: 12px;
}
.hd__count {
  color: #909399;
  font-size: 12px;
}
.hd__more {
  padding-top: 12px;
  text-align: center;
}
.hd__end {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
