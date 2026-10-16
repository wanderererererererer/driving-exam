import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AnswerResultVO, PageResult, PracticeMode, QuestionVO } from '@/types'
import {
  STUDY_PAGE_SIZE,
  getChapterQuestionsApi,
  getRandomQuestionsApi,
  getSequenceQuestionsApi,
  submitAnswerApi,
} from '@/api/study'
import { getRetryQuestionsApi, submitRetryAnswerApi } from '@/api/wrongBook'

/** 距末尾还剩这么多题时触发预加载（滑动到"倒数第 5 题"） */
const PRELOAD_THRESHOLD = 5

/** 随机 / 重练模式一次取多少题 */
const NON_PAGED_DEFAULT_COUNT = 10

/** 首次加载参数 */
export interface LoadQuestionsParams {
  subjectCode: string
  mode: PracticeMode
  /** CHAPTER 模式必传 */
  knowledgePointId?: number | null
  /** RANDOM / RETRY 模式生效 */
  count?: number
}

/**
 * 练习会话 store（组合式写法）。
 *
 * <p>覆盖四种练习模式，差异只在"取题接口"与"提交接口"：</p>
 * <ul>
 *   <li>SEQUENCE 顺序 → 分页，支持预加载</li>
 *   <li>CHAPTER 章节 → 分页，支持预加载（需 knowledgePointId）</li>
 *   <li>RANDOM 随机 → 一次性取 count 道，无预加载</li>
 *   <li>RETRY 错题重练 → 一次性取 count 道，无预加载；提交走重练接口</li>
 * </ul>
 *
 * <p><b>不持久化</b>：刷新即重新开始（练习是短会话，恢复现场不值得）。</p>
 */
export const usePracticeStore = defineStore('practice', () => {
  /* ============================== state ============================== */
  const questions = ref<QuestionVO[]>([])
  const currentIndex = ref(0)

  /** questionId → 用户的作答（提交前后都保留，用于回显选项） */
  const selections = ref<Map<number, string>>(new Map())
  /** questionId → 判分结果 */
  const results = ref<Map<number, AnswerResultVO>>(new Map())

  const mode = ref<PracticeMode | null>(null)
  const subjectCode = ref<string | null>(null)
  const knowledgePointId = ref<number | null>(null)
  /**
   * 本轮**题目总数**。
   *
   * <p>分页模式（SEQUENCE / CHAPTER）取接口 {@code PageResult.total}——即该科目/章节的**全部题数**，
   * 不是已加载的 {@code questions.length}（预加载会不断追加）。
   * 非分页模式（RANDOM / RETRY）接口只返回数组，取 {@code questions.length}。</p>
   */
  const totalQuestions = ref(0)
  /** 已加载到第几页（非分页模式恒为 0） */
  const pageNum = ref(0)
  const hasMore = ref(false)

  /** 首次加载中（UI 可显示骨架/遮罩） */
  const loading = ref(false)
  /** 静默预加载中（UI **不应**显示任何东西，避免答题时闪遮罩） */
  const preloading = ref(false)
  /** 提交中（防重复提交） */
  const submitting = ref(false)

  /* ============================= getters ============================= */
  const currentQuestion = computed<QuestionVO | null>(() => questions.value[currentIndex.value] ?? null)
  const total = computed(() => questions.value.length)

  const progressText = computed(() =>
    total.value === 0 ? '暂无题目' : `第 ${currentIndex.value + 1} / ${total.value} 题`,
  )

  const canPrev = computed(() => currentIndex.value > 0)
  const canNext = computed(() => currentIndex.value < total.value - 1)

  /** 是否分页模式（决定要不要预加载） */
  const isPagedMode = computed(() => mode.value === 'SEQUENCE' || mode.value === 'CHAPTER')

  /** 当前题用户已选的答案（未选则空串） */
  const currentSelection = computed(() => {
    const q = currentQuestion.value
    return q ? (selections.value.get(q.id) ?? '') : ''
  })

  /** 当前题的判分结果（未提交则 null） */
  const currentResult = computed<AnswerResultVO | null>(() => {
    const q = currentQuestion.value
    return q ? (results.value.get(q.id) ?? null) : null
  })

  /**
   * 当前题**是否已提交**。
   *
   * <p>判定依据是 {@code results}（判分结果），**不是** {@code selections}——
   * 后者只是"用户选了什么"，单选/多选在提交前就已写入，用它判断会误判。</p>
   */
  const hasAnsweredCurrent = computed(() => {
    const q = currentQuestion.value
    return q ? results.value.has(q.id) : false
  })

  function resultOf(questionId: number): AnswerResultVO | null {
    return results.value.get(questionId) ?? null
  }

  /* ============================= actions ============================= */

  /** 清空全部状态。切换科目 / 切换模式 / 退出刷题页时由**调用方**触发。 */
  function reset() {
    questions.value = []
    currentIndex.value = 0
    selections.value = new Map()
    results.value = new Map()
    mode.value = null
    subjectCode.value = null
    knowledgePointId.value = null
    totalQuestions.value = 0
    pageNum.value = 0
    hasMore.value = false
    loading.value = false
    preloading.value = false
    submitting.value = false
  }

  /**
   * 首次加载题目（切换科目 / 模式时调用）。
   *
   * <p>⚠️ <b>调用前请先 {@link reset}</b>，否则上一轮的 selections / results 会残留
   * （按 questionId 记录，不同轮次若撞到同一题会显示旧结果）。</p>
   */
  async function loadQuestions(params: LoadQuestionsParams) {
    subjectCode.value = params.subjectCode
    mode.value = params.mode
    knowledgePointId.value = params.knowledgePointId ?? null

    loading.value = true
    try {
      if (params.mode === 'SEQUENCE') {
        const page = await getSequenceQuestionsApi(params.subjectCode, 1)
        applyPage(page)
      } else if (params.mode === 'CHAPTER') {
        if (params.knowledgePointId == null) {
          // 章节模式必须带知识点；调用方传错时不发请求，避免拿到全库题目
          questions.value = []
          totalQuestions.value = 0
          pageNum.value = 0
          hasMore.value = false
          return
        }
        const page = await getChapterQuestionsApi(params.knowledgePointId, 1)
        applyPage(page)
      } else {
        // RANDOM / RETRY：不分页，一次取完
        const count = params.count ?? NON_PAGED_DEFAULT_COUNT
        questions.value =
          params.mode === 'RETRY'
            ? await getRetryQuestionsApi(params.subjectCode, count)
            : await getRandomQuestionsApi(params.subjectCode, count)
        totalQuestions.value = questions.value.length
        pageNum.value = 0
        hasMore.value = false
      }
      currentIndex.value = 0
    } finally {
      loading.value = false
    }
  }

  /** 把一页结果写入 questions / totalQuestions / pageNum / hasMore */
  function applyPage(page: PageResult<QuestionVO>) {
    questions.value = page.records
    totalQuestions.value = page.total      // 全部题数（不是本页条数）
    pageNum.value = page.pageNum
    hasMore.value = page.pageNum < page.pages
  }

  /**
   * 预加载：滑到倒数第 {@link PRELOAD_THRESHOLD} 题时，后台**静默**拉下一页并追加。
   *
   * <p>只有 SEQUENCE / CHAPTER 两种分页模式生效；随机与重练一次性给完，无需预加载。</p>
   */
  async function preloadIfNeeded() {
    if (!isPagedMode.value) return
    if (!hasMore.value) return
    if (currentIndex.value < questions.value.length - PRELOAD_THRESHOLD) return
    if (preloading.value || loading.value) return // 防并发 / 防与首屏加载打架

    preloading.value = true
    try {
      const nextPage = pageNum.value + 1
      let page: PageResult<QuestionVO>
      if (mode.value === 'CHAPTER') {
        if (knowledgePointId.value == null) return
        page = await getChapterQuestionsApi(knowledgePointId.value, nextPage)
      } else {
        if (!subjectCode.value) return
        page = await getSequenceQuestionsApi(subjectCode.value, nextPage)
      }

      // 去重：分页期间题库若有增删，后端可能返回已加载过的题
      const existing = new Set(questions.value.map((q) => q.id))
      const fresh = page.records.filter((q) => !existing.has(q.id))
      if (fresh.length > 0) {
        questions.value = [...questions.value, ...fresh]
      }

      pageNum.value = page.pageNum
      hasMore.value = page.pageNum < page.pages
    } finally {
      preloading.value = false
    }
  }

  /**
   * 提交作答：**按 mode 自动分发**到练习接口或重练接口（UI 不需要关心）。
   *
   * <p>只做判分与记录，<b>不自动翻页</b>——是否前进由 UI 决定
   * （答对 600ms 后调 {@link next}，答错停留）。</p>
   *
   * @returns 判分结果；若正在提交中则返回 null
   */
  async function submit(questionId: number, userAnswer: string): Promise<AnswerResultVO | null> {
    if (submitting.value) return null

    submitting.value = true
    try {
      const result =
        mode.value === 'RETRY'
          ? await submitRetryAnswerApi({ questionId, userAnswer })
          : await submitAnswerApi({ questionId, userAnswer })

      // 保留用户的选择（用于回显选项），并记录判分结果
      selections.value.set(questionId, userAnswer)
      results.value.set(questionId, result)
      return result
    } finally {
      submitting.value = false
    }
  }

  /**
   * 切换多选的某个选项（**不提交**）。
   *
   * <p>结果按字母升序拼接保存（如 "AB"），空选时移除该键。供 UI 通过 action 修改 state，
   * 组件不直接操作 {@code selections}。</p>
   */
  function toggleSelection(questionId: number, optionKey: string) {
    const current = selections.value.get(questionId) ?? ''
    const set = new Set(current.split('').filter(Boolean))
    if (set.has(optionKey)) {
      set.delete(optionKey)
    } else {
      set.add(optionKey)
    }
    const joined = [...set].sort().join('')
    if (joined) {
      selections.value.set(questionId, joined)
    } else {
      selections.value.delete(questionId)
    }
  }

  /**
   * 下一题。
   *
   * <p><b>未作答（{@code results} 里没有当前题）时不允许前进</b>——"未作答不能到下一题"这条规则
   * 在这里统一兜底，键盘翻页也就自动受约束。</p>
   */
  function next() {
    if (!hasAnsweredCurrent.value) return
    if (currentIndex.value < questions.value.length - 1) {
      currentIndex.value += 1
      // 顺带触发，避免调用方遗漏；内部有 loading/preloading 双重去重，重复调用无害
      void preloadIfNeeded().catch(() => {
        /* 预加载失败静默处理：错误提示已由 axios 拦截器统一弹出 */
      })
    }
  }

  /** 上一题 */
  function prev() {
    if (currentIndex.value > 0) {
      currentIndex.value -= 1
    }
  }

  return {
    // state
    questions,
    currentIndex,
    selections,
    results,
    mode,
    subjectCode,
    knowledgePointId,
    totalQuestions,
    pageNum,
    hasMore,
    loading,
    preloading,
    submitting,
    // getters
    currentQuestion,
    total,
    progressText,
    canPrev,
    canNext,
    isPagedMode,
    currentSelection,
    currentResult,
    hasAnsweredCurrent,
    // actions
    resultOf,
    reset,
    loadQuestions,
    preloadIfNeeded,
    toggleSelection,
    submit,
    next,
    prev,
  }
})
