<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { getKnowledgePointsApi } from '@/api/study'
import type { KPTreeNodeVO } from '@/types'

/**
 * 章节选择弹窗。
 *
 * <p>打开时拉取知识点树；点某个节点 → 抛 {@code select} 事件由父组件**先关弹窗再跳转**
 * （避免弹窗挡住新页面）。</p>
 *
 * <p>不做"全部章节"选项——顺序练习入口已在科目首页。</p>
 */
const props = defineProps<{
  /** v-model 控制显隐 */
  modelValue: boolean
  subjectCode: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', knowledgePointId: number): void
  /** 关闭动画结束后触发（父组件借此在动画播完后再跳转） */
  (e: 'closed'): void
}>()

const loading = ref(false)
const loadFailed = ref(false)
const treeData = ref<KPTreeNodeVO[]>([])

/** 当前在飞的请求；组件卸载 / 关弹窗 / 重新加载时取消，避免请求回来后写已卸载组件的状态 */
let controller: AbortController | null = null

async function loadTree() {
  controller?.abort() // 取消上一次未完成的请求
  const mine = new AbortController()
  controller = mine

  loading.value = true
  loadFailed.value = false
  treeData.value = []

  try {
    treeData.value = await getKnowledgePointsApi(props.subjectCode, mine.signal)
  } catch {
    if (mine.signal.aborted) return // 被取消：静默，不算错误
    loadFailed.value = true
  } finally {
    if (controller === mine) {
      loading.value = false
    }
  }
}

/** 关闭时的清理：取消请求 + 清空树数据（避免下次打开看到旧数据） */
function cleanup() {
  controller?.abort()
  controller = null
  treeData.value = []
  loadFailed.value = false
  loading.value = false
}

// 打开 → 加载；关闭 → 清理。
// 打开状态下 subjectCode 变化（用户手改 URL）也要重新拉树。
watch(
  () => [props.modelValue, props.subjectCode] as const,
  ([visible]) => {
    if (visible) {
      void loadTree()
    } else {
      cleanup()
    }
  },
)

onUnmounted(cleanup)

function onVisibleChange(value: boolean) {
  emit('update:modelValue', value)
}

function onNodeClick(node: KPTreeNodeVO) {
  emit('select', node.id)
}
</script>

<template>
  <!-- 禁止点空白关闭，避免误操作；关闭用右上角 × 或底部「取消」 -->
  <el-dialog
    :model-value="modelValue"
    title="选择章节"
    width="600px"
    :close-on-click-modal="false"
    @update:model-value="onVisibleChange"
    @closed="emit('closed')"
  >
    <div v-loading="loading" class="cpd__body">
      <!-- 加载失败：错误提示 + 重试 -->
      <el-alert
        v-if="loadFailed"
        type="error"
        :closable="false"
        show-icon
        title="知识点加载失败，请检查网络后重试"
      >
        <el-button size="small" type="primary" plain @click="loadTree">重试</el-button>
      </el-alert>

      <el-tree
        v-else
        :data="treeData"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        :expand-on-click-node="false"
        default-expand-all
        empty-text="暂无知识点"
        @node-click="onNodeClick"
      >
        <template #default="{ data }">
          <span class="cpd__node">
            <!-- 文字过长省略，悬停看全名 -->
            <el-tooltip :content="data.name" placement="top" :show-after="400">
              <span class="cpd__name">{{ data.name }}</span>
            </el-tooltip>
            <span v-if="data.questionCount" class="cpd__count">{{ data.questionCount }} 题</span>
          </span>
        </template>
      </el-tree>
    </div>

    <template #footer>
      <el-button @click="onVisibleChange(false)">取消</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.cpd__body {
  min-height: 120px;
  max-height: 420px;
  overflow-y: auto;
}
.cpd__node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0; /* 让 flex 子项能收缩，ellipsis 才生效 */
}
.cpd__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cpd__count {
  flex: none;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 8px;
  padding: 0 6px;
}
</style>
