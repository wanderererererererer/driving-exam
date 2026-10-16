<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PracticeToolbar from '@/components/study/PracticeToolbar.vue'
import QuestionCard from '@/components/study/QuestionCard.vue'
import QuestionFooter from '@/components/study/QuestionFooter.vue'
import { usePracticeStore } from '@/store/practice'
import type { PracticeMode } from '@/types'

/**
 * 刷题页（顺序 / 章节 / 随机 / 重练，靠路由 query 的 mode 区分）。
 *
 * <p>本页是**容器**：解析路由参数 → 调 store → 编排子组件。</p>
 */
const route = useRoute()
const router = useRouter()
const store = usePracticeStore()

const VALID_SUBJECTS = ['SUBJECT_1', 'SUBJECT_4']
const VALID_MODES: PracticeMode[] = ['SEQUENCE', 'CHAPTER', 'RANDOM', 'RETRY']

/** 解析路由 query（非法 mode 回落到 SEQUENCE） */
const parsed = computed(() => {
  const subjectCode = String(route.query.subjectCode ?? '')
  const modeRaw = String(route.query.mode ?? 'SEQUENCE').toUpperCase()
  const mode: PracticeMode = VALID_MODES.includes(modeRaw as PracticeMode)
    ? (modeRaw as PracticeMode)
    : 'SEQUENCE'

  const kpRaw = route.query.knowledgePointId
  const knowledgePointId =
    kpRaw !== undefined && kpRaw !== null && String(kpRaw) !== '' ? Number(kpRaw) : null

  return { subjectCode, mode, knowledgePointId }
})

/** 章节模式却没带知识点 —— 空状态（防御性；正常从弹窗进来必然带 id） */
const missingChapter = computed(
  () => parsed.value.mode === 'CHAPTER' && parsed.value.knowledgePointId == null,
)

const showEmpty = computed(
  () => !store.loading && store.questions.length === 0,
)

/** 加载本轮题目（切科目 / 切模式 / 换章节都会走这里） */
async function load() {
  const { subjectCode, mode, knowledgePointId } = parsed.value

  if (!VALID_SUBJECTS.includes(subjectCode)) {
    ElMessage.error('科目不存在')
    router.replace('/student/dashboard')
    return
  }

  store.reset()
  if (mode === 'CHAPTER' && knowledgePointId == null) {
    return // 空状态，不发请求
  }
  await store.loadQuestions({ subjectCode, mode, knowledgePointId })
}

onMounted(() => {
  void load()
})

// 路由 query 变化（用户前进后退 / 手改 URL / 从科目首页再进）时重新加载。
// 注意：离开本页时 query 会被清空，此时 subjectCode 为空 —— 直接跳过，
// 否则会误触发"科目不存在"的提示与跳转。
watch(
  () => [parsed.value.subjectCode, parsed.value.mode, parsed.value.knowledgePointId] as const,
  () => {
    if (!parsed.value.subjectCode) return
    void load()
  },
)

/* --------------------------- 键盘翻页（挂 window） --------------------------- */
function onKeydown(e: KeyboardEvent) {
  const t = e.target as HTMLElement | null
  // 排除输入框聚焦时的误触发（el-input / textarea / 富文本）
  if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable)) return

  if (e.key === 'ArrowLeft') {
    store.prev()
  } else if (e.key === 'ArrowRight') {
    store.next()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

/* ------------------------ 触摸左右滑（绑题目区容器） ------------------------ */
const SWIPE_MIN_X = 50
let touchStartX = 0
let touchStartY = 0

function onTouchStart(e: TouchEvent) {
  const t = e.changedTouches[0]
  touchStartX = t.clientX
  touchStartY = t.clientY
}

function onTouchEnd(e: TouchEvent) {
  const t = e.changedTouches[0]
  const dx = t.clientX - touchStartX
  const dy = t.clientY - touchStartY
  // 横向位移足够大、且明显大于纵向（否则视为纵向滚动）
  if (Math.abs(dx) > SWIPE_MIN_X && Math.abs(dx) > Math.abs(dy)) {
    if (dx < 0) {
      store.next() // 左滑 → 下一题
    } else {
      store.prev() // 右滑 → 上一题
    }
  }
}

/* ------------------------------- 预加载 ------------------------------- */
// currentIndex 变化时触发一次（内部已做"只对分页模式 + 防并发 + 到阈值才拉"的判断）
watch(
  () => store.currentIndex,
  () => {
    void store.preloadIfNeeded().catch(() => {
      /* 预加载失败静默：错误提示已由 axios 拦截器统一弹出 */
    })
  },
)

/* ------------------------------- 返回 ------------------------------- */
function goBack() {
  const sc = parsed.value.subjectCode
  router.push(VALID_SUBJECTS.includes(sc) ? `/student/subject/${sc}` : '/student/dashboard')
}
</script>

<template>
  <div class="sv">
    <PracticeToolbar
      :mode="store.mode"
      :current-index="store.currentIndex"
      :total-questions="store.totalQuestions"
      @back="goBack"
    />

    <!-- 触摸滑动绑在这块区域上，不绑 window -->
    <div class="sv__stage" @touchstart="onTouchStart" @touchend="onTouchEnd">
      <el-skeleton v-if="store.loading" :rows="6" animated />

      <el-empty v-else-if="missingChapter" description="未选择章节" />

      <el-empty v-else-if="showEmpty" description="暂无题目" />

      <template v-else>
        <!-- :key 让换题时组件重建，内部计时器/局部状态自然清零 -->
        <QuestionCard :key="store.currentQuestion?.id" />
        <QuestionFooter />
      </template>
    </div>
  </div>
</template>

<style scoped>
.sv__stage {
  min-height: 320px;
}
</style>
