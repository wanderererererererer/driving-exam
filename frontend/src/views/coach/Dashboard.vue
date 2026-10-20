<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMeApi, type UserInfoVO } from '@/api/auth'
import { listMyStudentsApi } from '@/api/coachStudent'
import { listCoachAppointmentsApi } from '@/api/appointment'
import { listChatSessionsApi } from '@/api/chat'
import { useUserStore } from '@/store/user'
import type {
  AppointmentStatus,
  AppointmentVO,
  CoachConfirmStatus,
  StudentVO,
  SubjectCode,
  SubjectPhaseStatus,
} from '@/types'

/**
 * 教练端工作台（真实数据）。
 *
 * <p>数据来源全部为现有只读接口，不使用演示数字：
 * <ul>
 *   <li>账号信息：GET /api/auth/me</li>
 *   <li>绑定学员：GET /api/coach/students</li>
 *   <li>待处理/已确认预约：GET /api/coach/appointments（按 status 分次统计 total）</li>
 *   <li>未读消息：GET /api/coach/chat/sessions</li>
 * </ul>
 * 各数据块独立加载，单个接口失败不影响其它区块展示。</p>
 */
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)

const me = ref<UserInfoVO | null>(null)
const profileError = ref(false)

const studentsTotal = ref<number | null>(null)
const students = ref<StudentVO[]>([])
const studentsError = ref(false)

const pendingTotal = ref<number | null>(null)
const pendingAppointments = ref<AppointmentVO[]>([])
const pendingError = ref(false)

const confirmedTotal = ref<number | null>(null)
const confirmedError = ref(false)

const unreadCount = ref<number | null>(null)
const unreadSessionCount = ref(0)
const chatError = ref(false)

const displayName = computed(
  () =>
    me.value?.realName ||
    me.value?.username ||
    userStore.userInfo?.realName ||
    userStore.userInfo?.username ||
    '教练',
)

const username = computed(() => me.value?.username || userStore.userInfo?.username || '—')

function go(path: string): void {
  void router.push(path)
}

/* ------------------------------- 展示映射 ------------------------------- */

type TagType = '' | 'success' | 'info' | 'warning' | 'danger'

const SUBJECT_LABELS: Record<SubjectCode, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_2: '科目二',
  SUBJECT_3: '科目三',
  SUBJECT_4: '科目四',
}

const PHASE_LABELS: Record<SubjectPhaseStatus, string> = {
  NOT_STARTED: '未开始',
  LEARNING: '学习中',
  READY_FOR_EXAM: '已具备考试条件',
  EXAM_PASSED: '考试通过',
  EXAM_FAILED: '考试未通过',
}

const CONFIRM_META: Record<CoachConfirmStatus, { label: string; type: TagType }> = {
  CONFIRMED_READY: { label: '已确认可参加考试', type: 'success' },
  NOT_READY: { label: '暂不建议参加考试', type: 'warning' },
  NEED_HELP: { label: '需要进一步辅导', type: 'danger' },
}

const APPOINTMENT_STATUS_META: Record<
  AppointmentStatus,
  { label: string; type: TagType }
> = {
  PENDING: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: '' },
  CANCELLED: { label: '已取消', type: 'info' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
}

function subjectLabel(code: SubjectCode | null | undefined): string {
  if (!code) return '暂无进行中科目'
  return SUBJECT_LABELS[code] ?? code
}

function phaseLabel(status: SubjectPhaseStatus | null | undefined): string {
  if (!status) return '—'
  return PHASE_LABELS[status] ?? status
}

function confirmMeta(status: CoachConfirmStatus | null): { label: string; type: TagType } {
  return status ? CONFIRM_META[status] : { label: '待确认', type: 'info' }
}

function appointmentSubjectLabel(code: string): string {
  if (code === 'SUBJECT_2') return '科目二'
  if (code === 'SUBJECT_3') return '科目三'
  return code
}

function appointmentStatusMeta(status: AppointmentStatus) {
  return APPOINTMENT_STATUS_META[status]
}

const metricCards = computed(() => [
  {
    label: '绑定学员',
    value: studentsError.value
      ? '加载失败'
      : studentsTotal.value !== null
        ? String(studentsTotal.value)
        : '—',
    hint: studentsError.value
      ? '学员接口加载失败'
      : studentsTotal.value !== null
        ? '当前有效绑定学员'
        : loading.value
          ? '加载中…'
          : '暂无数据',
    small: studentsError.value,
  },
  {
    label: '待处理预约',
    value: pendingError.value
      ? '加载失败'
      : pendingTotal.value !== null
        ? String(pendingTotal.value)
        : '—',
    hint: pendingError.value
      ? '预约接口加载失败'
      : pendingTotal.value !== null
        ? '等待教练审批的预约'
        : loading.value
          ? '加载中…'
          : '暂无数据',
    small: pendingError.value,
  },
  {
    label: '已确认预约',
    value: confirmedError.value
      ? '加载失败'
      : confirmedTotal.value !== null
        ? String(confirmedTotal.value)
        : '—',
    hint: confirmedError.value
      ? '预约接口加载失败'
      : confirmedTotal.value !== null
        ? '已通过、尚未完成的预约'
        : loading.value
          ? '加载中…'
          : '暂无数据',
    small: confirmedError.value,
  },
  {
    label: '未读消息',
    value: chatError.value
      ? '加载失败'
      : unreadCount.value !== null
        ? String(unreadCount.value)
        : '—',
    hint: chatError.value
      ? '消息接口加载失败'
      : unreadCount.value !== null
        ? `${unreadSessionCount.value} 个会话有待读消息`
        : loading.value
          ? '加载中…'
          : '暂无数据',
    small: chatError.value,
  },
])

/* ------------------------------- 数据加载 ------------------------------- */

async function loadAll(): Promise<void> {
  loading.value = true
  try {
    const [meRes, studentsRes, pendingRes, confirmedRes, chatRes] =
      await Promise.allSettled([
        getMeApi(),
        listMyStudentsApi({ pageNum: 1, pageSize: 5 }),
        listCoachAppointmentsApi({ status: 'PENDING', pageNum: 1, pageSize: 5 }),
        listCoachAppointmentsApi({ status: 'CONFIRMED', pageNum: 1, pageSize: 1 }),
        listChatSessionsApi('coach'),
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

    if (studentsRes.status === 'fulfilled') {
      students.value = studentsRes.value.records
      studentsTotal.value = studentsRes.value.total
      studentsError.value = false
    } else {
      students.value = []
      studentsTotal.value = null
      studentsError.value = true
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

    if (confirmedRes.status === 'fulfilled') {
      confirmedTotal.value = confirmedRes.value.total
      confirmedError.value = false
    } else {
      confirmedTotal.value = null
      confirmedError.value = true
    }

    if (chatRes.status === 'fulfilled') {
      unreadCount.value = chatRes.value.reduce(
        (sum, session) => sum + Math.max(0, session.unreadCount || 0),
        0,
      )
      unreadSessionCount.value = chatRes.value.filter(
        (session) => (session.unreadCount || 0) > 0,
      ).length
      chatError.value = false
    } else {
      unreadCount.value = null
      unreadSessionCount.value = 0
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
  <div v-loading="loading" class="cdash">
    <div class="cdash__head">
      <div>
        <h2 class="cdash__title">教练工作台</h2>
        <div class="cdash__desc">
          数据来自学员管理、预约与聊天接口，不使用演示数字。
        </div>
      </div>
      <el-button :loading="loading" @click="loadAll">刷新数据</el-button>
    </div>

    <el-alert
      v-if="profileError"
      class="cdash__alert"
      type="warning"
      show-icon
      :closable="false"
      title="账号信息加载失败，当前显示本地登录信息"
      description="其它区块仍会独立加载；可点击“刷新数据”重试。"
    />

    <el-card shadow="never" class="cdash__welcome">
      <div class="cdash__welcome-row">
        <div>
          <div class="cdash__hello">你好，{{ displayName }}</div>
          <div class="cdash__meta">
            <span>用户名：{{ username }}</span>
            <el-divider direction="vertical" />
            <span>角色：教练</span>
            <el-divider direction="vertical" />
            <span>手机号：{{ me?.phone || '未填写' }}</span>
          </div>
        </div>
        <div class="cdash__welcome-actions">
          <el-tag
            v-if="pendingTotal !== null"
            :type="pendingTotal > 0 ? 'warning' : 'info'"
            effect="plain"
          >
            待处理预约 {{ pendingTotal }}
          </el-tag>
          <el-tag
            v-if="unreadCount !== null"
            :type="unreadCount > 0 ? 'danger' : 'info'"
            effect="plain"
          >
            未读消息 {{ unreadCount }}
          </el-tag>
          <el-button plain @click="go('/coach/profile')">个人中心</el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="cdash__cards">
      <el-col v-for="item in metricCards" :key="item.label" :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover">
          <div class="cdash__label">{{ item.label }}</div>
          <div class="cdash__value" :class="{ 'cdash__value--small': item.small }">
            {{ item.value }}
          </div>
          <div class="cdash__hint">{{ item.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="cdash__row">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never" class="cdash__card">
          <template #header>
            <div class="cdash__card-head">
              <span class="cdash__card-title">待处理预约</span>
              <el-button link type="primary" @click="go('/coach/appointments')">
                前往预约管理
              </el-button>
            </div>
          </template>

          <el-alert
            v-if="pendingError"
            type="warning"
            show-icon
            :closable="false"
            title="待处理预约加载失败，可前往预约管理页重试"
          />

          <el-table
            v-else
            :data="pendingAppointments"
            size="small"
            empty-text="暂无待确认预约"
          >
            <el-table-column label="学员">
              <template #default="{ row }">{{ row.studentName || '—' }}</template>
            </el-table-column>
            <el-table-column label="科目" width="90">
              <template #default="{ row }">
                {{ appointmentSubjectLabel(row.subjectCode) }}
              </template>
            </el-table-column>
            <el-table-column label="预约时间" width="190">
              <template #default="{ row }">
                {{ row.appointmentDate }} {{ row.timeSlot }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="appointmentStatusMeta(row.status).type" size="small">
                  {{ appointmentStatusMeta(row.status).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="提交时间" width="165" />
            <el-table-column label="操作" width="80">
              <template #default>
                <el-button link type="primary" @click="go('/coach/appointments')">
                  去处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card shadow="never" class="cdash__card">
          <template #header>
            <div class="cdash__card-head">
              <span class="cdash__card-title">名下学员</span>
              <el-button link type="primary" @click="go('/coach/students')">
                查看全部（{{ studentsTotal ?? '—' }}）
              </el-button>
            </div>
          </template>

          <el-alert
            v-if="studentsError"
            type="warning"
            show-icon
            :closable="false"
            title="学员数据加载失败，可前往学员管理页重试"
          />

          <el-empty
            v-else-if="!loading && students.length === 0"
            description="暂无有效绑定学员"
            :image-size="60"
          >
            <el-button type="primary" plain @click="go('/coach/students')">
              去绑定学员
            </el-button>
          </el-empty>

          <div v-else-if="students.length > 0" class="cdash__students">
            <div v-for="student in students" :key="student.bindId" class="cdash__student">
              <div class="cdash__student-main">
                <div class="cdash__student-name">
                  {{ student.realName || student.username }}
                </div>
                <div class="cdash__student-meta">
                  {{ subjectLabel(student.currentSubjectCode) }} ·
                  {{ phaseLabel(student.currentPhaseStatus) }}
                </div>
              </div>
              <el-tag :type="confirmMeta(student.coachConfirmStatus).type" size="small">
                {{ confirmMeta(student.coachConfirmStatus).label }}
              </el-tag>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="cdash__card">
      <template #header>
        <div class="cdash__card-head">
          <span class="cdash__card-title">快捷操作</span>
        </div>
      </template>

      <div class="cdash__links">
        <el-button @click="go('/coach/students')">学员管理</el-button>
        <el-button @click="go('/coach/appointments')">预约管理</el-button>
        <el-badge :value="unreadCount ?? 0" :hidden="!unreadCount" class="cdash__badge">
          <el-button @click="go('/coach/chat')">学员消息</el-button>
        </el-badge>
        <el-button @click="go('/coach/profile')">个人中心</el-button>
      </div>

      <div v-if="chatError" class="cdash__muted cdash__links-note">
        未读消息加载失败，不影响进入学员消息页面。
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.cdash__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.cdash__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.cdash__desc {
  margin-top: 6px;
  font-size: 13px;
  color: #687589;
}
.cdash__alert {
  margin-bottom: 16px;
}
.cdash__welcome {
  margin-bottom: 16px;
}
.cdash__welcome-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.cdash__hello {
  font-size: 20px;
  font-weight: 600;
  color: #263449;
}
.cdash__meta {
  margin-top: 8px;
  font-size: 13px;
  color: #687589;
}
.cdash__welcome-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cdash__cards {
  margin-bottom: 16px;
}
.cdash__label {
  font-size: 13px;
  color: #687589;
}
.cdash__value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 600;
  color: #263449;
}
.cdash__value--small {
  font-size: 18px;
}
.cdash__hint {
  margin-top: 6px;
  min-height: 18px;
  font-size: 12px;
  color: #a3adbb;
}
.cdash__row {
  margin-bottom: 16px;
}
.cdash__card {
  margin-bottom: 16px;
}
.cdash__card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.cdash__card-title {
  font-weight: 600;
}
.cdash__muted {
  font-size: 12px;
  color: #687589;
}
.cdash__students {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.cdash__student {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.cdash__student-main {
  min-width: 0;
}
.cdash__student-name {
  font-size: 14px;
  font-weight: 600;
  color: #263449;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cdash__student-meta {
  margin-top: 4px;
  font-size: 12px;
  color: #687589;
}
.cdash__links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.cdash__links .el-button {
  margin-left: 0;
}
.cdash__links-note {
  margin-top: 12px;
}
.cdash__badge {
  display: inline-flex;
}
</style>
