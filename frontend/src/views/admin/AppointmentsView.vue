<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listAdminAppointmentsApi } from '@/api/admin'
import type { AppointmentStatus, AppointmentVO } from '@/types'

/**
 * 管理员端 · 预约查看（只读）。
 *
 * <p>数据来自 {@code GET /api/admin/appointments}（全量预约、按创建时间倒序）；
 * 本页只有查看能力，不调用任何审批/拒绝/完成/取消接口。</p>
 */
type TagType = '' | 'success' | 'info' | 'warning' | 'danger'

const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_2: '科目二',
  SUBJECT_3: '科目三',
}

const STATUS_META: Record<AppointmentStatus, { label: string; type: TagType }> = {
  PENDING: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: '' },
  CANCELLED: { label: '已取消', type: 'info' },
  REJECTED: { label: '已拒绝', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'success' },
}

const STATUS_OPTIONS: { label: string; value: AppointmentStatus | 'ALL' }[] = [
  { label: '全部', value: 'ALL' },
  { label: '待确认', value: 'PENDING' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '已取消', value: 'CANCELLED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已完成', value: 'COMPLETED' },
]

const PAGE_SIZE = 10
const status = ref<AppointmentStatus | 'ALL'>('ALL')
const list = ref<AppointmentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
let listSeq = 0

function subjectLabel(code: string): string {
  return Object.prototype.hasOwnProperty.call(SUBJECT_NAMES, code) ? SUBJECT_NAMES[code] : code
}

function statusMeta(statusValue: AppointmentStatus) {
  return STATUS_META[statusValue]
}

const emptyText = computed(() => (status.value === 'ALL' ? '暂无预约记录' : '该状态下暂无预约'))

async function loadList(): Promise<void> {
  const seq = ++listSeq
  loading.value = true
  loadError.value = false
  try {
    const page = await listAdminAppointmentsApi({
      status: status.value === 'ALL' ? undefined : status.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    if (seq !== listSeq) return
    list.value = page.records
    total.value = page.total
  } catch {
    if (seq !== listSeq) return
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

onMounted(() => {
  void loadList()
})
</script>

<template>
  <div class="aav">
    <div class="aav__head">
      <h2 class="aav__title">预约查看</h2>
      <span class="aav__desc">全量预约只读视图；不提供审批、拒绝、完成或取消操作。</span>
    </div>

    <el-card shadow="never">
      <div class="aav__toolbar">
        <el-radio-group v-model="status" @change="onStatusChange">
          <el-radio-button v-for="opt in STATUS_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </el-radio-button>
        </el-radio-group>
      </div>

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
          <el-table-column label="学员" min-width="140">
            <template #default="{ row }">
              <div>{{ row.studentName ?? '—' }}</div>
              <div class="aav__sub">{{ row.studentPhone ?? '未填写电话' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="教练" min-width="140">
            <template #default="{ row }">
              <div>{{ row.coachName ?? '—' }}</div>
              <div class="aav__sub">{{ row.coachPhone ?? '未填写电话' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="科目" width="90">
            <template #default="{ row }">{{ subjectLabel(row.subjectCode) }}</template>
          </el-table-column>
          <el-table-column prop="appointmentDate" label="预约日期" width="120" />
          <el-table-column prop="timeSlot" label="时段" width="130" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMeta(row.status).type" size="small">
                {{ statusMeta(row.status).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />

          <template #empty>
            <el-empty :description="emptyText" />
          </template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="aav__pager">
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
  </div>
</template>

<style scoped>
.aav__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.aav__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.aav__desc {
  font-size: 13px;
  color: #687589;
}
.aav__toolbar {
  margin-bottom: 16px;
}
.aav__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #687589;
}
.aav__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
