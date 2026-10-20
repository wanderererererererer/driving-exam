<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AnalysisPanel from '@/components/analysis/AnalysisPanel.vue'

/**
 * 学员端 · 学情分析**路由页**（薄壳）。
 *
 * <p>路由 {@code /student/analysis?subjectCode=SUBJECT_1}。只做两件事：
 * ① 从 query 解析科目并校验；② 渲染标题 + 返回 + {@link AnalysisPanel}。
 * 内容全部在 Panel 里（与首页抽屉复用同一组件）。</p>
 */
const route = useRoute()
const router = useRouter()

const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_4: '科目四',
}

/** 科目以 query 为准（此处不再提供页内切换，缺/非法一律视为无效） */
const subjectCode = computed(() => String(route.query.subjectCode ?? ''))
const valid = computed(() => subjectCode.value in SUBJECT_NAMES)
const subjectName = computed(() => SUBJECT_NAMES[subjectCode.value] ?? '')

// 缺 subjectCode 或非法 → 提示并跳回；与科目首页对非法科目的处理方式一致
onMounted(() => {
  if (!valid.value) {
    ElMessage.error('科目不存在')
    router.replace('/student/dashboard')
  }
})

function goBack(): void {
  router.push(valid.value ? `/student/subject/${subjectCode.value}` : '/student/dashboard')
}
</script>

<template>
  <div class="av">
    <div class="av__head">
      <el-button text @click="goBack">← 返回</el-button>
      <h2 class="av__title">{{ subjectName }} · 学情分析</h2>
    </div>

    <!-- v-if="valid"：非法科目时跳转前不渲染面板，避免用空 subjectCode 发请求 -->
    <AnalysisPanel v-if="valid" :subject-code="subjectCode" />
  </div>
</template>

<style scoped>
.av__head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.av__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
</style>
