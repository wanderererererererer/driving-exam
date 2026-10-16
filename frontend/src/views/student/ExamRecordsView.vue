<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamHistoryApi } from '@/api/exam'
import type { ExamRecordVO } from '@/types'

/**
 * 学员端 · 考试记录（仅已结束的考试）。
 *
 * <p>科目来自路由 query {@code /student/exam-records?subjectCode=SUBJECT_1}（由科目一/四页面进入），
 * 缺参或非法一律提示并跳回。数据来自后端既有接口 {@code GET /api/student/exam/history}
 * （按科目过滤、按交卷时间倒序分页）。</p>
 *
 * <p>仅展示后端真实返回的字段：试卷名称 / 交卷时间 / 得分 / 是否通过。
 * **后端未返回**考试状态字段、且**无成绩详情页**，故本页不展示这两项。</p>
 */
const route = useRoute()
const router = useRouter()

const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_4: '科目四',
}

const subjectCode = computed(() => String(route.query.subjectCode ?? ''))
const valid = computed(() => subjectCode.value in SUBJECT_NAMES)
const subjectName = computed(() => SUBJECT_NAMES[subjectCode.value] ?? '')

const records = ref<ExamRecordVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10

const loading = ref(false)
const loadError = ref(false)

/** 拉取当前科目的考试记录（分页） */
async function loadRecords(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const res = await getExamHistoryApi(subjectCode.value, pageNum.value, pageSize)
    records.value = res.records
    total.value = res.total
  } catch {
    // 失败提示由请求拦截器统一弹出，这里只落地错误态
    records.value = []
    total.value = 0
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
    pageNum.value = 1
    loadRecords()
  },
  { immediate: true },
)

/** 翻页 */
function onPageChange(p: number): void {
  pageNum.value = p
  loadRecords()
}

/** 返回对应科目首页 */
function goBack(): void {
  router.push(valid.value ? `/student/subject/${subjectCode.value}` : '/student/dashboard')
}
</script>

<template>
  <div v-if="valid" class="er">
    <div class="er__head">
      <el-button text @click="goBack">← 返回</el-button>
      <h2 class="er__title">{{ subjectName }} · 考试记录</h2>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="er__loading">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- 加载失败：允许重试 -->
    <el-result
      v-else-if="loadError"
      icon="error"
      title="考试记录加载失败"
      sub-title="请检查网络或稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="loadRecords">重试</el-button>
      </template>
    </el-result>

    <!-- 记录表 -->
    <div v-else class="er__body">
      <el-table :data="records" stripe style="width: 100%">
        <el-table-column prop="paperTitle" label="试卷名称" min-width="200" />
        <el-table-column prop="submitTime" label="考试时间" min-width="180" />
        <el-table-column prop="score" label="得分" width="100" />
        <el-table-column label="是否通过" width="110">
          <template #default="{ row }">
            <el-tag :type="row.isPass ? 'success' : 'danger'" size="small">
              {{ row.isPass ? '通过' : '未通过' }}
            </el-tag>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无考试记录" />
        </template>
      </el-table>

      <div v-if="total > pageSize" class="er__pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          :current-page="pageNum"
          @current-change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.er {
  padding: 16px;
}
.er__head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.er__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.er__loading {
  background: #fff;
  padding: 16px;
  border-radius: 4px;
}
.er__body {
  background: #fff;
  padding: 16px;
  border-radius: 4px;
}
.er__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
