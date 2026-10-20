<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getAnalysisOverviewApi, getKnowledgeStatsApi, getTrendApi } from '@/api/analysis'
import type { AnalysisOverviewVO, KPStatVO, TrendPointVO, WeakReason } from '@/types'
import MasteryBarChart from '@/components/analysis/MasteryBarChart.vue'
import TrendLineChart from '@/components/analysis/TrendLineChart.vue'

/**
 * 学情分析**内容面板**（可复用：路由页 + 首页抽屉）。
 *
 * <p>只负责内容——时间范围选择 + 概览卡片 + 掌握度柱状图 + 趋势折线图 + 短板表格。
 * <b>不含标题、不含「← 返回」</b>：那属于宿主外壳（路由页自带标题；抽屉由 {@code el-drawer}
 * 的 title 提供），放进本组件会导致抽屉里出现"双标题"。</p>
 *
 * <p>因此本组件<b>不依赖 useRoute</b>，科目一律由 {@code subjectCode} prop 传入。</p>
 *
 * <p><b>关于 days 重置</b>：抽屉用 {@code v-if} 挂载本组件，每次打开都会**重建**，
 * 于是时间范围回到默认 14 天、数据重新拉取——这是**预期行为**，不需要把 days 提升到外层。</p>
 */
const props = defineProps<{ subjectCode: string }>()

/** 时间范围选项（仅影响 trend） */
const DAY_OPTIONS = [7, 14, 30]

/** 短板原因中文映射（后端只给 code） */
const WEAK_REASON_TEXT: Record<WeakReason, string> = {
  LOW_MASTERY: '掌握度偏低',
  BASE_WEAK: '基础不牢',
}

const days = ref(14)

const overview = ref<AnalysisOverviewVO | null>(null)
const kpStats = ref<KPStatVO[]>([])
const trend = ref<TrendPointVO[]>([])
const loading = ref(false)

/** 比率展示：null → "-"（区分"未开始"与"0%"） */
function pct(v: number | null | undefined): string {
  return v == null ? '-' : `${Math.round(v * 100)}%`
}

/** 短板原因 → 中文（表格里 row 为 any，故走带类型的函数而非直接索引 Map） */
function weakReasonText(reason: WeakReason | null | undefined): string {
  return reason ? WEAK_REASON_TEXT[reason] : '-'
}

const weakList = computed(() => kpStats.value.filter((k) => k.isWeak))

/** 三接口并行请求（统一 loading） */
async function loadAll(): Promise<void> {
  if (!props.subjectCode) return
  loading.value = true
  try {
    const [o, k, t] = await Promise.all([
      getAnalysisOverviewApi(props.subjectCode),
      getKnowledgeStatsApi(props.subjectCode),
      getTrendApi(props.subjectCode, days.value),
    ])
    overview.value = o
    kpStats.value = k
    trend.value = t
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 仅重新请求趋势（overview / knowledge 不依赖 days） */
async function loadTrend(): Promise<void> {
  if (!props.subjectCode) return
  try {
    trend.value = await getTrendApi(props.subjectCode, days.value)
  } catch {
    // 拦截器已提示
  }
}

// 科目变化时整体重载（抽屉里 subjectCode 固定；路由页由 query 决定）
watch(() => props.subjectCode, () => void loadAll())
watch(days, () => void loadTrend())
onMounted(() => void loadAll())
</script>

<template>
  <div v-loading="loading" class="ap">
    <div class="ap__toolbar">
      <span class="ap__toolbar-label">时间范围</span>
      <el-radio-group v-model="days" size="small">
        <el-radio-button v-for="d in DAY_OPTIONS" :key="d" :value="d">{{ d }} 天</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 概览卡片 -->
    <div class="ap__cards">
      <el-card class="ap__stat" shadow="never">
        <div class="ap__stat-value">{{ overview?.totalQuestions ?? '-' }}</div>
        <div class="ap__stat-label">题库总题数</div>
      </el-card>
      <el-card class="ap__stat" shadow="never">
        <div class="ap__stat-value">{{ overview?.answeredQuestions ?? '-' }}</div>
        <div class="ap__stat-label">已答题数</div>
      </el-card>
      <el-card class="ap__stat" shadow="never">
        <div class="ap__stat-value">{{ pct(overview?.correctRate) }}</div>
        <div class="ap__stat-label">总体正确率</div>
      </el-card>
      <el-card class="ap__stat" shadow="never">
        <div class="ap__stat-value">{{ pct(overview?.progress) }}</div>
        <div class="ap__stat-label">练习进度</div>
      </el-card>
      <el-card class="ap__stat" shadow="never">
        <div class="ap__stat-value ap__stat-value--warn">{{ overview?.weakCount ?? '-' }}</div>
        <div class="ap__stat-label">短板知识点</div>
      </el-card>
    </div>

    <!-- 知识点掌握度 -->
    <el-card class="ap__block" shadow="never">
      <template #header><span>知识点掌握度</span></template>
      <MasteryBarChart v-if="kpStats.length > 0" :data="kpStats" />
      <el-empty v-else description="暂无数据（先去刷题吧）" :image-size="80" />
    </el-card>

    <!-- 正确率趋势 -->
    <el-card class="ap__block" shadow="never">
      <template #header><span>正确率趋势（近 {{ days }} 天）</span></template>
      <TrendLineChart v-if="trend.length > 0" :data="trend" />
      <el-empty v-else description="暂无趋势数据" :image-size="80" />
    </el-card>

    <!-- 短板明细 -->
    <el-card class="ap__block" shadow="never">
      <template #header><span>短板知识点</span></template>
      <el-table v-if="weakList.length > 0" :data="weakList" size="small" border>
        <el-table-column prop="knowledgePointName" label="知识点" min-width="140" />
        <el-table-column label="掌握度" width="90">
          <template #default="{ row }">
            <span class="ap__warn-text">{{ pct(row.mastery) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="answeredCount" label="答题数" width="80" />
        <el-table-column label="正确率" width="90">
          <template #default="{ row }">{{ pct(row.correctRate) }}</template>
        </el-table-column>
        <el-table-column label="短板原因" width="110">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ weakReasonText(row.weakReason) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无短板知识点" :image-size="80" />
    </el-card>
  </div>
</template>

<style scoped>
.ap__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.ap__toolbar-label {
  font-size: 13px;
  color: #687589;
}
.ap__cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}
.ap__stat {
  text-align: center;
}
.ap__stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #7597d5;
}
.ap__stat-value--warn {
  color: #c96a6a;
}
.ap__stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: #687589;
}
.ap__block {
  margin-top: 16px;
}
.ap__warn-text {
  color: #c96a6a;
  font-weight: 600;
}
</style>
