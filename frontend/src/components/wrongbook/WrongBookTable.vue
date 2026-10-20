<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWrongBookListApi, markMasteredApi } from '@/api/wrongBook'
import type { WrongBookCategory, WrongBookItemVO } from '@/types'

/**
 * 错题列表（分页 + 操作）。
 *
 * <p>自己负责拉列表与分页；数据变更后通过 {@code changed} 事件通知父组件刷新角标。
 * 「错题重练」入口在父组件的 tab 栏右侧（不是每行一个）。</p>
 */
const props = defineProps<{
  subjectCode: string
  category: WrongBookCategory
}>()

const emit = defineEmits<{
  /** 列表数据发生变化（标记已掌握）——父组件需要刷新 summary */
  (e: 'changed'): void
}>()

const loading = ref(false)
const list = ref<WrongBookItemVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

async function load() {
  loading.value = true
  try {
    const page = await getWrongBookListApi({
      subjectCode: props.subjectCode,
      category: props.category,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    list.value = page.records
    total.value = page.total
    pageNum.value = page.pageNum
  } catch {
    // 错误提示已由拦截器统一弹出
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 切科目或切 tab → 回到第 1 页重新加载
watch(
  () => [props.subjectCode, props.category],
  () => {
    pageNum.value = 1
    void load()
  },
)

onMounted(() => {
  void load()
})

function onPageChange(page: number) {
  pageNum.value = page
  void load()
}

/** 标记已掌握：成功后刷新本列表 + 通知父组件刷新角标 */
async function onMaster(item: WrongBookItemVO) {
  await ElMessageBox.confirm(
    '标记后该题会移到「已攻克」，不再出现在待攻克列表中。如果答错会重新回到待攻克。确定吗？',
    '提示',
    { type: 'info' },
  )
  await markMasteredApi(item.id)
  ElMessage.success('已标记为掌握')
  await load() // 刷新当前列表（否则那行还留在原地）
  emit('changed') // 通知父组件刷新 tab 角标
}
</script>

<template>
  <div class="wbl">
    <div v-loading="loading" class="wbl__body">
      <el-empty v-if="!loading && list.length === 0" description="暂无错题" />

      <div v-for="item in list" :key="item.id" class="wbl__row">
        <div class="wbl__main">
          <el-tooltip :content="item.content" placement="top" :show-after="400">
            <span class="wbl__content">{{ item.content }}</span>
          </el-tooltip>
          <div class="wbl__meta">
            <el-tag size="small" type="info">{{ item.questionType }}</el-tag>
            <span>错误 {{ item.wrongCount }} 次</span>
            <span v-if="item.lastWrongTime">最近错于 {{ item.lastWrongTime }}</span>
          </div>
        </div>

        <div class="wbl__actions">
          <!-- 已攻克的题不再显示「标记已掌握」 -->
          <el-button
            v-if="item.category !== 'MASTERED'"
            size="small"
            plain
            @click="onMaster(item)"
          >
            标记已掌握
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="total > 0" class="wbl__pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total"
        :current-page="pageNum"
        :page-size="pageSize"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<style scoped>
.wbl__body {
  min-height: 120px;
}
.wbl__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid #f1f4f9;
}
.wbl__row:last-child {
  border-bottom: none;
}
.wbl__main {
  flex: 1;
  min-width: 0;
}
.wbl__content {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #263449;
}
.wbl__meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #687589;
}
.wbl__actions {
  flex: none;
}
.wbl__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
