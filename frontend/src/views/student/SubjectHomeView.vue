<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, Collection, Document, EditPen, Refresh, TrendCharts, Warning } from '@element-plus/icons-vue'
import type { PracticeMode } from '@/types'
import ChapterPickerDialog from '@/components/study/ChapterPickerDialog.vue'

/**
 * 学情分析面板**异步加载**。
 *
 * <p>该面板依赖 echarts（约 1.1 MB），若静态引入会被打进本页 chunk，
 * 导致**每次进科目首页都要下载它**（哪怕不点悬浮按钮）。改成异步组件后，
 * 只有抽屉真正打开（`v-if` 渲染）时才去加载那块 chunk。</p>
 */
const AnalysisPanel = defineAsyncComponent(() => import('@/components/analysis/AnalysisPanel.vue'))

/**
 * 科目首页：科目的 4 个练习入口（2×2 居中）+ 右下角「学情分析」悬浮按钮。
 *
 * <p>路由 `/student/subject/:subjectCode`，从**路由参数**（不是 query）读科目编码。</p>
 *
 * <p>不放任何统计/进度——这里只是分流入口。学情分析改用悬浮抽屉承载，
 * 内容复用 {@link AnalysisPanel}（与 /student/analysis 路由页同一组件）。</p>
 */
const route = useRoute()
const router = useRouter()

const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_4: '科目四',
}

const subjectCode = computed(() => String(route.params.subjectCode ?? ''))
const subjectName = computed(() => SUBJECT_NAMES[subjectCode.value] ?? '未知科目')

onMounted(() => {
  if (!(subjectCode.value in SUBJECT_NAMES)) {
    ElMessage.error('科目不存在')
    router.replace('/student/dashboard')
  }
})

/** 进入刷题页 */
function goStudy(mode: PracticeMode) {
  router.push({
    path: '/student/study',
    query: { subjectCode: subjectCode.value, mode },
  })
}

/** 进入错题本 */
function goWrongBook() {
  router.push({
    path: '/student/wrong-book',
    query: { subjectCode: subjectCode.value },
  })
}

/** 进入模拟考试（带本科目上下文：科一 → SUBJECT_1，科四 → SUBJECT_4） */
function goExam() {
  router.push({
    path: '/student/exam',
    query: { subjectCode: subjectCode.value },
  })
}

/** 进入考试记录（带本科目上下文，只展示本科目记录） */
function goExamRecords() {
  router.push({
    path: '/student/exam-records',
    query: { subjectCode: subjectCode.value },
  })
}

/** 章节选择弹窗显隐 */
const chapterPickerVisible = ref(false)
/** 选中的知识点，暂存到弹窗关闭动画播完再跳转 */
const pendingKnowledgePointId = ref<number | null>(null)

/** 章节练习：打开章节选择弹窗 */
function openChapterPicker() {
  chapterPickerVisible.value = true
}

/** 选中章节：只**记下**并关闭弹窗，跳转留到 @closed（动画结束后） */
function onChapterPicked(knowledgePointId: number) {
  pendingKnowledgePointId.value = knowledgePointId
  chapterPickerVisible.value = false
}

/**
 * 弹窗关闭动画结束：此时再跳转，避免"关闭动画还没播完，新页面已经在渲染"的重叠。
 * 若没选任何节点就关掉了（取消 / ×），pendingKnowledgePointId 为 null，不跳转。
 */
function onPickerClosed() {
  const id = pendingKnowledgePointId.value
  pendingKnowledgePointId.value = null
  if (id == null) return

  router.push({
    path: '/student/study',
    query: {
      subjectCode: subjectCode.value,
      mode: 'CHAPTER',
      knowledgePointId: String(id),
    },
  })
}

/** 学情分析抽屉显隐 */
const drawerVisible = ref(false)

/** 4 个练习入口（`key` 同时用作配色 modifier class） */
const entries = computed(() => [
  {
    key: 'SEQUENCE',
    title: '顺序练习',
    desc: '按题号顺序，一题一题过',
    icon: EditPen,
    onClick: () => goStudy('SEQUENCE'),
  },
  {
    key: 'RANDOM',
    title: '随机练习',
    desc: '随机抽题，检验真实水平',
    icon: Refresh,
    onClick: () => goStudy('RANDOM'),
  },
  {
    key: 'CHAPTER',
    title: '章节练习',
    desc: '按知识点分章，逐个攻克',
    icon: Collection,
    onClick: openChapterPicker,
  },
  {
    key: 'WRONG',
    title: '我的错题',
    desc: '待攻克 / 高频错 / 已攻克',
    icon: Warning,
    onClick: goWrongBook,
  },
])
</script>

<template>
  <div class="sh">
    <div class="sh__head">
      <el-button class="sh__back" text @click="router.push('/student/dashboard')">← 返回</el-button>
      <h2 class="sh__title">{{ subjectName }}</h2>
    </div>

    <div class="sh__body">
      <div class="sh__grid">
        <el-card
          v-for="e in entries"
          :key="e.key"
          class="sh__card"
          :class="`sh__card--${e.key.toLowerCase()}`"
          shadow="never"
          @click="e.onClick()"
        >
          <div class="sh__icon">
            <el-icon :size="26"><component :is="e.icon" /></el-icon>
          </div>
          <div class="sh__card-title">{{ e.title }}</div>
          <div class="sh__card-desc">{{ e.desc }}</div>
        </el-card>
      </div>

      <!-- 下方两个并排功能卡片：模拟考试 / 考试记录（均带本科目上下文） -->
      <div class="sh__more">
        <el-card class="sh__more-card sh__more-card--exam" shadow="never" @click="goExam">
          <div class="sh__more-icon">
            <el-icon :size="22"><Document /></el-icon>
          </div>
          <div class="sh__more-title">模拟考试</div>
          <div class="sh__more-desc">模拟真实考试，检验学习成果</div>
        </el-card>
        <el-card class="sh__more-card sh__more-card--records" shadow="never" @click="goExamRecords">
          <div class="sh__more-icon">
            <el-icon :size="22"><Clock /></el-icon>
          </div>
          <div class="sh__more-title">考试记录</div>
          <div class="sh__more-desc">查看历史成绩与考试情况</div>
        </el-card>
      </div>
    </div>

    <!-- 学情分析悬浮按钮（仅本科目首页出现；抽屉打开时隐藏） -->
    <el-tooltip content="查看学情分析" placement="left">
      <button
        v-show="!drawerVisible"
        class="sh__fab"
        type="button"
        aria-label="查看学情分析"
        @click="drawerVisible = true"
      >
        <el-icon :size="26"><TrendCharts /></el-icon>
      </button>
    </el-tooltip>

    <!-- 学情分析抽屉（v-if 让面板每次打开都重建：days 归 14、数据重新拉取） -->
    <el-drawer
      v-model="drawerVisible"
      :title="`${subjectName} · 学情分析`"
      direction="rtl"
      size="640px"
    >
      <AnalysisPanel v-if="drawerVisible" :subject-code="subjectCode" />
    </el-drawer>

    <ChapterPickerDialog
      v-model="chapterPickerVisible"
      :subject-code="subjectCode"
      @select="onChapterPicked"
      @closed="onPickerClosed"
    />
  </div>
</template>

<style scoped>
.sh {
  --color-analysis: #9c27b0; /* 学情分析语义色（悬浮按钮） */

  /*
   * 页头**置顶**，卡片区在剩余空间居中。
   * · min-height（非 height）：内容超高时容器随之增高，从顶部排列、不裁切。
   * · 减 100px = 60px(导航 el-header) + 40px(el-main 上下 padding 各 20px)。
   */
  box-sizing: border-box;
  min-height: calc(100vh - 100px);
  padding: 16px;
  display: flex;
  flex-direction: column;
}

/* ---------------- 卡片配色（预置浅色常量，不用 color-mix） ---------------- */
.sh__card--sequence {
  --card-color: #409eff;
  --card-bg: #ecf5ff;
  --card-icon-bg: #d9ecff;
}
.sh__card--random {
  --card-color: #67c23a;
  --card-bg: #f0f9eb;
  --card-icon-bg: #e1f3d8;
}
.sh__card--chapter {
  --card-color: #e6a23c;
  --card-bg: #fdf6ec;
  --card-icon-bg: #faecd8;
}
.sh__card--wrong {
  --card-color: #f56c6c;
  --card-bg: #fef0f0;
  --card-icon-bg: #fde2e2;
}

/* 页头置顶：三列网格（左右各 1fr + 中间 auto）→ 标题**真正居中**（flex 的 space-between 做不到） */
.sh__head {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  margin-bottom: 12px;
}
.sh__back {
  justify-self: start; /* 返回键贴左 */
}
.sh__title {
  justify-self: center; /* 标题居中 */
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

/* 内容区：占据页头之外的剩余高度，把「4 练习卡 + 2 功能卡」整体居中 */
.sh__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 20px;
}
/* 4 个练习卡片：2×2 居中 */
.sh__grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  max-width: 528px;
}
.sh__card {
  flex: 0 0 240px;
  max-width: 100%;
  cursor: pointer;
  text-align: center;
  background-color: var(--card-bg);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition:
    transform 0.25s,
    box-shadow 0.25s;
}
.sh__card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
}
.sh__card :deep(.el-card__body) {
  padding: 22px 12px;
}
.sh__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--card-icon-bg);
  color: var(--card-color);
}
.sh__card-title {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--card-color);
}
.sh__card-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

/* ---------------- 下方两个并排功能卡片（模拟考试 / 考试记录） ---------------- */
.sh__more {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
  width: 100%;
  max-width: 528px;
}
.sh__more-card--exam {
  --card-color: #409eff;
  --card-bg: #ecf5ff;
  --card-icon-bg: #d9ecff;
}
.sh__more-card--records {
  --card-color: #909399;
  --card-bg: #f4f4f5;
  --card-icon-bg: #e9e9eb;
}
.sh__more-card {
  flex: 0 0 240px;
  max-width: 100%;
  cursor: pointer;
  text-align: center;
  background-color: var(--card-bg);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition:
    transform 0.25s,
    box-shadow 0.25s;
}
.sh__more-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
}
.sh__more-card :deep(.el-card__body) {
  padding: 14px 12px;
}
.sh__more-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--card-icon-bg);
  color: var(--card-color);
}
.sh__more-title {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 600;
  color: var(--card-color);
}
.sh__more-desc {
  margin-top: 4px;
  font-size: 13px;
  color: #909399;
}

/* ---------------- 悬浮按钮 ---------------- */
.sh__fab {
  position: fixed;
  right: 40px;
  bottom: 40px;
  z-index: 1000; /* 低于 el-drawer 层级，抽屉打开时被遮罩盖住 */
  width: 56px;
  height: 56px;
  padding: 0;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #fff;
  background: var(--color-analysis);
  box-shadow: 0 6px 16px rgba(156, 39, 176, 0.35);
  transition:
    transform 0.25s,
    box-shadow 0.25s;
}
.sh__fab:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 22px rgba(156, 39, 176, 0.45);
}

/* 窄屏：按钮贴近边角并给页面底部留出空间，避免遮挡卡片 */
@media (max-width: 560px) {
  .sh__fab {
    right: 16px;
    bottom: 16px;
  }
  .sh {
    padding-bottom: 88px;
  }
}
</style>
