<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMeApi, type UserInfoVO } from '@/api/auth'
import { getAnalysisOverviewApi } from '@/api/analysis'
import { getWrongBookSummaryApi } from '@/api/wrongBook'
import { getMyAppointmentsApi } from '@/api/appointment'
import { listChatSessionsApi } from '@/api/chat'
import { useUserStore } from '@/store/user'
import type {
  AnalysisOverviewVO,
  AppointmentStatus,
  AppointmentVO,
  WrongBookSummaryVO,
} from '@/types'

/**
 * 学员端首页（真实数据）。
 *
 * <p>数据来源全部为现有只读接口，不使用演示数字：
 * <ul>
 *   <li>账号信息：GET /api/auth/me</li>
 *   <li>科目一/四学习进度：GET /api/student/analysis/overview</li>
 *   <li>错题概况：GET /api/student/wrong-book/summary（不传科目 = 全部科目）</li>
 *   <li>预约概况：GET /api/student/appointment</li>
 *   <li>未读消息：GET /api/student/chat/sessions</li>
 * </ul>
 * 各数据块独立加载，单个接口失败不影响其它区块展示。</p>
 */
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)

const me = ref<UserInfoVO | null>(null)
const profileError = ref(false)

const overview1 = ref<AnalysisOverviewVO | null>(null)
const overview4 = ref<AnalysisOverviewVO | null>(null)
const theory1Error = ref(false)
const theory4Error = ref(false)

const wrongSummary = ref<WrongBookSummaryVO | null>(null)
const wrongBookError = ref(false)

const pendingAppointmentTotal = ref<number | null>(null)
const pendingAppointmentError = ref(false)
const recentAppointments = ref<AppointmentVO[]>([])
const appointmentTotal = ref<number | null>(null)
const appointmentListError = ref(false)

const unreadCount = ref<number | null>(null)
const chatError = ref(false)

const displayName = computed(
  () =>
    me.value?.realName ||
    me.value?.username ||
    userStore.userInfo?.realName ||
    userStore.userInfo?.username ||
    '学员',
)

const username = computed(() => me.value?.username || userStore.userInfo?.username || '—')

function go(path: string): void {
  void router.push(path)
}

function percent(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return `${Math.round(value * 100)}%`
}

function progressText(overview: AnalysisOverviewVO | null): string {
  if (!overview) return '—'
  if (overview.totalQuestions === 0) return '暂无题目'
  return percent(overview.progress)
}

function progressPercent(overview: AnalysisOverviewVO): number {
  return Math.max(0, Math.min(100, Math.round(overview.progress * 100)))
}

function theoryHint(overview: AnalysisOverviewVO | null, error: boolean): string {
  if (error) return '接口加载失败，请刷新重试'
  if (!overview) return loading.value ? '加载中…' : '暂无数据'
  if (overview.totalQuestions === 0) return '该科目暂无启用题目'
  return `已答 ${overview.answeredQuestions} / ${overview.totalQuestions} 题 · 正确率 ${percent(
    overview.correctRate,
  )}`
}

const metricCards = computed(() => [
  {
    label: '科目一练习进度',
    value: progressText(overview1.value),
    hint: theoryHint(overview1.value, theory1Error.value),
    small: overview1.value?.totalQuestions === 0 || theory1Error.value,
  },
  {
    label: '科目四练习进度',
    value: progressText(overview4.value),
    hint: theoryHint(overview4.value, theory4Error.value),
    small: overview4.value?.totalQuestions === 0 || theory4Error.value,
  },
  {
    label: '待攻克错题',
    value: wrongBookError.value
      ? '加载失败'
      : wrongSummary.value
        ? String(wrongSummary.value.pending)
        : '—',
    hint: wrongBookError.value
      ? '错题统计接口加载失败'
      : wrongSummary.value
        ? `全部错题 ${wrongSummary.value.total} 条 · 已掌握 ${wrongSummary.value.mastered} 条`
        : loading.value
          ? '加载中…'
          : '暂无数据',
    small: wrongBookError.value,
  },
  {
    label: '待确认预约',
    value: pendingAppointmentError.value
      ? '加载失败'
      : pendingAppointmentTotal.value !== null
        ? String(pendingAppointmentTotal.value)
        : '—',
    hint: pendingAppointmentError.value
      ? '预约接口加载失败'
      : pendingAppointmentTotal.value !== null
        ? `全部预约 ${appointmentTotal.value ?? '—'} 条`
        : loading.value
          ? '加载中…'
          : '暂无数据',
    small: pendingAppointmentError.value,
  },
])

const theoryRows = computed(() => [
  {
    code: 'SUBJECT_1',
    label: '科目一',
    overview: overview1.value,
    error: theory1Error.value,
  },
  {
    code: 'SUBJECT_4',
    label: '科目四',
    overview: overview4.value,
    error: theory4Error.value,
  },
])

/* -------------------------------- 预约状态 -------------------------------- */

type TagType = '' | 'success' | 'info' | 'warning' | 'danger'

const STATUS_META: Record<AppointmentStatus, { label: string; type: TagType }> = {
  PENDING: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: '' },
  CANCELLED: { label: '已取消', type: 'info' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
}

function statusMeta(status: AppointmentStatus) {
  return STATUS_META[status]
}

function appointmentSubjectLabel(code: string): string {
  if (code === 'SUBJECT_2') return '科目二'
  if (code === 'SUBJECT_3') return '科目三'
  return code
}

function appointmentPath(code: string): string {
  if (code === 'SUBJECT_2' || code === 'SUBJECT_3') {
    return `/student/appointment?subjectCode=${code}`
  }
  return '/student/appointment?subjectCode=SUBJECT_2'
}

/* ------------------------------- 数据加载 ------------------------------- */

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [meRes, s1Res, s4Res, wrongRes, pendingRes, recentRes, chatRes] =
      await Promise.allSettled([
        getMeApi(),
        getAnalysisOverviewApi('SUBJECT_1'),
        getAnalysisOverviewApi('SUBJECT_4'),
        getWrongBookSummaryApi(),
        getMyAppointmentsApi({ status: 'PENDING', pageNum: 1, pageSize: 1 }),
        getMyAppointmentsApi({ pageNum: 1, pageSize: 5 }),
        listChatSessionsApi('student'),
      ])

    if (meRes.status === 'fulfilled') {
      me.value = meRes.value
      profileError.value = false
      userStore.setUserInfo({
        userId: meRes.value.id,
        username: meRes.value.username,
        realName: meRes.value.realName,
        role: meRes.value.role,
      })
    } else {
      me.value = null
      profileError.value = true
    }

    if (s1Res.status === 'fulfilled') {
      overview1.value = s1Res.value
      theory1Error.value = false
    } else {
      overview1.value = null
      theory1Error.value = true
    }

    if (s4Res.status === 'fulfilled') {
      overview4.value = s4Res.value
      theory4Error.value = false
    } else {
      overview4.value = null
      theory4Error.value = true
    }

    if (wrongRes.status === 'fulfilled') {
      wrongSummary.value = wrongRes.value
      wrongBookError.value = false
    } else {
      wrongSummary.value = null
      wrongBookError.value = true
    }

    if (pendingRes.status === 'fulfilled') {
      pendingAppointmentTotal.value = pendingRes.value.total
      pendingAppointmentError.value = false
    } else {
      pendingAppointmentTotal.value = null
      pendingAppointmentError.value = true
    }

    if (recentRes.status === 'fulfilled') {
      recentAppointments.value = recentRes.value.records
      appointmentTotal.value = recentRes.value.total
      appointmentListError.value = false
    } else {
      recentAppointments.value = []
      appointmentTotal.value = null
      appointmentListError.value = true
    }

    if (chatRes.status === 'fulfilled') {
      unreadCount.value = chatRes.value.reduce(
        (sum, session) => sum + Math.max(0, session.unreadCount || 0),
        0,
      )
      chatError.value = false
    } else {
      unreadCount.value = null
      chatError.value = true
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div v-loading="loading" class="sdash">
    <div class="sdash__head">
      <div>
        <h2 class="sdash__title">学员工作台</h2>
        <div class="sdash__desc">
          数据来自学情分析、错题本、预约与聊天接口，不使用演示数字。
        </div>
      </div>
      <el-button :loading="loading" @click="loadAll">刷新数据</el-button>
    </div>

    <el-alert
      v-if="profileError"
      class="sdash__alert"
      type="warning"
      show-icon
      :closable="false"
      title="账号信息加载失败，当前显示本地登录信息"
      description="其它区块仍会独立加载；可点击“刷新数据”重试。"
    />

    <el-card shadow="never" class="sdash__welcome">
      <div class="sdash__welcome-row">
        <div>
          <div class="sdash__hello">你好，{{ displayName }}</div>
          <div class="sdash__meta">
            <span>用户名：{{ username }}</span>
            <el-divider direction="vertical" />
            <span>角色：学员</span>
            <el-divider direction="vertical" />
            <span>手机号：{{ me?.phone || '未填写' }}</span>
          </div>
        </div>
        <div class="sdash__welcome-actions">
          <el-tag
            v-if="unreadCount !== null"
            :type="unreadCount > 0 ? 'danger' : 'info'"
            effect="plain"
          >
            未读消息 {{ unreadCount }}
          </el-tag>
          <el-button plain @click="go('/student/profile')">个人中心</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="sdash__cards">
      <el-col v-for="item in metricCards" :key="item.label" :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover">
          <div class="sdash__label">{{ item.label }}</div>
          <div class="sdash__value" :class="{ 'sdash__value--small': item.small }">
            {{ item.value }}
          </div>
          <div class="sdash__hint">{{ item.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="sdash__row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" class="sdash__card">
          <template #header>
            <div class="sdash__card-head">
              <span class="sdash__card-title">理论学习进度</span>
              <span class="sdash__muted">
                GET /api/student/analysis/overview
              </span>
            </div>
          </template>

          <div v-for="row in theoryRows" :key="row.code" class="sdash__subject">
            <div class="sdash__subject-head">
              <span class="sdash__subject-name">{{ row.label }}</span>
              <span class="sdash__subject-percent">{{ progressText(row.overview) }}</span>
            </div>

            <el-alert
              v-if="row.error"
              type="warning"
              show-icon
              :closable="false"
              :title="`${row.label}学习数据加载失败`"
            />

            <template v-else-if="row.overview">
              <el-progress
                :percentage="progressPercent(row.overview)"
                :stroke-width="8"
                :show-text="false"
              />
              <div class="sdash__subject-meta">
                <span>
                  已答 {{ row.overview.answeredQuestions }} / {{ row.overview.totalQuestions }} 题
                </span>
                <span>正确率：{{ percent(row.overview.correctRate) }}</span>
                <span>薄弱知识点：{{ row.overview.weakCount }}</span>
              </div>
            </template>

            <el-empty v-else-if="!loading" description="暂无学习数据" :image-size="52" />

            <div class="sdash__subject-actions">
              <el-button link type="primary" @click="go(`/student/subject/${row.code}`)">
                进入学习
              </el-button>
              <el-button
                link
                type="primary"
                @click="go(`/student/analysis?subjectCode=${row.code}`)"
              >
                学情分析
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card shadow="never" class="sdash__card">
          <template #header>
            <div class="sdash__card-head">
              <span class="sdash__card-title">快捷入口</span>
            </div>
          </template>

          <div class="sdash__links">
            <el-button @click="go('/student/subject/SUBJECT_1')">科目一刷题</el-button>
            <el-button @click="go('/student/subject/SUBJECT_4')">科目四刷题</el-button>
            <el-button @click="go('/student/wrong-book?subjectCode=SUBJECT_1')">
              错题本
            </el-button>
            <el-button @click="go('/student/ai')">智能问答</el-button>
            <el-button @click="go('/student/exam?subjectCode=SUBJECT_1')">
              科目一模考
            </el-button>
            <el-button @click="go('/student/exam-records?subjectCode=SUBJECT_1')">
              考试记录
            </el-button>
            <el-button @click="go('/student/appointment?subjectCode=SUBJECT_2')">
              科目二预约
            </el-button>
            <el-button @click="go('/student/appointment?subjectCode=SUBJECT_3')">
              科目三预约
            </el-button>
            <el-badge :value="unreadCount ?? 0" :hidden="!unreadCount" class="sdash__badge">
              <el-button @click="go('/student/chat')">教练聊天</el-button>
            </el-badge>
          </div>

          <div v-if="chatError" class="sdash__muted sdash__links-note">
            未读消息加载失败，不影响进入聊天页面。
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="sdash__card sdash__row">
      <template #header>
        <div class="sdash__card-head">
          <span class="sdash__card-title">最近预约</span>
          <span class="sdash__muted">
            待确认 {{ pendingAppointmentTotal ?? '—' }} 条 · 全部 {{ appointmentTotal ?? '—' }} 条
          </span>
        </div>
      </template>

      <el-alert
        v-if="appointmentListError"
        class="sdash__alert"
        type="warning"
        show-icon
        :closable="false"
        title="预约列表加载失败"
      />

      <el-table
        :data="recentAppointments"
        size="small"
        empty-text="暂无预约记录，可从上方快捷入口发起科目二/三预约"
      >
        <el-table-column label="科目" width="90">
          <template #default="{ row }">{{ appointmentSubjectLabel(row.subjectCode) }}</template>
        </el-table-column>
        <el-table-column prop="appointmentDate" label="预约日期" width="120" />
        <el-table-column prop="timeSlot" label="时段" width="130" />
        <el-table-column label="教练">
          <template #default="{ row }">{{ row.coachName || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">
              {{ statusMeta(row.status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="提交时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link type="primary" @click="go(appointmentPath(row.subjectCode))">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.sdash__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.sdash__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.sdash__desc {
  margin-top: 6px;
  font-size: 13px;
  color: #687589;
}
.sdash__alert {
  margin-bottom: 16px;
}
.sdash__welcome {
  margin-bottom: 16px;
}
.sdash__welcome-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.sdash__hello {
  font-size: 20px;
  font-weight: 600;
  color: #263449;
}
.sdash__meta {
  margin-top: 8px;
  font-size: 13px;
  color: #687589;
}
.sdash__welcome-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sdash__cards {
  margin-bottom: 16px;
}
.sdash__label {
  font-size: 13px;
  color: #687589;
}
.sdash__value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 600;
  color: #263449;
}
.sdash__value--small {
  font-size: 18px;
}
.sdash__hint {
  margin-top: 6px;
  min-height: 18px;
  font-size: 12px;
  color: #a3adbb;
}
.sdash__row {
  margin-bottom: 16px;
}
.sdash__card {
  margin-bottom: 16px;
}
.sdash__card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.sdash__card-title {
  font-weight: 600;
}
.sdash__muted {
  font-size: 12px;
  color: #687589;
}
.sdash__subject + .sdash__subject {
  margin-top: 22px;
  padding-top: 22px;
  border-top: 1px dashed #dce3ec;
}
.sdash__subject-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sdash__subject-name {
  font-size: 15px;
  font-weight: 600;
  color: #263449;
}
.sdash__subject-percent {
  font-size: 18px;
  font-weight: 600;
  color: #7597d5;
}
.sdash__subject-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin-top: 10px;
  font-size: 13px;
  color: #4a5a70;
}
.sdash__subject-actions {
  margin-top: 8px;
}
.sdash__links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.sdash__links .el-button {
  margin-left: 0;
}
.sdash__links-note {
  margin-top: 12px;
}
.sdash__badge {
  display: inline-flex;
}
</style>
