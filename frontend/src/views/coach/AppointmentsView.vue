<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveAppointmentApi,
  completeAppointmentApi,
  listCoachAppointmentsApi,
  rejectAppointmentApi,
} from '@/api/appointment'
import type { AppointmentStatus, AppointmentVO } from '@/types'

/**
 * 教练端 · 预约管理。
 *
 * <p>数据来自 {@code GET /api/coach/appointments}（仅当前教练名下，按创建时间倒序，
 * 排序完全由后端负责）。后端<b>没有默认状态过滤</b>，本页默认筛选「待确认」（PENDING），
 * 与后端注释（"待处理优先应在前端默认选中 status=PENDING"）保持一致。</p>
 *
 * <p>操作规则（与后端一致）：通过 / 拒绝仅 PENDING；完成仅 CONFIRMED；
 * 其余为终态，不显示任何操作按钮。错误提示统一由 {@code utils/request.ts} 拦截器弹出，
 * 页面 catch 只负责停止 loading，不重复提示。</p>
 */
type AppointmentSubject = 'SUBJECT_2' | 'SUBJECT_3'

const SUBJECT_NAMES: Record<AppointmentSubject, string> = {
  SUBJECT_2: '科目二',
  SUBJECT_3: '科目三',
}

function isAppointmentSubject(value: string): value is AppointmentSubject {
  return value === 'SUBJECT_2' || value === 'SUBJECT_3'
}

function subjectLabel(code: string): string {
  return isAppointmentSubject(code) ? SUBJECT_NAMES[code] : code
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

/** 默认筛选「待确认」：后端不传 status 即不过滤，默认值必须由前端给出 */
const status = ref<StatusFilter>('PENDING')
const list = ref<AppointmentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)

const emptyText = computed(() => {
  if (status.value === 'ALL') return '暂无预约记录'
  if (status.value === 'PENDING') return '暂无待确认预约'
  return '该状态下暂无预约'
})

/** 列表请求序号：只应用最后一次请求的结果，避免快速切筛选时旧响应覆盖新数据 */
let listSeq = 0

async function loadList(): Promise<void> {
  const seq = ++listSeq
  loading.value = true
  loadError.value = false
  try {
    const page = await listCoachAppointmentsApi({
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

/** 操作成功后刷新；若当前页被操作到空且不是第一页，回退一页 */
async function reloadAfterAction(): Promise<void> {
  await loadList()
  if (!loadError.value && list.value.length === 0 && total.value > 0 && pageNum.value > 1) {
    pageNum.value -= 1
    await loadList()
  }
}

/* -------------------------------- 操作状态 -------------------------------- */

type ActionType = 'approve' | 'reject' | 'complete'

/** 正在提交操作的预约 id（非 null 时禁用所有操作按钮，防重复提交） */
const actingId = ref<number | null>(null)
const actingType = ref<ActionType | null>(null)

function resetActing(): void {
  actingId.value = null
  actingType.value = null
}

/* --------------------------------- 通过 --------------------------------- */

async function onApprove(row: AppointmentVO): Promise<void> {
  if (actingId.value !== null) return
  actingId.value = row.id
  actingType.value = 'approve'
  try {
    await approveAppointmentApi(row.id)
    ElMessage.success('已通过该预约')
    await reloadAfterAction()
  } catch {
    // 失败提示由拦截器统一弹出（如 3005 状态不允许）
  } finally {
    resetActing()
  }
}

/* --------------------------------- 拒绝 --------------------------------- */

const rejectVisible = ref(false)
const rejectTarget = ref<AppointmentVO | null>(null)
const rejectRemark = ref('')
const rejecting = ref(false)

function openReject(row: AppointmentVO): void {
  if (actingId.value !== null) return
  rejectTarget.value = row
  rejectRemark.value = ''
  rejectVisible.value = true
}

async function submitReject(): Promise<void> {
  const target = rejectTarget.value
  if (!target || rejecting.value) return

  const remark = rejectRemark.value.trim()
  rejecting.value = true
  actingId.value = target.id
  actingType.value = 'reject'
  try {
    // 后端 @RequestBody 必填：即使不填原因也必须传 {}（不能省略 body，否则 1001）
    await rejectAppointmentApi(target.id, remark ? { coachRemark: remark } : {})
    ElMessage.success('已拒绝该预约')
    rejectVisible.value = false
    await reloadAfterAction()
  } catch {
    // 失败提示由拦截器统一弹出；保留弹窗便于调整原因后重试
  } finally {
    rejecting.value = false
    resetActing()
  }
}

/* --------------------------------- 完成 --------------------------------- */

async function onComplete(row: AppointmentVO): Promise<void> {
  if (actingId.value !== null) return
  try {
    await ElMessageBox.confirm(
      `确认学员「${row.studentName ?? '该学员'}」的预约已完成吗？完成后不可再变更。`,
      '完成预约',
      { type: 'warning' },
    )
  } catch {
    return // 用户放弃
  }

  actingId.value = row.id
  actingType.value = 'complete'
  try {
    await completeAppointmentApi(row.id)
    ElMessage.success('已标记为完成')
    await reloadAfterAction()
  } catch {
    // 失败提示由拦截器统一弹出（如 3005 状态不允许）
  } finally {
    resetActing()
  }
}

/* --------------------------------- 初始化 -------------------------------- */

onMounted(() => {
  void loadList()
})
</script>

<template>
  <div class="cap">
    <div class="cap__head">
      <h2 class="cap__title">预约管理</h2>
      <span class="cap__desc">默认展示「待确认」预约；通过 / 拒绝后列表自动刷新。</span>
    </div>

    <el-card shadow="never">
      <div class="cap__toolbar">
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
          <el-table-column label="学员" min-width="130">
            <template #default="{ row }">
              <div>{{ row.studentName ?? '—' }}</div>
              <div class="cap__sub">{{ row.studentPhone ?? '未填写电话' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="科目" width="90">
            <template #default="{ row }">{{ subjectLabel(row.subjectCode) }}</template>
          </el-table-column>
          <el-table-column prop="appointmentDate" label="预约日期" width="120" />
          <el-table-column prop="timeSlot" label="时段" width="130" />
          <el-table-column label="学员备注" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.remark || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <!-- 仅 PENDING 可通过 / 拒绝 -->
              <template v-if="row.status === 'PENDING'">
                <el-button
                  link
                  type="primary"
                  :loading="actingId === row.id && actingType === 'approve'"
                  :disabled="actingId !== null"
                  @click="onApprove(row)"
                >
                  通过
                </el-button>
                <el-button
                  link
                  type="danger"
                  :disabled="actingId !== null"
                  @click="openReject(row)"
                >
                  拒绝
                </el-button>
              </template>
              <!-- 仅 CONFIRMED 可完成 -->
              <el-button
                v-else-if="row.status === 'CONFIRMED'"
                link
                type="success"
                :loading="actingId === row.id && actingType === 'complete'"
                :disabled="actingId !== null"
                @click="onComplete(row)"
              >
                完成
              </el-button>
              <!-- 终态（已取消 / 已拒绝 / 已完成）不提供操作 -->
              <span v-else class="cap__muted">—</span>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty :description="emptyText" />
          </template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="cap__pager">
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

    <!-- 拒绝预约弹窗 -->
    <el-dialog
      v-model="rejectVisible"
      title="拒绝预约"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="!rejecting"
      :show-close="!rejecting"
    >
      <div v-if="rejectTarget" class="cap__reject-tip">
        学员：{{ rejectTarget.studentName ?? '—' }} ｜
        {{ subjectLabel(rejectTarget.subjectCode) }}
        {{ rejectTarget.appointmentDate }} {{ rejectTarget.timeSlot }}
      </div>
      <el-input
        v-model="rejectRemark"
        type="textarea"
        :rows="3"
        maxlength="255"
        show-word-limit
        placeholder="拒绝原因（选填，最多 255 字）"
        :disabled="rejecting"
      />

      <template #footer>
        <el-button :disabled="rejecting" @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="submitReject">
          确认拒绝
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.cap__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.cap__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.cap__desc {
  font-size: 13px;
  color: #687589;
}
.cap__toolbar {
  margin-bottom: 16px;
}
.cap__sub {
  font-size: 12px;
  color: #687589;
}
.cap__muted {
  color: #a3adbb;
}
.cap__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.cap__reject-tip {
  margin-bottom: 12px;
  font-size: 13px;
  color: #4a5a70;
}
</style>
