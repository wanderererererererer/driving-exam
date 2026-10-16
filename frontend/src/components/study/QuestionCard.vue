<script setup lang="ts">
import { computed, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { usePracticeStore } from '@/store/practice'

/**
 * 题目卡片：题干 + 选项 + 判分反馈 + 解析。
 *
 * <p>只读 store 状态，修改一律走 store 的 action。</p>
 *
 * <p><b>三种题型的作答方式</b>：SINGLE 点选项即提交；MULTIPLE 勾选后点「提交」；
 * JUDGE 渲染两个固定按钮（对/错），点击即提交，<b>不读 options</b>。</p>
 */
const store = usePracticeStore()

const question = computed(() => store.currentQuestion)
const result = computed(() => store.currentResult)
/** 用户已选答案（提交前后都在，用于回显） */
const selectedAnswer = computed(() => store.currentSelection)
const submitted = computed(() => !!result.value)
const isCorrect = computed(() => result.value?.isCorrect === true)

const isJudge = computed(() => question.value?.questionType === 'JUDGE')
const isMultiple = computed(() => question.value?.questionType === 'MULTIPLE')

const typeLabel = computed(() => {
  switch (question.value?.questionType) {
    case 'SINGLE':
      return '单选题'
    case 'MULTIPLE':
      return '多选题'
    case 'JUDGE':
      return '判断题'
    default:
      return ''
  }
})

/** 用户选中的选项 key 集合（判断题为「对」/「错」） */
const userSet = computed(() => new Set(selectedAnswer.value.split('').filter(Boolean)))
/** 正确答案的 key 集合（判断题放「对」/「错」本身） */
const correctSet = computed(() => {
  const ca = result.value?.correctAnswer ?? ''
  if (!ca) return new Set<string>()
  return isJudge.value ? new Set([ca]) : new Set(ca.split('').filter(Boolean))
})

/** 多选「提交」按钮可用条件：**用 selections 判断**（不是 results），且尚未提交 */
const canSubmitMultiple = computed(() => !submitted.value && selectedAnswer.value.length > 0)

/* ====================== 600ms 自动跳题（本组件最大的坑） ====================== */

/**
 * 答对后自动跳下一题的延迟。
 *
 * <p>600ms 是**平衡"用户能感知到答对了"与"不拖沓"**的经验值。
 * 如果用户反馈"跳太快来不及看"，可调大到 1000ms；反之可调小。</p>
 */
const AUTO_NEXT_DELAY_MS = 600

let autoNextTimer: number | null = null

function clearAutoNext() {
  if (autoNextTimer !== null) {
    window.clearTimeout(autoNextTimer)
    autoNextTimer = null
  }
}

function scheduleAutoNext() {
  clearAutoNext() // 先清，防止重复调度
  autoNextTimer = window.setTimeout(() => {
    autoNextTimer = null
    store.next()
  }, AUTO_NEXT_DELAY_MS)
}

// 双保险（主保险是 StudyView 给本组件加 :key="currentQuestion.id"，
// 换题时组件被重建 → onUnmounted 自动清计时器）。
// 这里的 watch 是"万一没加 :key"的兜底：只要当前题变了就清挂起的自动跳题，
// 否则会出现"手动点了下一题，600ms 后又自动跳一次"的双跳 bug。
watch(() => store.currentIndex, clearAutoNext)
watch(() => store.questions, clearAutoNext)
onUnmounted(clearAutoNext)

/* =============================== 作答 =============================== */
async function doSubmit(answer: string) {
  const q = question.value
  if (!q || submitted.value) return

  const r = await store.submit(q.id, answer)
  // 仅在"非提交 → 提交"的这一次判断，答对才排自动跳题
  if (r?.isCorrect) {
    scheduleAutoNext()
  }
}

function onOptionClick(optionKey: string) {
  if (submitted.value) return
  if (isMultiple.value) {
    // 多选：只切换选择，等用户点「提交」
    const q = question.value
    if (q) store.toggleSelection(q.id, optionKey)
  } else {
    // 单选：点击即提交
    void doSubmit(optionKey)
  }
}

function onSubmitMultiple() {
  if (!canSubmitMultiple.value) return
  void doSubmit(selectedAnswer.value)
}

function onJudgeClick(value: '对' | '错') {
  if (submitted.value) return
  void doSubmit(value)
}

function onAiHelp() {
  ElMessage('AI 帮助记忆功能开发中，将于智能问答模块上线')
}

/* ============================ 选项样式 ============================ */
function optionClass(key: string) {
  const cls: Record<string, boolean> = {}
  if (submitted.value) {
    const userPicked = userSet.value.has(key)
    const isRight = correctSet.value.has(key)
    cls['is-correct'] = isRight               // 正确答案：绿
    cls['is-wrong'] = userPicked && !isRight  // 选了但错：红
    cls['is-picked'] = userPicked
    cls['is-locked'] = true
  } else {
    cls['is-picked'] = userSet.value.has(key) // 未提交时：选中高亮（多选勾选态）
  }
  return cls
}
</script>

<template>
  <el-card v-if="question" class="qc" shadow="never">
    <!-- 题干 -->
    <div class="qc__head">
      <el-tag size="small" type="info">{{ typeLabel }}</el-tag>
      <span v-if="question.difficulty" class="qc__difficulty">难度 {{ question.difficulty }}</span>
    </div>

    <div class="qc__content">{{ question.content }}</div>

    <!-- 媒体（有值才显示） -->
    <div v-if="question.mediaUrl" class="qc__media">
      <el-image :src="question.mediaUrl" fit="contain" style="max-width: 100%" />
    </div>

    <!-- 判断题：两个固定按钮，不读 options -->
    <div v-if="isJudge" class="qc__judge">
      <div
        v-for="v in (['对', '错'] as const)"
        :key="v"
        class="qc__judge-btn"
        :class="optionClass(v)"
        @click="onJudgeClick(v)"
      >
        {{ v }}
      </div>
    </div>

    <!-- 单选 / 多选：选项列表 -->
    <div v-else class="qc__options">
      <div
        v-for="opt in question.options || []"
        :key="opt.optionKey"
        class="qc__option"
        :class="optionClass(opt.optionKey)"
        @click="onOptionClick(opt.optionKey)"
      >
        <span class="qc__option-key">{{ opt.optionKey }}</span>
        <span class="qc__option-text">{{ opt.optionContent }}</span>
      </div>
    </div>

    <!-- 多选的提交按钮 -->
    <div v-if="isMultiple && !submitted" class="qc__actions">
      <el-button type="primary" :disabled="!canSubmitMultiple" @click="onSubmitMultiple">
        提交
      </el-button>
    </div>

    <!-- 判分反馈 + 解析 -->
    <div v-if="submitted && result" class="qc__result">
      <div class="qc__verdict" :class="isCorrect ? 'is-ok' : 'is-bad'">
        {{ isCorrect ? '✓ 正确' : '✗ 错误' }}
        <span class="qc__answer">正确答案：{{ result.correctAnswer }}</span>
      </div>

      <div class="qc__analysis">{{ result.analysis || '暂无解析' }}</div>

      <div v-if="!isCorrect" class="qc__ai">
        <el-button size="small" type="warning" plain @click="onAiHelp">AI 帮助记忆</el-button>
      </div>
    </div>
  </el-card>

  <el-empty v-else description="暂无题目" />
</template>

<style scoped>
.qc__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.qc__difficulty {
  font-size: 12px;
  color: #909399;
}
.qc__content {
  font-size: 16px;
  line-height: 1.7;
  margin-bottom: 16px;
  white-space: pre-wrap;
}
.qc__media {
  margin-bottom: 16px;
}

/* 选项 */
.qc__options,
.qc__judge {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.qc__judge {
  flex-direction: row;
}
.qc__judge-btn {
  flex: 1;
  text-align: center;
  padding: 18px 0;
  font-size: 18px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.qc__option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.qc__option-key {
  flex: none;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: #f0f2f5;
  font-size: 13px;
}
.qc__option-text {
  flex: 1;
  line-height: 1.6;
}

/* 交互态 */
.qc__option:not(.is-locked):hover,
.qc__judge-btn:not(.is-locked):hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.is-picked {
  border-color: #409eff;
  background: #ecf5ff;
}
/* 判分后 */
.is-correct {
  border-color: #67c23a;
  background: #f0f9eb;
}
.is-wrong {
  border-color: #f56c6c;
  background: #fef0f0;
}
.is-locked {
  cursor: default;
}

.qc__actions {
  margin-top: 16px;
}

/* 解析区 */
.qc__result {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed #e4e7ed;
}
.qc__verdict {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
}
.qc__verdict.is-ok {
  color: #67c23a;
}
.qc__verdict.is-bad {
  color: #f56c6c;
}
.qc__answer {
  margin-left: 12px;
  font-weight: 400;
  color: #606266;
}
.qc__analysis {
  line-height: 1.7;
  color: #606266;
  white-space: pre-wrap;
}
.qc__ai {
  margin-top: 12px;
}
</style>
