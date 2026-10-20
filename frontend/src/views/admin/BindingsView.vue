<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAdminBindingApi,
  listAdminBindingsApi,
  unbindAdminApi,
  type AdminBindingVO,
} from '@/api/admin'

/** 管理员端 · 绑定管理（查看、绑定、软解绑；不删除历史）。 */
const PAGE_SIZE = 10
const status = ref<number | ''>('')
const list = ref<AdminBindingVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
const unbindingId = ref<number | null>(null)
let seq = 0

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ coachId: undefined as number | undefined, studentId: undefined as number | undefined })
const rules: FormRules = {
  coachId: [{ required: true, message: '请输入教练 ID', trigger: 'change' }],
  studentId: [{ required: true, message: '请输入学员 ID', trigger: 'change' }],
}

async function load(): Promise<void> {
  const my = ++seq
  loading.value = true
  loadError.value = false
  try {
    const page = await listAdminBindingsApi({
      status: status.value === '' ? undefined : status.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    if (my !== seq) return
    list.value = page.records
    total.value = page.total
  } catch {
    if (my !== seq) return
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    if (my === seq) loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function onPage(page: number): void {
  pageNum.value = page
  void load()
}

function openCreate(): void {
  form.coachId = undefined
  form.studentId = undefined
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function create(): Promise<void> {
  if (!formRef.value || saving.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  if (!form.coachId || !form.studentId) return
  saving.value = true
  try {
    await createAdminBindingApi({ coachId: form.coachId, studentId: form.studentId })
    ElMessage.success('绑定已创建')
    dialogVisible.value = false
    pageNum.value = 1
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function unbind(row: AdminBindingVO): Promise<void> {
  if (unbindingId.value !== null) return
  try {
    await ElMessageBox.confirm(
      `确定解除「${row.coachName || row.coachId} ↔ ${row.studentName || row.studentId}」的绑定吗？` +
        '这是软解绑，历史数据与预约不会被删除或取消。',
      '解除绑定',
      { type: 'warning' },
    )
  } catch {
    return
  }
  unbindingId.value = row.bindId
  try {
    await unbindAdminApi(row.bindId)
    ElMessage.success('已解除绑定')
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    unbindingId.value = null
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="abind">
    <div class="abind__head">
      <h2 class="abind__title">绑定管理</h2>
      <el-button type="primary" class="abind__new" @click="openCreate">新增绑定</el-button>
    </div>

    <el-card shadow="never">
      <div class="abind__toolbar">
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="有效" :value="1" />
          <el-option label="已解除" :value="0" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-result v-if="loadError" icon="error" title="绑定列表加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" @click="load">重试</el-button></template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column label="教练" min-width="150">
            <template #default="{ row }">
              <div>{{ row.coachName || '—' }}</div>
              <div class="abind__sub">ID {{ row.coachId }} · {{ row.coachPhone || '未填写电话' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="学员" min-width="150">
            <template #default="{ row }">
              <div>{{ row.studentName || '—' }}</div>
              <div class="abind__sub">ID {{ row.studentId }} · {{ row.studentPhone || '未填写电话' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="bindTime" label="绑定时间" width="180" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '有效' : '已解除' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button
                v-if="row.status === 1"
                link
                type="danger"
                :loading="unbindingId === row.bindId"
                :disabled="unbindingId !== null && unbindingId !== row.bindId"
                @click="unbind(row)"
              >
                解绑
              </el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无绑定记录" /></template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="abind__pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="PAGE_SIZE"
            :current-page="pageNum"
            @current-change="onPage"
          />
        </div>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增绑定" width="460px" :close-on-click-modal="false">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="请输入用户 ID（可在用户管理中查看）；教练角色必须为 COACH，学员必须为 STUDENT。"
        class="abind__alert"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="教练 ID" prop="coachId">
          <el-input-number v-model="form.coachId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="学员 ID" prop="studentId">
          <el-input-number v-model="form.studentId" :min="1" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.abind__head {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.abind__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.abind__new {
  margin-left: auto;
}
.abind__toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.abind__sub {
  font-size: 12px;
  color: #687589;
}
.abind__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.abind__alert {
  margin-bottom: 16px;
}
</style>
