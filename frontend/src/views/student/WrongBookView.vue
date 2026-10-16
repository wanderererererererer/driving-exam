<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WrongBookTabs from '@/components/wrongbook/WrongBookTabs.vue'
import WrongBookTable from '@/components/wrongbook/WrongBookTable.vue'
import { getWrongBookSummaryApi } from '@/api/wrongBook'
import type { WrongBookCategory, WrongBookSummaryVO } from '@/types'

/**
 * 错题本页（按科目）。
 *
 * <p>路由：{@code /student/wrong-book?subjectCode=SUBJECT_1}。
 * 容器职责：解析科目、拉分类统计、编排 tabs 与列表。</p>
 */
const route = useRoute()
const router = useRouter()

const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_4: '科目四',
}

const subjectCode = computed(() => String(route.query.subjectCode ?? 'SUBJECT_1'))
const subjectName = computed(() => SUBJECT_NAMES[subjectCode.value] ?? '科目一')

/** 当前 tab，默认「待攻克」 */
const category = ref<WrongBookCategory>('PENDING')
const summary = ref<WrongBookSummaryVO | null>(null)

async function loadSummary() {
  try {
    summary.value = await getWrongBookSummaryApi(subjectCode.value)
  } catch {
    // 错误提示已由拦截器统一弹出；角标不显示即可
    summary.value = null
  }
}

// 切科目：重置到「待攻克」并重新拉统计
watch(subjectCode, () => {
  category.value = 'PENDING'
  void loadSummary()
})

onMounted(() => {
  void loadSummary()
})

/** 返回科目首页 */
function goBack() {
  const sc = subjectCode.value
  router.push(SUBJECT_NAMES[sc] ? `/student/subject/${sc}` : '/student/dashboard')
}

/** 重练：跳刷题页的 RETRY 模式。
 *  注意这是"练一批"（后端按"优先高频错、不够补待攻克"抽题），不能指定单题。 */
function onRetry() {
  router.push({
    path: '/student/study',
    query: { subjectCode: subjectCode.value, mode: 'RETRY' },
  })
}
</script>

<template>
  <div class="wbv">
    <div class="wbv__head">
      <el-button text @click="goBack">← 返回</el-button>
      <h2 class="wbv__title">{{ subjectName }}错题本</h2>
    </div>

    <div class="wbv__bar">
      <WrongBookTabs v-model="category" :summary="summary" />
      <el-button type="primary" @click="onRetry">错题重练</el-button>
    </div>

    <div class="wbv__list">
      <WrongBookTable
        :subject-code="subjectCode"
        :category="category"
        @changed="loadSummary"
      />
    </div>
  </div>
</template>

<style scoped>
.wbv__head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.wbv__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.wbv__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.wbv__list {
  margin-top: 16px;
}
</style>
