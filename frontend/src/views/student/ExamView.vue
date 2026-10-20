<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPapersApi, startExamApi } from '@/api/exam'
import type { PaperVO } from '@/types'

/**
 * 学员端 · 模拟考试（试卷列表 + 开始考试入口）。
 *
 * <p>**科目来自路由 query** {@code /student/exam?subjectCode=SUBJECT_1}（由科目一/四页面进入），
 * 页内**不再**提供科目切换。缺参或非法参数一律提示并跳回，**不静默请求错误科目**。</p>
 *
 * <p>范围：展示后端真实返回的试卷列表 + 调用真实的「开始考试」接口；答题与逐题报告分别在
 * {@code /student/exam/:examId} 和 {@code /student/exam/:examId/report} 页面展示。</p>
 */
const route = useRoute()
const router = useRouter()

/** 模拟考试只针对理论科目 */
const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_4: '科目四',
}

const subjectCode = computed(() => String(route.query.subjectCode ?? ''))
const valid = computed(() => subjectCode.value in SUBJECT_NAMES)
const subjectName = computed(() => SUBJECT_NAMES[subjectCode.value] ?? '')

const papers = ref<PaperVO[]>([])
const loading = ref(false)
const loadError = ref(false)

/** 正在「开始考试」的试卷 id；非 null 时禁用所有开始按钮（防重复提交） */
const startingId = ref<number | null>(null)

/** 拉取当前科目的试卷列表 */
async function loadPapers(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    papers.value = await getPapersApi(subjectCode.value)
  } catch {
    // 失败提示已由请求拦截器统一弹出（ElMessage），这里只落地「错误态 + 允许重试」
    papers.value = []
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// 校验科目 + 加载（immediate：进入即跑；同时覆盖同组件内 query 变化）
watch(
  subjectCode,
  (sc) => {
    if (!(sc in SUBJECT_NAMES)) {
      ElMessage.error('科目不存在')
      router.replace('/student/dashboard')
      return
    }
    loadPapers()
  },
  { immediate: true },
)

/**
 * 试卷按钮：**继续考试** vs **开始考试**。
 *
 * <p><b>有进行中考试（`ongoingExamId != null`）</b>：直接用该 examId 进入**原来那场**，
 * **不调用 `POST /start`**。原因：`start` 在"进行中考试已超时"时会**先收尾旧场、再新建一场**，
 * 用户会莫名进到一场**新考试**（与原场不同、答案为空）——这正是"退出后无法恢复原场"的成因。
 * 直接进原场可保证「继续考试 → 进入原 examId」，并让"超时"如实表现为"该场已结束"，
 * 而不是被一场新考试掩盖。</p>
 *
 * <p><b>无进行中考试</b>：调用 `POST /start` 新建一场，并用返回的 examId 进入答题页。</p>
 */
async function onPaperAction(paper: PaperVO): Promise<void> {
  if (startingId.value !== null) return // 防重复提交

  // 继续考试：直接进入后端给出的原 examId，绝不新建
  if (paper.ongoingExamId != null) {
    router.push({
      path: `/student/exam/${paper.ongoingExamId}`,
      query: { subjectCode: subjectCode.value },
    })
    return
  }

  // 开始新考试
  startingId.value = paper.id
  try {
    const exam = await startExamApi(paper.id)
    router.push({
      path: `/student/exam/${exam.examId}`,
      query: { subjectCode: subjectCode.value },
    })
  } catch {
    // 失败提示由请求拦截器统一弹出（如 2101）；**不静默跳转、不新建**
  } finally {
    startingId.value = null
  }
}

/** 返回对应科目首页 */
function goBack(): void {
  router.push(valid.value ? `/student/subject/${subjectCode.value}` : '/student/dashboard')
}
</script>

<template>
  <div v-if="valid" class="exam">
    <div class="exam__head">
      <el-button text @click="goBack">← 返回</el-button>
      <h2 class="exam__title">{{ subjectName }} · 模拟考试</h2>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="exam__loading">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- 加载失败：允许重试 -->
    <el-result
      v-else-if="loadError"
      icon="error"
      title="试卷列表加载失败"
      sub-title="请检查网络或稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="loadPapers">重试</el-button>
      </template>
    </el-result>

    <!-- 空列表 -->
    <el-empty v-else-if="papers.length === 0" description="该科目暂无可用试卷" />

    <!-- 试卷列表 -->
    <div v-else class="exam__list">
      <el-card v-for="p in papers" :key="p.id" class="exam__card" shadow="never">
        <div class="exam__card-title">{{ p.title }}</div>
        <div class="exam__meta">
          <span>题量 {{ p.questionCount }} 题</span>
          <span>时长 {{ p.duration }} 分钟</span>
          <span>总分 {{ p.totalScore }}</span>
          <span>及格 {{ p.passScore }}</span>
        </div>
        <el-tag v-if="p.ongoingExamId != null" type="warning" size="small" class="exam__tag">
          进行中
        </el-tag>
        <div class="exam__actions">
          <el-button
            type="primary"
            :loading="startingId === p.id"
            :disabled="startingId !== null"
            @click="onPaperAction(p)"
          >
            {{ p.ongoingExamId != null ? '继续考试' : '开始考试' }}
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.exam {
  padding: 16px;
}
.exam__head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.exam__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.exam__loading {
  background: #fff;
  padding: 16px;
  border-radius: 4px;
}
.exam__list {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  max-width: 720px;
}
.exam__card {
  flex: 0 0 336px;
  max-width: 100%;
}
.exam__card-title {
  font-size: 16px;
  font-weight: 600;
  color: #263449;
}
.exam__meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 13px;
  color: #687589;
}
.exam__tag {
  margin-top: 10px;
}
.exam__actions {
  margin-top: 14px;
}
</style>
