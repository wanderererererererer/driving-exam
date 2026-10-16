<script setup lang="ts">
import { computed } from 'vue'
import { usePracticeStore } from '@/store/practice'

/**
 * 题目底部导航：上一题 / 下一题。
 *
 * <p>进度文字已移到顶部 {@code PracticeToolbar}（那里用的是"科目总题数"），
 * 本组件只负责翻页，避免同一屏出现两个不一致的进度。</p>
 *
 * <p>「下一题」要求**本题已提交**（{@code store.hasAnsweredCurrent}），否则禁用——
 * 未作答不能前进。（store.next() 内部也做了同样的兜底，键盘翻页同样受约束。）</p>
 */
const store = usePracticeStore()

const canNext = computed(() => store.canNext && store.hasAnsweredCurrent)
</script>

<template>
  <div class="qf">
    <el-button :disabled="!store.canPrev" @click="store.prev()">上一题</el-button>
    <el-button type="primary" :disabled="!canNext" @click="store.next()">下一题</el-button>
  </div>
</template>

<style scoped>
.qf {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 12px 0;
}
</style>
