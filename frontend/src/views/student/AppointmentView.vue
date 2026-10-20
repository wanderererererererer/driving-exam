<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  cancelAppointmentApi,
  createAppointmentApi,
  getMyAppointmentsApi,
  listMyCoachesApi,
} from '@/api/appointment'
import type {
  AppointmentStatus,
  AppointmentTimeSlot,
  AppointmentVO,
  CoachVO,
} from '@/types'

/**
 * 学员端 · 预约（科目二 / 科目三共用）。
 *
 * <p>科目来自路由 query：{@code /student/appointment?subjectCode=SUBJECT_2} 或
 * {@code ?subjectCode=SUBJECT_3}（由学员端侧边菜单的「科目二」「科目三」进入）。
 * 预约接口只接受这两个科目，缺参或非法值一律<b>提示并跳回首页</b>，不静默默认。</p>
 *
 * <p>功能：预约列表（状态筛选 + 分页，排序完全由后端负责）、新建预约、取消「待确认」预约、
 * 预约详情（直接复用列表行数据，<b>不额外请求</b> {@code GET /student/appointment/{id}}，
 * 避免同一份数据重复拉取）。</p>
 *
 * <p>错误提示统一由 {@code utils/request.ts} 拦截器弹出（含 3002 时段冲突），
 * 页面 catch 只负责落地"失败态 / 停止 loading"，不重复提示。</p>
 */
const route = useRoute()
const router = useRouter()

/* ------------------------------ 科目与路由校验 ------------------------------ */

type AppointmentSubject = 'SUBJECT_2' | 'SUBJECT_3'

/** 预约类科目（与后端 AppointmentCreateDTO 的 subjectCode 白名单一致） */
const SUBJECT_NAMES: Record<AppointmentSubject, string> = {
  SUBJECT_2: '科目二',
  SUBJECT_3: '科目三',
}

function isAppointmentSubject(value: string): value is AppointmentSubject {
  return value === 'SUBJECT_2' || value === 'SUBJECT_3'
}

const subjectCode = computed(() => String(route.query.subjectCode ?? ''))
const valid = computed(() => isAppointmentSubject(subjectCode.value))
const subjectName = computed(() =>
  isAppointmentSubject(subjectCode.value) ? SUBJECT_NAMES[subjectCode.value] : '',
)

function subjectLabel(code: string): string {
  return isAppointmentSubject(code) ? SUBJECT_NAMES[code] : code
}

/** 返回首页（科目二/三暂无独立科目首页） */
function goBack(): void {
  router.push('/student/dashboard')
}

/* -------------------------------- 状态映射 -------------------------------- */

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

/* ------------------------------- 列表与筛选 ------------------------------- */

type StatusFilter = AppointmentStatus | 'ALL'

const STATUS_OPTIONS: { label: string; value: StatusFilter }[] = [
  { label: '全部', value: 'ALL' },
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已完成', value: 'COMPLETED' },
]

/** 分页大小：后端上限 20，这里固定 10 */
const PAGE_SIZE = 10

const status = ref<StatusFilter>('ALL')
const list = ref<AppointmentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
/** 正在取消的预约 id（防重复点击） */
const cancelling = ref(false)

const emptyText = computed(() =>
  status.value === 'ALL' ? '暂无预约记录' : '该状态下暂无预约',
)

/** 列表请求序号：只应用最后一次请求的结果，避免快速切筛选时旧响应覆盖新数据 */
let listSeq = 0

async function loadList(): Promise<void> {
  if (!isAppointmentSubject(subjectCode.value)) return
  const seq = ++listSeq
  loading.value = true
  loadError.value = false
  try {
    const page = await getMyAppointmentsApi({
      subjectCode: subjectCode.value,
      status: status.value === 'ALL' ? undefined : status.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    if (seq !== listSeq) return
    list.value = page.records
    total.value = page.total
  } catch {
    if (seq !== listSeq) return
    // 失败提示已由请求拦截器统一弹出，这里只落地错误态
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    if (seq === listSeq) {
      loading.value = false
    }
  }
}

function onStatusChange(): void {
  pageNum.value = 1
  void loadList()
}

function onPageChange(page: number): void {
  pageNum.value = page
  void loadList()
}

/* -------------------------------- 新建预约 -------------------------------- */

/** 固定时段（与后端 AppointmentCreateDTO 的 @Pattern 白名单一致） */
const TIME_SLOTS: AppointmentTimeSlot[] = [
  '08:00-10:00',
  '10:00-12:00',
  '14:00-16:00',
  '16:00-18:00',
]

interface CreateForm {
  coachId: number | null
  /** yyyy-MM-dd（el-date-picker 清空时为 null） */
  appointmentDate: string | null
  timeSlot: AppointmentTimeSlot | ''
  remark: string
}

const newVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<CreateForm>({
  coachId: null,
  appointmentDate: null,
  timeSlot: '',
  remark: '',
})

const rules: FormRules = {
  coachId: [{ required: true, message: '请选择教练', trigger: 'change' }],
  appointmentDate: [{ required: true, message: '请选择预约日期', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请选择预约时段', trigger: 'change' }],
  remark: [{ max: 255, message: '备注最多 255 个字符', trigger: 'blur' }],
}

const coaches = ref<CoachVO[]>([])
const coachesLoading = ref(false)
const coachesFailed = ref(false)

/** 教练列表请求序号：只应用最后一次请求的结果 */
let coachesSeq = 0

async function loadCoaches(): Promise<void> {
  const seq = ++coachesSeq
  coachesLoading.value = true
  coachesFailed.value = false
  try {
    const data = await listMyCoachesApi()
    if (seq !== coachesSeq) return
    coaches.value = data
  } catch {
    if (seq !== coachesSeq) return
    coaches.value = []
    coachesFailed.value = true
  } finally {
    if (seq === coachesSeq) {
      coachesLoading.value = false
    }
  }
}

function coachLabel(coach: CoachVO): string {
  const name = coach.realName ?? `教练 #${coach.coachId}`
  return coach.phone ? `${name}（${coach.phone}）` : name
}

/** 禁止选择今天之前的日期（允许今天） */
function disabledDate(date: Date): boolean {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return date.getTime() < today.getTime()
}

function openCreate(): void {
  form.coachId = null
  form.appointmentDate = null
  form.timeSlot = ''
  form.remark = ''
  newVisible.value = true
  // 弹窗内容首次打开才渲染，等渲染后再清校验
  void nextTick(() => formRef.value?.clearValidate())
  void loadCoaches()
}

async function onSubmit(): Promise<void> {
  if (!formRef.value || submitting.value) return
  if (coaches.value.length === 0) return // 无绑定教练：提交按钮已禁用，双保险

  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return

  const coachId = form.coachId
  const appointmentDate = form.appointmentDate
  const timeSlot = form.timeSlot
  const sc = subjectCode.value
  if (coachId === null || !appointmentDate || timeSlot === '' || !isAppointmentSubject(sc)) {
    return
  }

  submitting.value = true
  try {
    await createAppointmentApi({
      coachId,
      subjectCode: sc,
      appointmentDate,
      timeSlot,
      remark: form.remark.trim() || undefined,
    })
    ElMessage.success('预约已提交，等待教练确认')
    newVisible.value = false
    // 后端不返回新预约 id：重置到「全部 + 第 1 页」重新拉取，确保能看到新建结果
    status.value = 'ALL'
    pageNum.value = 1
    await loadList()
  } catch {
    // 失败提示由拦截器统一弹出（含 3002 时段冲突），保持弹窗打开便于修改
  } finally {
    submitting.value = false
  }
}

/* --------------------------------- 详情 --------------------------------- */

/**
 * 详情直接展示列表行数据（AppointmentVO 在列表接口中已完整返回），
 * 不调用 getMyAppointmentApi，避免为同一份数据制造重复请求。
 */
const detailVisible = ref(false)
const detail = ref<AppointmentVO | null>(null)

function openDetail(row: AppointmentVO): void {
  detail.value = row
  detailVisible.value = true
}

/* --------------------------------- 取消 --------------------------------- */

async function onCancel(row: AppointmentVO): Promise<void> {
  if (cancelling.value) return
  try {
    await ElMessageBox.confirm(
      `确定取消「${row.appointmentDate} ${row.timeSlot}」的预约吗？`,
      '取消预约',
      { type: 'warning' },
    )
  } catch {
    return // 用户放弃
  }

  cancelling.value = true
  try {
    await cancelAppointmentApi(row.id)
    ElMessage.success('预约已取消')
    detailVisible.value = false
    await loadList()
    // 当前页被取消到空时回退一页（列表按创建时间倒序，末页可能只剩这一条）
    if (!loadError.value && list.value.length === 0 && total.value > 0 && pageNum.value > 1) {
      pageNum.value -= 1
      await loadList()
    }
  } catch {
    // 失败提示由拦截器统一弹出（如 3004 仅可取消待确认）
  } finally {
    cancelling.value = false
  }
}

function cancelFromDetail(): void {
  if (detail.value) void onCancel(detail.value)
}

/* ------------------------------- 科目变化 ------------------------------- */

// 校验科目 + 重置列表（immediate：进入即跑；同时覆盖同组件内 query 变化）
watch(
  subjectCode,
  (sc) => {
    newVisible.value = false
    detailVisible.value = false
    if (!isAppointmentSubject(sc)) {
      ElMessage.error('预约科目参数缺失或非法，已返回首页')
      router.replace('/student/dashboard')
      return
    }
    status.value = 'ALL'
    pageNum.value = 1
    void loadList()
  },
  { immediate: true },
)
</script>

<template>
  <div v-if="valid" class="ap">
    <div class="ap__head">
      <el-button text @click="goBack">← 返回首页</el-button>
      <h2 class="ap__title">{{ subjectName }} · 练车预约</h2>
      <el-button type="primary" class="ap__new" @click="openCreate">新建预约</el-button>
    </div>

    <el-card shadow="never">
      <div class="ap__toolbar">
        <el-radio-group v-model="status" @change="onStatusChange">
          <el-radio-button
            v-for="opt in STATUS_OPTIONS"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 加载失败：错误态 + 重试 -->
      <el-result
        v-if="loadError"
        icon="error"
        title="预约列表加载失败"
        sub-title="请检查网络或稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="loadList">重试</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column prop="appointmentDate" label="预约日期" width="120" />
          <el-table-column prop="timeSlot" label="时段" width="130" />
          <el-table-column label="教练" min-width="160">
            <template #default="{ row }">
              <div>{{ row.coachName ?? '—' }}</div>
              <div class="ap__sub">{{ row.coachPhone ?? '未填写电话' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button
                v-if="row.status === 'PENDING'"
                link
                type="danger"
                :disabled="cancelling"
                @click="onCancel(row)"
              >
                取消预约
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty :description="emptyText" />
          </template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="ap__pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="PAGE_SIZE"
            :current-page="pageNum"
            @current-change="onPageChange"
          />
        </div>
      </template>
    </el-card>

    <!-- 新建预约弹窗 -->
    <el-dialog
      v-model="newVisible"
      title="新建预约"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!submitting"
      :show-close="!submitting"
    >
      <el-alert
        v-if="coachesFailed"
        type="error"
        :closable="false"
        show-icon
        title="教练列表加载失败"
        class="ap__alert"
      >
        <el-button size="small" type="primary" plain @click="loadCoaches">重试</el-button>
      </el-alert>
      <el-alert
        v-else-if="!coachesLoading && coaches.length === 0"
        type="warning"
        :closable="false"
        show-icon
        title="暂无已绑定教练，请先联系教练完成绑定后再预约"
        class="ap__alert"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="预约科目">
          <el-tag size="small">{{ subjectName }}（{{ subjectCode }}）</el-tag>
        </el-form-item>
        <el-form-item label="教练" prop="coachId">
          <el-select
            v-model="form.coachId"
            placeholder="请选择教练"
            style="width: 100%"
            :loading="coachesLoading"
            :disabled="coachesLoading || coaches.length === 0"
          >
            <el-option
              v-for="coach in coaches"
              :key="coach.coachId"
              :label="coachLabel(coach)"
              :value="coach.coachId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期" prop="appointmentDate">
          <el-date-picker
            v-model="form.appointmentDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择日期"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="预约时段" prop="timeSlot">
          <el-select v-model="form.timeSlot" placeholder="请选择时段" style="width: 100%">
            <el-option v-for="slot in TIME_SLOTS" :key="slot" :label="slot" :value="slot" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="选填，最多 255 字"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="newVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="coachesLoading || coaches.length === 0"
          @click="onSubmit"
        >
          提交预约
        </el-button>
      </template>
    </el-dialog>

    <!-- 预约详情弹窗 -->
    <el-dialog v-model="detailVisible" title="预约详情" width="560px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="预约科目">
          {{ subjectLabel(detail.subjectCode) }}
        </el-descriptions-item>
        <el-descriptions-item label="预约日期">{{ detail.appointmentDate }}</el-descriptions-item>
        <el-descriptions-item label="预约时段">{{ detail.timeSlot }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMeta(detail.status).type" size="small">
            {{ statusMeta(detail.status).label }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="教练姓名">
          {{ detail.coachName ?? '—（账号信息不可用）' }}
        </el-descriptions-item>
        <el-descriptions-item label="教练电话">
          {{ detail.coachPhone ?? '未填写' }}
        </el-descriptions-item>
        <el-descriptions-item label="学员备注">
          {{ detail.remark || '无' }}
        </el-descriptions-item>
        <el-descriptions-item :label="detail.status === 'REJECTED' ? '拒绝原因' : '教练备注'">
          {{ detail.coachRemark || '无' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ detail.updateTime }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button
          v-if="detail?.status === 'PENDING'"
          type="danger"
          :loading="cancelling"
          @click="cancelFromDetail"
        >
          取消预约
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ap__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.ap__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.ap__new {
  margin-left: auto;
}
.ap__toolbar {
  margin-bottom: 16px;
}
.ap__sub {
  font-size: 12px;
  color: #687589;
}
.ap__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.ap__alert {
  margin-bottom: 16px;
}
</style>
