<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Calendar,
  ChatDotRound,
  Collection,
  Document,
  Link,
  Setting,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import {
  getAdminDashboardApi,
  listAdminAppointmentsApi,
  listAdminUsersApi,
  type AdminDashboardVO,
  type AdminRole,
} from '@/api/admin'
import type { AppointmentStatus, AppointmentVO } from '@/types'

/**
 * 管理员端 · 工作台。
 * 统计、用户构成、待处理预约和最近预约分别加载；失败不伪装为 0。
 */
const router = useRouter()

const loading = ref(false)

const summary = ref<AdminDashboardVO | null>(null)
const summaryError = ref(false)

const roleTotals = reactive<Record<AdminRole, number | null>>({
  STUDENT: null,
  COACH: null,
  ADMIN: null,
})
const roleError = ref(false)

const pendingTotal = ref<number | null>(null)
const pendingAppointments = ref<AppointmentVO[]>([])
const pendingError = ref(false)

const recentAppointments = ref<AppointmentVO[]>([])
const recentError = ref(false)

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

function metricValue(value: number | null | undefined, error: boolean): string {
  if (error) return '加载失败'
  return value === null || value === undefined ? '—' : String(value)
}

const userTotal = computed(() => summary.value?.userCount ?? null)

const metrics = computed(() => [
  {
    label: '用户总数',
    value: metricValue(summary.value?.userCount, summaryError.value),
    hint: summaryError.value ? '用户统计加载失败' : '全部账号',
    featured: true,
  },
  {
    label: '启用题目',
    value: metricValue(summary.value?.questionCount, summaryError.value),
    hint: summaryError.value ? '题目统计加载失败' : 'status=1',
  },
  {
    label: '预约总数',
    value: metricValue(summary.value?.appointmentCount, summaryError.value),
    hint: summaryError.value ? '预约统计加载失败' : '全部记录',
  },
  {
    label: 'AI 问答历史',
    value: metricValue(summary.value?.aiChatCount, summaryError.value),
    hint: summaryError.value ? 'AI 统计加载失败' : '记录数，不代表 RAG 在线',
  },
])

const roleRows = computed(() => {
  const total = userTotal.value
  return [
    { label: '学员', value: roleTotals.STUDENT },
    { label: '教练', value: roleTotals.COACH },
    { label: '管理员', value: roleTotals.ADMIN },
  ].map((row) => ({
    ...row,
    percent:
      total && total > 0 && row.value !== null
        ? Math.min(100, Math.round((row.value / total) * 100))
        : 0,
  }))
})

const quickLinks = [
  { label: '用户管理', desc: '账号与状态', path: '/admin/users', icon: User },
  { label: '绑定管理', desc: '师生关系', path: '/admin/bindings', icon: Link },
  { label: '知识点', desc: '章节与描述', path: '/admin/knowledge-points', icon: Collection },
  { label: '题库管理', desc: '题干与选项', path: '/admin/questions', icon: Document },
  { label: '试卷管理', desc: '组卷与状态', path: '/admin/papers', icon: Tickets },
  { label: '预约查看', desc: '全量记录', path: '/admin/appointments', icon: Calendar },
  { label: 'AI 问答统计', desc: '历史用量', path: '/admin/ai-stats', icon: ChatDotRound },
  { label: '个人中心', desc: '资料与密码', path: '/admin/profile', icon: Setting },
]

function go(path: string): void {
  void router.push(path)
}

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      getAdminDashboardApi(),
      listAdminUsersApi({ role: 'STUDENT', pageNum: 1, pageSize: 1 }),
      listAdminUsersApi({ role: 'COACH', pageNum: 1, pageSize: 1 }),
      listAdminUsersApi({ role: 'ADMIN', pageNum: 1, pageSize: 1 }),
      listAdminAppointmentsApi({ status: 'PENDING', pageNum: 1, pageSize: 5 }),
      listAdminAppointmentsApi({ pageNum: 1, pageSize: 5 }),
    ])
    const [summaryRes, studentRes, coachRes, adminRes, pendingRes, recentRes] = results

    if (summaryRes.status === 'fulfilled') {
      summary.value = summaryRes.value
      summaryError.value = false
    } else {
      summary.value = null
      summaryError.value = true
    }

    roleError.value = false
    roleTotals.STUDENT =
      studentRes.status === 'fulfilled' ? studentRes.value.total : null
    roleTotals.COACH = coachRes.status === 'fulfilled' ? coachRes.value.total : null
    roleTotals.ADMIN = adminRes.status === 'fulfilled' ? adminRes.value.total : null
    if (
      studentRes.status === 'rejected'
      || coachRes.status === 'rejected'
      || adminRes.status === 'rejected'
    ) {
      roleError.value = true
    }

    if (pendingRes.status === 'fulfilled') {
      pendingAppointments.value = pendingRes.value.records
      pendingTotal.value = pendingRes.value.total
      pendingError.value = false
    } else {
      pendingAppointments.value = []
      pendingTotal.value = null
      pendingError.value = true
    }

    if (recentRes.status === 'fulfilled') {
      recentAppointments.value = recentRes.value.records
      recentError.value = false
    } else {
      recentAppointments.value = []
      recentError.value = true
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
  <div v-loading="loading" class="adash">
    <div class="adash__head">
      <div>
        <h1 class="adash__title">管理端工作台</h1>
        <p class="adash__desc">平台数据概览与待办工作区，所有数字来自后端真实接口。</p>
      </div>
      <el-button :loading="loading" @click="loadAll">刷新数据</el-button>
    </div>

    <el-alert
      v-if="summaryError"
      class="adash__alert"
      type="warning"
      show-icon
      :closable="false"
      title="基础统计加载失败"
      description="用户、题目、预约和 AI 数量暂时不可用；其它区块仍会独立加载。"
    />

    <section class="adash__overview" aria-label="平台核心指标">
      <div
        v-for="item in metrics"
        :key="item.label"
        class="adash__metric"
        :class="{ 'adash__metric--featured': item.featured }"
      >
        <span class="adash__metric-label">{{ item.label }}</span>
        <strong class="adash__metric-value">{{ item.value }}</strong>
        <span class="adash__metric-hint">{{ item.hint }}</span>
      </div>
    </section>

    <div class="adash__grid">
      <div class="adash__primary">
        <el-card shadow="never" class="adash__card adash__card--urgent">
          <template #header>
            <div class="adash__card-head">
              <div class="adash__card-title-wrap">
                <span class="adash__eyebrow">优先处理</span>
                <span class="adash__card-title">待确认预约</span>
              </div>
              <div class="adash__card-head-right">
                <span class="adash__count">{{ pendingTotal ?? '—' }}</span>
                <el-button link type="primary" @click="go('/admin/appointments')">
                  查看全部
                </el-button>
              </div>
            </div>
          </template>

          <el-alert
            v-if="pendingError"
            type="warning"
            show-icon
            :closable="false"
            title="待处理预约加载失败"
            description="可前往预约查看页重试。"
          />

          <el-table
            v-else
            :data="pendingAppointments"
            size="small"
            empty-text="暂无待确认预约"
          >
            <el-table-column label="学员" min-width="120">
              <template #default="{ row }">{{ row.studentName || '—' }}</template>
            </el-table-column>
            <el-table-column label="科目" width="90">
              <template #default="{ row }">{{ appointmentSubjectLabel(row.subjectCode) }}</template>
            </el-table-column>
            <el-table-column label="预约时间" width="190">
              <template #default="{ row }">
                {{ row.appointmentDate }} {{ row.timeSlot }}
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="165" />
            <el-table-column label="操作" width="80">
              <template #default>
                <el-button link type="primary" @click="go('/admin/appointments')">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="adash__card">
          <template #header>
            <div class="adash__card-head">
              <div class="adash__card-title-wrap">
                <span class="adash__eyebrow">业务动态</span>
                <span class="adash__card-title">最近提交的预约</span>
              </div>
              <span class="adash__muted">最近 5 条</span>
            </div>
          </template>

          <el-alert
            v-if="recentError"
            type="warning"
            show-icon
            :closable="false"
            title="最近预约加载失败"
          />

          <el-table
            v-else
            :data="recentAppointments"
            size="small"
            empty-text="暂无预约记录"
          >
            <el-table-column label="学员" min-width="110">
              <template #default="{ row }">{{ row.studentName || '—' }}</template>
            </el-table-column>
            <el-table-column label="教练" min-width="110">
              <template #default="{ row }">{{ row.coachName || '—' }}</template>
            </el-table-column>
            <el-table-column label="科目" width="85">
              <template #default="{ row }">{{ appointmentSubjectLabel(row.subjectCode) }}</template>
            </el-table-column>
            <el-table-column label="预约时间" width="185">
              <template #default="{ row }">
                {{ row.appointmentDate }} {{ row.timeSlot }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="95">
              <template #default="{ row }">
                <el-tag :type="statusMeta(row.status).type" size="small">
                  {{ statusMeta(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="160" />
          </el-table>
        </el-card>
      </div>

      <aside class="adash__side">
        <el-card shadow="never" class="adash__card">
          <template #header>
            <div class="adash__card-head">
              <div class="adash__card-title-wrap">
                <span class="adash__eyebrow">账号结构</span>
                <span class="adash__card-title">用户构成</span>
              </div>
              <el-button link type="primary" @click="go('/admin/users')">
                管理
              </el-button>
            </div>
          </template>

          <el-alert
            v-if="roleError"
            type="warning"
            show-icon
            :closable="false"
            title="角色分布加载失败，已知数量仍显示"
          />

          <div class="adash__roles">
            <div v-for="row in roleRows" :key="row.label" class="adash__role">
              <div class="adash__role-top">
                <span>{{ row.label }}</span>
                <b>{{ row.value ?? '—' }}</b>
              </div>
              <el-progress
                :percentage="row.percent"
                :show-text="false"
                :stroke-width="6"
                color="#7b86c7"
              />
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="adash__card">
          <template #header>
            <div class="adash__card-head">
              <div class="adash__card-title-wrap">
                <span class="adash__eyebrow">管理功能</span>
                <span class="adash__card-title">快捷入口</span>
              </div>
            </div>
          </template>

          <div class="adash__actions">
            <button
              v-for="item in quickLinks"
              :key="item.path"
              type="button"
              class="adash__action"
              @click="go(item.path)"
            >
              <span class="adash__action-icon"><el-icon><component :is="item.icon" /></el-icon></span>
              <span class="adash__action-text">
                <span class="adash__action-label">{{ item.label }}</span>
                <span class="adash__action-desc">{{ item.desc }}</span>
              </span>
            </button>
          </div>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.adash {
  width: 100%;
}
.adash__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 22px;
}
.adash__title {
  margin: 0;
  color: var(--app-text);
  font-size: 26px;
  font-weight: 650;
  letter-spacing: -0.025em;
}
.adash__desc {
  margin: 7px 0 0;
  color: var(--app-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}
.adash__alert {
  margin-bottom: 18px;
}

.adash__overview {
  display: grid;
  grid-template-columns: 1.35fr repeat(3, minmax(0, 1fr));
  margin-bottom: 22px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-sm);
  overflow: hidden;
}
.adash__metric {
  min-height: 132px;
  padding: 22px 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-left: 1px solid var(--app-border-light);
}
.adash__metric:first-child {
  border-left: 0;
}
.adash__metric--featured {
  background: linear-gradient(135deg, #f5f8fe 0%, #ffffff 78%);
}
.adash__metric-label {
  color: var(--app-text-secondary);
  font-size: 13px;
  font-weight: 500;
}
.adash__metric-value {
  margin-top: 6px;
  color: var(--app-text);
  font-size: 34px;
  line-height: 1.1;
  font-weight: 650;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
}
.adash__metric--featured .adash__metric-value {
  color: var(--app-primary-strong);
  font-size: 40px;
}
.adash__metric-hint {
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.adash__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(290px, 0.8fr);
  gap: 18px;
  align-items: start;
}
.adash__primary,
.adash__side {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}
.adash__card {
  margin: 0;
}
.adash__card--urgent {
  border-top: 3px solid #d7a35d !important;
}
.adash__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.adash__card-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.adash__eyebrow {
  color: var(--app-text-secondary);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.08em;
}
.adash__card-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 600;
}
.adash__card-head-right {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.adash__count {
  color: var(--app-warning);
  font-size: 22px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.adash__muted {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.adash__roles {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.adash__role-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 7px;
  color: var(--app-text-regular);
  font-size: 13px;
}
.adash__role-top b {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}

.adash__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.adash__action {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: var(--app-surface-muted);
  color: var(--app-text-regular);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: all 0.18s ease;
}
.adash__action:hover {
  background: var(--app-primary-faint);
  border-color: var(--app-primary-soft);
  transform: translateY(-1px);
}
.adash__action-icon {
  width: 30px;
  height: 30px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: var(--app-surface);
  color: #7b86c7;
  font-size: 15px;
  box-shadow: var(--app-shadow-sm);
}
.adash__action-text {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.adash__action-label {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}
.adash__action-desc {
  color: var(--app-text-secondary);
  font-size: 11px;
  white-space: nowrap;
}

@media (max-width: 1180px) {
  .adash__overview {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .adash__metric:nth-child(3) {
    border-left: 0;
    border-top: 1px solid var(--app-border-light);
  }
  .adash__metric:nth-child(4) {
    border-top: 1px solid var(--app-border-light);
  }
  .adash__grid {
    grid-template-columns: 1fr;
  }
  .adash__side {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .adash__overview,
  .adash__side {
    grid-template-columns: 1fr;
  }
  .adash__metric {
    min-height: 100px;
    border-left: 0;
    border-top: 1px solid var(--app-border-light);
  }
  .adash__metric:first-child {
    border-top: 0;
  }
  .adash__head {
    flex-direction: column;
  }
}
</style>
