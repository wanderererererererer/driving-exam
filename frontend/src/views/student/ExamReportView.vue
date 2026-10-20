<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExamReportApi } from '@/api/exam'
import type { ExamReportItemVO, ExamReportVO } from '@/types'

/**
 * 学员端 · 模拟考试逐题报告。
 *
 * <p>数据来自后端 {@code GET /api/student/exam/{examId}/report}；归属校验在服务端完成，
 * 本页不传用户 ID。报告中的题干、选项、标准答案和解析以服务端返回为准。</p>
 */
const route = useRoute()
const router = useRouter()

const examId = computed(() => Number(route.params.examId))
const subjectCode = computed(() => {
  const sc = route.query.subjectCode
  return typeof sc === 'string' ? sc : ''
})

const report = ref<ExamReportVO | null>(null)
const loading = ref(false)
const loadError = ref(false)

const typeLabel: Record<string, string> = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  JUDGE: '判断题',
}

function goBack(): void {
  const sc = subjectCode.value
  if (sc === 'SUBJECT_1' || sc === 'SUBJECT_4') {
    router.push(`/student/exam-records?subjectCode=${sc}`)
  } else {
    router.push('/student/dashboard')
  }
}

function optionOf(item: ExamReportItemVO, key: string): string {
  const option = (item.options || []).find((o) => o.optionKey === key)
  return option ? `${key}. ${option.optionContent}` : key
}

/** 把 A / AB / 对 / 错 转成可读文本；空答案为未作答。 */
function answerText(item: ExamReportItemVO, answer: string | null | undefined): string {
  if (!answer || !answer.trim()) return '未作答'
  if (item.questionType === 'JUDGE') return answer
  return answer
    .split('')
    .filter(Boolean)
    .map((key) => optionOf(item, key))
    .join('；')
}

function isCorrectOption(item: ExamReportItemVO, key: string): boolean {
  return (item.correctAnswer || '').includes(key)
}

function isUserOption(item: ExamReportItemVO, key: string): boolean {
  return (item.userAnswer || '').includes(key)
}

async function load(): Promise<void> {
  if (!Number.isInteger(examId.value) || examId.value <= 0) {
    ElMessage.error('无效的考试记录')
    goBack()
    return
  }
  loading.value = true
  loadError.value = false
  try {
    report.value = await getExamReportApi(examId.value)
  } catch {
    report.value = null
    loadError.value = true
  } finally {
    loading.value = false
  }
}

watch(examId, () => void load(), { immediate: true })
</script>

<template>
  <div class="ear">
    <div class="ear__head">
      <el-button text @click="goBack">← 返回考试记录</el-button>
      <h2 class="ear__title">考试报告</h2>
    </div>

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-result
      v-else-if="loadError"
      icon="error"
      title="成绩报告加载失败"
      sub-title="考试可能不存在、不属于当前账号，或网络异常"
    >
      <template #extra>
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else-if="report">
      <el-card shadow="never" class="ear__summary">
        <div class="ear__summary-row">
          <div>
            <div class="ear__score">
              <span class="ear__score-num">{{ report.score }}</span>
              <span class="ear__score-total">/ {{ report.totalScore }} 分</span>
            </div>
            <el-tag :type="report.isPass ? 'success' : 'danger'" effect="dark" class="ear__pass">
              {{ report.isPass ? '及格' : '未及格' }}
            </el-tag>
          </div>
          <div class="ear__counts">
            <span>正确 <b>{{ report.correctCount }}</b></span>
            <span>错误 <b>{{ report.wrongCount }}</b></span>
            <span>未答 <b>{{ report.unansweredCount }}</b></span>
            <span>及格线 <b>{{ report.passScore }}</b></span>
          </div>
        </div>
      </el-card>

      <el-empty v-if="report.items.length === 0" description="本场考试没有逐题数据" />

      <el-card
        v-for="(item, index) in report.items"
        :key="item.questionId"
        shadow="never"
        class="ear__item"
      >
        <div class="ear__item-head">
          <span class="ear__item-index">第 {{ index + 1 }} 题</span>
          <el-tag size="small" type="info">{{ typeLabel[item.questionType] || item.questionType }}</el-tag>
          <el-tag
            size="small"
            :type="item.isCorrect ? 'success' : item.userAnswer ? 'danger' : 'info'"
          >
            {{ item.isCorrect ? '答对' : item.userAnswer ? '答错' : '未作答' }}
          </el-tag>
        </div>

        <div class="ear__content">{{ item.content }}</div>

        <div v-if="item.options && item.options.length" class="ear__options">
          <div
            v-for="opt in item.options"
            :key="opt.optionKey"
            class="ear__option"
            :class="{
              'is-correct': isCorrectOption(item, opt.optionKey),
              'is-user': isUserOption(item, opt.optionKey),
            }"
          >
            <span class="ear__option-key">{{ opt.optionKey }}</span>
            <span class="ear__option-text">{{ opt.optionContent }}</span>
            <el-tag v-if="isCorrectOption(item, opt.optionKey)" size="small" type="success">
              正确答案
            </el-tag>
            <el-tag v-if="isUserOption(item, opt.optionKey)" size="small" type="info">
              你的选择
            </el-tag>
          </div>
        </div>

        <div class="ear__answers">
          <span>你的答案：{{ answerText(item, item.userAnswer) }}</span>
          <span>正确答案：{{ answerText(item, item.correctAnswer) }}</span>
        </div>

        <div v-if="item.analysis" class="ear__analysis">
          <div class="ear__analysis-title">解析</div>
          <div>{{ item.analysis }}</div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.ear {
  padding: 16px;
}
.ear__head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.ear__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.ear__summary {
  margin-bottom: 16px;
}
.ear__summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.ear__score {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.ear__score-num {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
  color: #7597d5;
}
.ear__score-total {
  font-size: 18px;
  color: #4a5a70;
}
.ear__pass {
  margin-top: 8px;
}
.ear__counts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  color: #4a5a70;
  font-size: 14px;
}
.ear__counts b {
  color: #263449;
}
.ear__item {
  margin-bottom: 14px;
}
.ear__item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.ear__item-index {
  font-weight: 600;
  color: #263449;
}
.ear__content {
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
}
.ear__options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.ear__option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border: 1px solid #dce3ec;
  border-radius: 6px;
  color: #4a5a70;
}
.ear__option.is-correct {
  border-color: #5bae7b;
  background: #edf7f1;
}
.ear__option.is-user {
  box-shadow: inset 0 0 0 1px #7597d5;
}
.ear__option-key {
  flex: none;
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background: #f1f4f9;
  font-size: 12px;
}
.ear__option-text {
  flex: 1;
  line-height: 1.5;
}
.ear__answers {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin-top: 12px;
  font-size: 14px;
  color: #4a5a70;
}
.ear__analysis {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  color: #4a5a70;
  white-space: pre-wrap;
}
.ear__analysis-title {
  margin-bottom: 4px;
  font-weight: 600;
  color: #263449;
}
</style>
