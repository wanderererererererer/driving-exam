<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExamApi, getExamReportApi, judgeExamAnswerApi, saveExamAnswerApi, submitExamApi } from '@/api/exam'
import type { ExamReportVO, ExamVO, QuestionVO } from '@/types'

/**
 * 学员端 · 模拟考试【答题页】（服务器暂存 + 倒计时阶段）。
 *
 * <p>在前一阶段（题目渲染 / 本地作答 / 翻题 / 答题卡）基础上新增：</p>
 * <ul>
 *   <li><b>服务器暂存</b>：选项变化后调用 `POST /exam/{examId}/answer`（`ExamAnswerDTO`）；</li>
 *   <li><b>服务器恢复</b>：进入/刷新时用 `GET /exam/{examId}` 的 `answeredMap` 回填；</li>
 *   <li><b>倒计时</b>：以 `remainingSeconds` 为起点；归零后锁定作答并自动请求交卷，
 *       后端定时任务也会独立收尾，避免客户端关闭/延迟导致考试长期停留 ONGOING。</li>
 * </ul>
 *
 * <p><b>防作弊</b>：题目不含答案/解析，本页也不展示正确答案。</p>
 *
 * <p><b>保存并发</b>：后端 upsert 无版本号、后到者胜 → 本页对**同一题**用"收敛循环 + 哨兵"串行化
 * （{@link ensureSaved}），确保新答案不会被旧请求覆盖。</p>
 *
 * <p>范围：答题、服务器暂存、倒计时、交卷和成绩汇总；逐题报告由独立报告页展示。</p>
 */
const route = useRoute()
const router = useRouter()

const examId = computed(() => Number(route.params.examId))

const exam = ref<ExamVO | null>(null)
const loading = ref(false)
const loadError = ref(false)

/** 当前答案（含未保存）：questionId → userAnswer（SINGLE 'A' / MULTIPLE 'AB' / JUDGE '对'|'错'） */
const answers = reactive(new Map<number, string>())
/** 服务器已确认保存的答案：questionId → userAnswer */
const savedAnswers = reactive(new Map<number, string>())
/** 正在保存中的题目 */
const savingSet = reactive(new Set<number>())
/** 保存失败的题目 */
const saveError = reactive(new Set<number>())
/** 请求进行中的题目（非响应式，用于串行化：一题同时只跑一条保存链） */
const inFlight = new Set<number>()

/** 已判题结果：questionId → isCorrect（**服务端**判题；答题卡正误显示 + 刷新恢复） */
const correctMap = reactive(new Map<number, boolean>())
/** 正在「下一题」流程中（等保存 + 判题）；防止连点造成重复判题/乱序推进 */
const advancing = ref(false)
/** 正在交卷；防止重复提交（交卷期间禁用交卷按钮） */
const submitting = ref(false)

/** 本场成绩（来源：交卷响应 或 按 examId 重新拉取的报告接口；**非前端计算**） */
const report = ref<ExamReportVO | null>(null)
const reportLoading = ref(false)
const reportError = ref(false)

const currentIndex = ref(0)

const questions = computed<QuestionVO[]>(() => exam.value?.questions ?? [])
const total = computed(() => questions.value.length)
const currentQuestion = computed<QuestionVO | null>(() => questions.value[currentIndex.value] ?? null)

/** 是否进行中（非 ONGOING = 已交卷/已超时） */
const ongoing = computed(() => exam.value?.status === 'ONGOING')

const isJudge = computed(() => currentQuestion.value?.questionType === 'JUDGE')
const isMultiple = computed(() => currentQuestion.value?.questionType === 'MULTIPLE')

/* =============================== 倒计时 =============================== */

/** 剩余秒数（来自后端 remainingSeconds，前端仅做本地递减展示） */
const remainingSeconds = ref<number | null>(null)
/** 时间已到（前端状态，不代表后端已交卷） */
const timeUp = ref(false)
/** 超时自动交卷失败，允许用户手动重试 */
const timeUpSubmitError = ref(false)
let timerId: number | null = null

/** 是否禁止作答：考试已结束 或 时间已到 */
const locked = computed(() => !ongoing.value || timeUp.value)

function stopTimer(): void {
  if (timerId !== null) {
    window.clearInterval(timerId)
    timerId = null
  }
}

/** 时间到：停止计时、锁定作答，并立即请求后端交卷；后端仍会以服务端时间再次判定超时。 */
function markTimeUp(): void {
  stopTimer()
  remainingSeconds.value = 0
  timeUp.value = true
  timeUpSubmitError.value = false
  void submitExam(false)
}

/** 用后端返回的剩余秒数重置并启动计时（单实例：先清后建） */
function startTimer(seconds: number | null): void {
  stopTimer()
  timeUp.value = false
  remainingSeconds.value = seconds
  if (!ongoing.value) return // 已结束不计时
  if (seconds === null || seconds <= 0) {
    markTimeUp()
    return
  }
  timerId = window.setInterval(() => {
    const cur = remainingSeconds.value
    if (cur === null) return
    const next = cur - 1
    remainingSeconds.value = next
    if (next <= 0) markTimeUp()
  }, 1000)
}

/** 倒计时显示：MM:SS；超过 1 小时用 HH:MM:SS */
const timeText = computed(() => {
  const s = remainingSeconds.value
  if (s === null) return '--:--'
  const sec = Math.max(0, Math.floor(s))
  const pad = (n: number): string => String(n).padStart(2, '0')
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const ss = sec % 60
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(ss)}` : `${pad(m)}:${pad(ss)}`
})

onUnmounted(stopTimer)

/* =============================== 保存状态 =============================== */

type SaveState = 'none' | 'saving' | 'saved' | 'dirty' | 'error'

/**
 * 单题保存状态。
 *
 * <ul>
 *   <li>{@code none}：未作答；</li>
 *   <li>{@code saving}：正在保存；</li>
 *   <li>{@code dirty}：已选但尚未确认保存（排队中/保存过又改动）；</li>
 *   <li>{@code saved}：与服务器已保存值一致；</li>
 *   <li>{@code error}：保存失败。</li>
 * </ul>
 */
function saveStateOf(qid: number): SaveState {
  if (saveError.has(qid)) return 'error'
  const cur = answers.get(qid)
  if (cur === undefined || cur === '') return 'none'
  if (savingSet.has(qid)) return 'saving'
  return savedAnswers.get(qid) === cur ? 'saved' : 'dirty'
}

const answeredCount = computed(() => questions.value.filter((q) => answers.has(q.id)).length)
const savedCount = computed(() => questions.value.filter((q) => saveStateOf(q.id) === 'saved').length)
const pendingCount = computed(
  () => questions.value.filter((q) => ['saving', 'dirty'].includes(saveStateOf(q.id))).length,
)
const errorCount = computed(() => questions.value.filter((q) => saveError.has(q.id)).length)

/**
 * 保存某题到最新答案（**串行化 + 收敛**）。
 *
 * <p>同一题同时只跑一条"保存链"：链内循环——只要"当前答案 ≠ 已保存值"就再存一次，直到收敛。
 * 这样即便用户在请求飞行途中又改了答案，也只会以**最新值**收尾，不会让旧请求覆盖新答案。</p>
 *
 * <p>空答案不保存：后端 `userAnswer` 为 `@NotBlank`（空串会 1001），且没有"清除答案"接口。</p>
 */
async function ensureSaved(qid: number): Promise<void> {
  if (inFlight.has(qid)) return // 已有保存链在跑，它会收敛到最新值
  inFlight.add(qid)
  saveError.delete(qid)
  try {
    for (;;) {
      const desired = answers.get(qid) ?? ''
      if (desired === '') break // 不保存空答案（后端不接受）
      if ((savedAnswers.get(qid) ?? '') === desired) break // 已收敛
      savingSet.add(qid)
      await saveExamAnswerApi(examId.value, qid, desired)
      savingSet.delete(qid)
      savedAnswers.set(qid, desired)
    }
  } catch {
    // 失败提示由请求拦截器统一弹出（如 2102 已交卷）；此处落地"保存失败"态 + 供重试
    savingSet.delete(qid)
    saveError.add(qid)
  } finally {
    savingSet.delete(qid)
    inFlight.delete(qid)
  }
}

/* =============================== 加载 / 恢复 =============================== */

/** 返回试卷列表（带科目上下文，缺则回首页） */
function backTo(): string {
  const sc = String(route.query.subjectCode ?? '')
  return sc ? `/student/exam?subjectCode=${sc}` : '/student/dashboard'
}

function goBack(): void {
  router.push(backTo())
}

/**
 * 退出考试（答题页**唯一**的离开入口）。
 *
 * <p>只做导航：**不交卷、不改考试状态**。后端当前**不支持暂停**（详见工作日志），
 * 因此退出后本场考试仍在进行、`remainingSeconds` 继续减少——提示按真实行为写，**不谎称已暂停**。
 * 重新进入时从试卷列表「继续考试」按 `ongoingExamId` 恢复**同一场**。</p>
 */
function exitExam(): void {
  ElMessageBox.confirm(
    '退出后本场考试仍在进行，剩余时间会继续减少（当前未实现暂停）；可稍后从试卷列表「继续考试」回到本场。是否退出？',
    '退出考试',
    { type: 'warning', confirmButtonText: '退出', cancelButtonText: '继续答题' },
  )
    .then(() => {
      stopTimer()
      router.push(backTo())
    })
    .catch(() => {})
}

/** 加载/恢复考试：按 examId 拉取后端真实状态（题目 + 已答 + 剩余时间 + 状态） */
async function load(): Promise<void> {
  stopTimer()
  const id = examId.value
  if (!Number.isInteger(id) || id <= 0) {
    ElMessage.error('无效的考试')
    router.replace(backTo())
    return
  }
  loading.value = true
  loadError.value = false
  timeUpSubmitError.value = false
  try {
    const vo = await getExamApi(id)
    exam.value = vo

    // 重置答案与保存状态，并以服务器 answeredMap 回填（回填的都算"已保存"）
    answers.clear()
    savedAnswers.clear()
    savingSet.clear()
    saveError.clear()
    inFlight.clear()
    correctMap.clear()
    if (vo.answeredMap) {
      for (const [qid, ans] of Object.entries(vo.answeredMap)) {
        answers.set(Number(qid), ans)
        savedAnswers.set(Number(qid), ans)
      }
    }
    // 恢复已判题结果（服务端计算，仅正误、不含标准答案）
    if (vo.correctMap) {
      for (const [qid, ok] of Object.entries(vo.correctMap)) {
        correctMap.set(Number(qid), ok === true)
      }
    }
    currentIndex.value = 0

    // 倒计时以服务端剩余秒数为准（刷新/重进都重新取）
    startTimer(vo.remainingSeconds ?? null)

    // 已结束的考试（已交卷 / 已超时）：按 examId 重新取得成绩，供刷新成绩页
    report.value = null
    reportError.value = false
    if (vo.status !== 'ONGOING') {
      void loadReport()
    }
  } catch {
    // 失败提示（含 2104 不存在/非本人）由请求拦截器统一弹出
    exam.value = null
    loadError.value = true
    stopTimer()
  } finally {
    loading.value = false
  }
}

/**
 * 取得本场成绩（已结束考试）。**来源为后端报告接口，非前端计算**；用于刷新 / 直接打开已交卷考试。
 */
async function loadReport(): Promise<void> {
  reportLoading.value = true
  reportError.value = false
  try {
    report.value = await getExamReportApi(examId.value)
  } catch {
    // 失败提示由拦截器统一弹出；此处落地错误态（已交卷 → 不当作进行中、不重新创建考试）
    report.value = null
    reportError.value = true
  } finally {
    reportLoading.value = false
  }
}

// immediate：进入即加载；同时覆盖路由 examId 变化
watch(examId, () => void load(), { immediate: true })

/* =============================== 作答（本地更新 + 服务器暂存） =============================== */

function pickSingle(optionKey: string): void {
  const q = currentQuestion.value
  if (!q || locked.value) return
  if (answers.get(q.id) === optionKey) return // 未变化，不触发保存
  answers.set(q.id, optionKey)
  void ensureSaved(q.id)
}

function toggleMultiple(optionKey: string): void {
  const q = currentQuestion.value
  if (!q || locked.value) return
  const cur = answers.get(q.id) ?? ''
  const set = new Set(cur.split('').filter(Boolean))
  if (set.has(optionKey)) set.delete(optionKey)
  else set.add(optionKey)
  const joined = [...set].sort().join('') // 字母升序、无分隔 —— 与后端约定一致（如 'AB'）
  if (joined) answers.set(q.id, joined)
  else answers.delete(q.id)
  void ensureSaved(q.id)
}

function pickJudge(value: '对' | '错'): void {
  const q = currentQuestion.value
  if (!q || locked.value) return
  if (answers.get(q.id) === value) return
  answers.set(q.id, value)
  void ensureSaved(q.id)
}

/** 保存失败后手动重试当前题 */
function retryCurrent(): void {
  const q = currentQuestion.value
  if (q) void ensureSaved(q.id)
}

/** 是否已选某选项 key（单选/多选为选项标识，判断题为 '对'/'错'） */
function isPicked(key: string): boolean {
  const q = currentQuestion.value
  if (!q) return false
  return (answers.get(q.id) ?? '').split('').includes(key)
}

const saveStateLabel: Record<SaveState, string> = {
  none: '',
  saving: '保存中…',
  dirty: '待保存…',
  saved: '已保存',
  error: '保存失败',
}

/* =============================== 导航 =============================== */

function goPrev(): void {
  if (currentIndex.value > 0) currentIndex.value -= 1
}
/**
 * 下一题：**先确保当前题答案已保存，再判题更新答题卡，最后推进**。
 *
 * <p>保存失败 → 提示重试、**不推进**（不得用未保存答案判题）；判题失败 → 提示、**不推进**
 * （保留答案，可再点「下一题」重试）；连点由 {@code advancing} 兜底，避免重复判题 / 乱序推进。</p>
 */
async function goNext(): Promise<void> {
  if (locked.value || advancing.value) return // 超时/已结束后不再判题或推进
  if (currentIndex.value >= total.value - 1) return // 已是最后一题
  const q = currentQuestion.value
  if (q && answers.has(q.id)) {
    advancing.value = true
    try {
      await ensureSaved(q.id) // 等保存链收敛到当前答案
      if (saveError.has(q.id)) {
        ElMessage.error('本题答案保存失败，请点「重试保存」后再继续')
        return // 不推进
      }
      await judgeOne(q.id) // 判题（失败会 throw）
    } catch {
      // 判题失败：拦截器已弹提示；不推进，用户可再点「下一题」重试
      return
    } finally {
      advancing.value = false
    }
  }
  currentIndex.value += 1
}

/** 调后端对单题判题，写入 correctMap（只回正误，不回标准答案） */
async function judgeOne(questionId: number): Promise<void> {
  const res = await judgeExamAnswerApi(examId.value, questionId)
  if (res.answered) correctMap.set(questionId, res.isCorrect === true)
  else correctMap.delete(questionId)
}

/**
 * 交卷。
 *
 * <p>答案已逐题存到服务器，交卷请求 body 为空 → 后端从草稿读答案并统一判分（幂等；是否超时由后端判定）。
 * 成功后：停止计时、提示得分/是否及格、切入页面既有的「已结束」状态，并提供逐题报告入口。
 * 失败：由请求拦截器统一提示，按钮恢复可再次点击；超时自动交卷失败时保留重试入口。</p>
 */
async function submitExam(confirm = true): Promise<void> {
  const e = exam.value
  if (!e || !ongoing.value || submitting.value) return

  if (confirm) {
    try {
      await ElMessageBox.confirm('确定要交卷吗？交卷后不可再修改答案。', '交卷确认', {
        type: 'warning',
        confirmButtonText: '交卷',
        cancelButtonText: '继续答题',
      })
    } catch {
      return // 用户取消
    }
  }

  submitting.value = true
  timeUpSubmitError.value = false
  try {
    const rpt = await submitExamApi(examId.value)
    stopTimer()
    // 直接用交卷响应作为成绩（不重复请求）；并切到「已结束」状态
    report.value = rpt
    reportError.value = false
    exam.value = { ...e, status: timeUp.value ? 'TIMEOUT' : 'SUBMITTED' }
    ElMessage.success(timeUp.value ? '时间已到，已自动交卷' : '已交卷，成绩已生成')
  } catch {
    // 交卷失败：拦截器已弹提示；超时场景保留手动重试入口
    timeUpSubmitError.value = timeUp.value
  } finally {
    submitting.value = false
  }
}

/** 查看本场逐题报告 */
function goReport(): void {
  const sc = String(route.query.subjectCode ?? '')
  router.push({
    path: `/student/exam/${examId.value}/report`,
    query: sc ? { subjectCode: sc } : {},
  })
}
function jumpTo(i: number): void {
  currentIndex.value = i
}

/** 答题卡格子状态 */
function cardClass(i: number): Record<string, boolean> {
  const q = questions.value[i]
  if (!q) return {}
  const st = saveStateOf(q.id)
  const judged = correctMap.get(q.id)
  return {
    'is-current': i === currentIndex.value,
    'is-correct': judged === true, // 蓝：答对
    'is-wrong': judged === false, // 红：答错
    // 已保存但未判题 → 中性灰（区别于白色未答，也区别于蓝/红）
    'is-answered': judged === undefined && answers.has(q.id),
    'is-pending': judged === undefined && (st === 'saving' || st === 'dirty'),
    'is-error': st === 'error', // 保存失败
  }
}

const typeLabel = computed(() => {
  switch (currentQuestion.value?.questionType) {
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

const progressText = computed(() =>
  total.value === 0 ? '' : `第 ${currentIndex.value + 1} / ${total.value} 题`,
)
</script>

<template>
  <div class="ea">
    <!-- 首次加载中 -->
    <el-skeleton v-if="loading" :rows="6" animated />

    <!-- 加载失败：允许重试 -->
    <el-result
      v-else-if="loadError"
      icon="error"
      title="考试加载失败"
      sub-title="考试不存在、不属于你，或网络异常；请重试或返回"
    >
      <template #extra>
        <el-button @click="goBack">返回</el-button>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <template v-else-if="exam">
      <!-- 已交卷 / 已超时：成绩结算（页面主体持续显示成绩） -->
      <div v-if="!ongoing" class="ea__result">
        <h2 class="ea__result-title">{{ exam.paperTitle }} · 考试结束</h2>
        <p class="ea__result-sub">
          {{ exam.status === 'TIMEOUT' ? '本场考试已超时收尾，不可再作答' : '本场考试已提交，不可再作答' }}
        </p>

        <!-- 成绩加载中（刷新成绩页时） -->
        <el-skeleton v-if="reportLoading" :rows="4" animated class="ea__result-skel" />

        <!-- 成绩加载失败：明确提示 + 重新加载（不当作进行中、不新建考试） -->
        <div v-else-if="reportError" class="ea__result-error">
          <el-alert
            type="error"
            :closable="false"
            show-icon
            title="成绩加载失败"
            description="本场考试已交卷，但成绩暂时取不到；可重新加载或返回列表。"
          />
          <div class="ea__result-actions">
            <el-button @click="goBack">返回考试列表</el-button>
            <el-button type="primary" @click="loadReport">重新加载</el-button>
          </div>
        </div>

        <!-- 成绩（字段均来自后端交卷 / 报告接口） -->
        <template v-else-if="report">
          <div class="ea__score">
            <span class="ea__score-num">{{ report.score }}</span>
            <span class="ea__score-total">/ {{ report.totalScore }} 分</span>
          </div>
          <el-tag
            :type="report.isPass ? 'success' : 'danger'"
            size="large"
            effect="dark"
            class="ea__pass"
          >
            {{ report.isPass ? '及格' : '未及格' }}
          </el-tag>
          <div class="ea__counts">
            <span>正确 <b>{{ report.correctCount }}</b></span>
            <span>错误 <b>{{ report.wrongCount }}</b></span>
            <span>未答 <b>{{ report.unansweredCount }}</b></span>
            <span>及格线 <b>{{ report.passScore }}</b></span>
          </div>
          <div class="ea__result-actions">
            <el-button type="primary" @click="goReport">查看逐题报告</el-button>
            <el-button @click="goBack">返回考试列表</el-button>
          </div>
        </template>

        <!-- 兜底（报告为空且非加载/出错）：仅返回 -->
        <div v-else class="ea__result-actions">
          <el-button @click="goBack">返回考试列表</el-button>
        </div>
      </div>

      <!-- 题目为空 -->
      <el-empty v-else-if="total === 0" description="本场考试暂无题目" />

      <!-- 答题主体 -->
      <div v-else class="ea__wrap">
        <div class="ea__head">
          <el-button text @click="exitExam">← 退出考试</el-button>
          <span class="ea__title">{{ exam.paperTitle }}</span>
          <span class="ea__progress">{{ progressText }} · 已答 {{ answeredCount }}/{{ total }}</span>
          <span class="ea__clock" :class="{ 'is-low': remainingSeconds !== null && remainingSeconds <= 60 }">
            剩余 {{ timeText }}
          </span>
        </div>

        <!-- 时间已到：锁定作答并自动交卷；失败时保留手动重试入口 -->
        <el-alert
          v-if="timeUp"
          class="ea__banner"
          type="error"
          :closable="false"
          show-icon
          :title="timeUpSubmitError ? '考试时间已到，自动交卷失败' : '考试时间已到'"
          :description="
            timeUpSubmitError
              ? '自动交卷未成功，请点击下方「交卷」重试；答案已按服务器暂存内容结算。'
              : submitting
                ? '正在自动交卷，请稍候…'
                : '已自动交卷，当前不可继续作答。'
          "
        />

        <!-- 保存状态汇总 -->
        <el-alert
          v-else
          class="ea__banner"
          :type="errorCount > 0 ? 'error' : 'info'"
          :closable="false"
          show-icon
          :title="`答案自动保存到服务器：已保存 ${savedCount} / ${total} 题`"
          :description="
            errorCount > 0
              ? `${errorCount} 题保存失败（答题卡标红，可在题目处重试）`
              : pendingCount > 0
                ? `${pendingCount} 题保存中/待确认…`
                : '所有已作答题目均已保存'
          "
        />

        <div class="ea__body">
          <!-- 题目 + 选项 -->
          <div class="ea__main">
            <el-card v-if="currentQuestion" shadow="never">
              <div class="ea__qmeta">
                <el-tag size="small" type="info">{{ typeLabel }}</el-tag>
                <span v-if="currentQuestion.difficulty" class="ea__diff">
                  难度 {{ currentQuestion.difficulty }}
                </span>
                <!-- 当前题保存状态 -->
                <span
                  v-if="saveStateOf(currentQuestion.id) !== 'none'"
                  class="ea__save"
                  :class="`is-${saveStateOf(currentQuestion.id)}`"
                >
                  {{ saveStateLabel[saveStateOf(currentQuestion.id)] }}
                </span>
                <el-button
                  v-if="saveStateOf(currentQuestion.id) === 'error'"
                  size="small"
                  type="danger"
                  plain
                  @click="retryCurrent"
                >
                  重试保存
                </el-button>
              </div>

              <div class="ea__content">{{ currentQuestion.content }}</div>

              <div v-if="currentQuestion.mediaUrl" class="ea__media">
                <el-image :src="currentQuestion.mediaUrl" fit="contain" style="max-width: 100%" />
              </div>

              <!-- 判断题：两个固定按钮，不读 options -->
              <div v-if="isJudge" class="ea__judge">
                <div
                  v-for="v in (['对', '错'] as const)"
                  :key="v"
                  class="ea__judge-btn"
                  :class="{ 'is-picked': isPicked(v), 'is-locked': locked }"
                  @click="pickJudge(v)"
                >
                  {{ v }}
                </div>
              </div>

              <!-- 单选 / 多选 -->
              <div v-else class="ea__options">
                <div
                  v-for="opt in currentQuestion.options || []"
                  :key="opt.optionKey"
                  class="ea__option"
                  :class="{ 'is-picked': isPicked(opt.optionKey), 'is-locked': locked }"
                  @click="isMultiple ? toggleMultiple(opt.optionKey) : pickSingle(opt.optionKey)"
                >
                  <span class="ea__option-key">{{ opt.optionKey }}</span>
                  <span class="ea__option-text">{{ opt.optionContent }}</span>
                </div>
              </div>

              <div v-if="isMultiple" class="ea__hint">多选题：可选多个选项</div>
            </el-card>

            <div class="ea__nav">
              <el-button :disabled="currentIndex === 0" @click="goPrev">上一题</el-button>
              <el-button
                type="primary"
                :loading="advancing"
                :disabled="currentIndex >= total - 1"
                @click="goNext"
              >
                下一题
              </el-button>
              <el-button
                type="danger"
                :loading="submitting"
                :disabled="submitting || advancing"
                @click="submitExam"
              >
                交卷
              </el-button>
            </div>
          </div>

          <!-- 答题卡 -->
          <div class="ea__card">
            <div class="ea__card-title">答题卡</div>
            <div class="ea__grid">
              <div
                v-for="(q, i) in questions"
                :key="q.id"
                class="ea__cell"
                :class="cardClass(i)"
                @click="jumpTo(i)"
              >
                <span class="ea__cell-num">{{ i + 1 }}</span>
                <span v-if="correctMap.get(q.id) === true" class="ea__cell-mark">✓</span>
                <span v-else-if="correctMap.get(q.id) === false" class="ea__cell-mark">✗</span>
              </div>
            </div>
            <div class="ea__legend">
              <span><i class="ea__dot is-current" />当前</span>
              <span><i class="ea__dot" />未答</span>
              <span><i class="ea__dot is-answered" />已答(未判)</span>
              <span><i class="ea__dot is-correct" />答对</span>
              <span><i class="ea__dot is-wrong" />答错</span>
              <span><i class="ea__dot is-error" />失败</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.ea__wrap {
  padding: 16px;
}
.ea__head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}
.ea__title {
  font-size: 18px;
  font-weight: 600;
  color: #263449;
}
.ea__progress {
  margin-left: auto;
  font-size: 14px;
  color: #4a5a70;
}
.ea__clock {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #7597d5;
}
.ea__clock.is-low {
  color: #c96a6a;
}
.ea__banner {
  margin-bottom: 16px;
}
.ea__body {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.ea__main {
  flex: 1;
  min-width: 0;
}
.ea__qmeta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.ea__diff {
  font-size: 12px;
  color: #687589;
}
.ea__save {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 10px;
  background: #f1f4f9;
  color: #687589;
}
.ea__save.is-saved {
  background: #edf7f1;
  color: #5bae7b;
}
.ea__save.is-dirty,
.ea__save.is-saving {
  background: #fbf4e8;
  color: #c98f4a;
}
.ea__save.is-error {
  background: #fbeded;
  color: #c96a6a;
}
.ea__content {
  font-size: 16px;
  line-height: 1.7;
  margin-bottom: 16px;
  white-space: pre-wrap;
}
.ea__media {
  margin-bottom: 16px;
}

/* 选项 */
.ea__options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ea__option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid #dce3ec;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.ea__option-key {
  flex: none;
  width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  border-radius: 50%;
  background: #f1f4f9;
  font-size: 13px;
}
.ea__option-text {
  flex: 1;
  line-height: 1.6;
}
.ea__option:not(.is-locked):hover {
  border-color: #7597d5;
  background: #e8effb;
}
/* 判断题：两列并排、等宽，整体收窄，避免两个按钮占满页面 */
.ea__judge {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  max-width: 320px;
}
.ea__judge-btn {
  text-align: center;
  padding: 11px 0;
  font-size: 15px;
  border: 1px solid #dce3ec;
  border-radius: 6px;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.ea__judge-btn:not(.is-locked):hover {
  border-color: #7597d5;
  background: #e8effb;
}
.is-picked {
  border-color: #7597d5;
  background: #e8effb;
}
.is-locked {
  cursor: default;
  opacity: 0.85;
}
.ea__hint {
  margin-top: 12px;
  font-size: 12px;
  color: #687589;
}
.ea__nav {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

/* ---------------- 成绩结算（已交卷 / 已超时） ---------------- */
.ea__result {
  padding: 24px 16px;
  text-align: center;
}
.ea__result-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #263449;
}
.ea__result-sub {
  margin: 6px 0 20px;
  color: #687589;
}
.ea__result-skel {
  max-width: 420px;
  margin: 0 auto;
}
.ea__score {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
}
.ea__score-num {
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  color: #7597d5;
}
.ea__score-total {
  font-size: 20px;
  color: #4a5a70;
}
.ea__pass {
  margin-top: 12px;
}
.ea__counts {
  margin-top: 18px;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px 24px;
  color: #4a5a70;
  font-size: 14px;
}
.ea__counts b {
  color: #263449;
}
.ea__result-actions {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: center;
}
.ea__result-error {
  max-width: 520px;
  margin: 0 auto;
}

/* 答题卡 */
.ea__card {
  flex: 0 0 240px;
  max-width: 240px;
  background: #fff;
  border-radius: 4px;
  padding: 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
.ea__card-title {
  font-size: 14px;
  font-weight: 600;
  color: #263449;
  margin-bottom: 12px;
}
.ea__grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ea__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid #dce3ec;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  color: #4a5a70;
  transition: all 0.15s;
}
.ea__cell-num {
  line-height: 1;
}
.ea__cell-mark {
  margin-top: 1px;
  font-size: 10px;
  line-height: 1;
}
.ea__cell:hover {
  border-color: #7597d5;
}
/* 已作答但未判题：中性灰（区别于白色"未答"） */
.ea__cell.is-answered {
  background: #f2f4f7;
  border-color: #dce3ec;
  color: #687589;
}
/* 蓝：答对 */
.ea__cell.is-correct {
  border-color: #7597d5;
  background: #e8effb;
  color: #7597d5;
}
/* 红：答错 */
.ea__cell.is-wrong {
  border-color: #c96a6a;
  background: #fbeded;
  color: #c96a6a;
}
/* 待保存 / 保存失败：橙（与正误的蓝/红区分） */
.ea__cell.is-pending {
  border-color: #c98f4a;
  color: #c98f4a;
}
.ea__cell.is-error {
  border-color: #c98f4a;
  border-style: dashed;
  color: #c98f4a;
}
/* 当前题：用蓝环（非填充），保证正误底色仍可见 */
.ea__cell.is-current {
  border-color: #7597d5;
  box-shadow: 0 0 0 2px rgba(117, 151, 213, 0.28);
}
.ea__legend {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #687589;
}
.ea__legend span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.ea__dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  border: 1px solid #dce3ec;
}
.ea__dot.is-answered {
  background: #f2f4f7;
}
.ea__dot.is-correct {
  border-color: #7597d5;
  background: #e8effb;
}
.ea__dot.is-wrong {
  border-color: #c96a6a;
  background: #fbeded;
}
.ea__dot.is-current {
  box-shadow: 0 0 0 2px rgba(117, 151, 213, 0.28);
}
.ea__dot.is-error {
  border-color: #c98f4a;
}
</style>
