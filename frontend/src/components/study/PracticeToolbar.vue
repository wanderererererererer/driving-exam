<script setup lang="ts">
import { computed } from 'vue'
import type { PracticeMode } from '@/types'

/**
 * 刷题页顶部栏：返回按钮 + 进度文字。
 *
 * <p><b>纯表现层</b>：不读也不改 store，数据由 StudyView 以 props 传入，
 * 返回意图通过事件抛给 StudyView。</p>
 *
 * <p><b>进度只在「顺序练习」下显示</b>：章节 / 随机 / 重练没有"全库进度"的概念。</p>
 */
const props = defineProps<{
  mode: PracticeMode | null
  /** 当前题序（从 1 开始），即 currentIndex + 1 */
  currentIndex: number
  /** 题目总数（顺序模式取 PageResult.total，即该科目全部题数） */
  totalQuestions: number
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

/** 只有顺序练习显示"第 X / Y 题" */
const showProgress = computed(() => props.mode === 'SEQUENCE' && props.totalQuestions > 0)

const progressText = computed(
  () => `第 ${props.currentIndex + 1} / ${props.totalQuestions} 题`,
)
</script>

<template>
  <div class="ptb">
    <el-button text @click="emit('back')">← 返回</el-button>

    <span v-if="showProgress" class="ptb__progress">{{ progressText }}</span>
    <span v-else class="ptb__placeholder" />
  </div>
</template>

<style scoped>
.ptb {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  margin-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}
.ptb__progress {
  font-size: 14px;
  color: #606266;
}
</style>
