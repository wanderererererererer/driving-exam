<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createAdminUserApi,
  getAdminUserApi,
  listAdminUsersApi,
  setAdminUserStatusApi,
  updateAdminUserApi,
  type AdminRole,
  type AdminUserVO,
} from '@/api/admin'

/** 管理员端 · 用户管理（列表、创建、编辑、启用/禁用；不物理删除）。 */
const PAGE_SIZE = 10
const role = ref<'' | AdminRole>('')
const keyword = ref('')
const list = ref<AdminUserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
const togglingId = ref<number | null>(null)
let seq = 0

const ROLE_LABEL: Record<AdminRole, string> = {
  STUDENT: '学员',
  COACH: '教练',
  ADMIN: '管理员',
}

const dialogVisible = ref(false)
const saving = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  role: 'STUDENT' as AdminRole,
  status: 1,
})

const rules = computed<FormRules>(() => {
  const base: FormRules = {
    realName: [{ max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' }],
    phone: [{ max: 20, message: '手机号不能超过 20 个字符', trigger: 'blur' }],
  }
  if (mode.value === 'create') {
    base.username = [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { max: 50, message: '用户名不能超过 50 个字符', trigger: 'blur' },
    ]
    base.password = [{ required: true, message: '请输入初始密码', trigger: 'blur' }]
  }
  return base
})

async function load(): Promise<void> {
  const my = ++seq
  loading.value = true
  loadError.value = false
  try {
    const page = await listAdminUsersApi({
      role: role.value || undefined,
      keyword: keyword.value.trim() || undefined,
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
  mode.value = 'create'
  editingId.value = null
  form.username = ''
  form.password = ''
  form.realName = ''
  form.phone = ''
  form.role = 'STUDENT'
  form.status = 1
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function openEdit(row: AdminUserVO): Promise<void> {
  try {
    const detail = await getAdminUserApi(row.id)
    mode.value = 'edit'
    editingId.value = detail.id
    form.username = detail.username
    form.password = ''
    form.realName = detail.realName ?? ''
    form.phone = detail.phone ?? ''
    form.role = detail.role
    form.status = detail.status
    dialogVisible.value = true
    void nextTick(() => formRef.value?.clearValidate())
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

async function save(): Promise<void> {
  if (!formRef.value || saving.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    if (mode.value === 'create') {
      await createAdminUserApi({
        username: form.username.trim(),
        password: form.password,
        realName: form.realName.trim() || undefined,
        phone: form.phone.trim() || undefined,
        role: form.role,
        status: form.status,
      })
      ElMessage.success('用户已创建')
      pageNum.value = 1
    } else if (editingId.value != null) {
      await updateAdminUserApi(editingId.value, {
        realName: form.realName.trim(),
        phone: form.phone.trim(),
        role: form.role,
        status: form.status,
      })
      ElMessage.success('用户已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: AdminUserVO): Promise<void> {
  if (togglingId.value !== null) return
  const next = row.status === 1 ? 0 : 1
  togglingId.value = row.id
  try {
    await setAdminUserStatusApi(row.id, next)
    ElMessage.success(next === 1 ? '已启用' : '已禁用')
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    togglingId.value = null
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="auser">
    <div class="auser__head">
      <h2 class="auser__title">用户管理</h2>
      <el-button type="primary" class="auser__new" @click="openCreate">新增用户</el-button>
    </div>

    <el-card shadow="never">
      <div class="auser__toolbar">
        <el-select v-model="role" placeholder="全部角色" clearable style="width: 140px">
          <el-option label="学员" value="STUDENT" />
          <el-option label="教练" value="COACH" />
          <el-option label="管理员" value="ADMIN" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="用户名 / 姓名"
          clearable
          style="width: 220px"
          @keyup.enter="search"
        />
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-result
        v-if="loadError"
        icon="error"
        title="用户列表加载失败"
        sub-title="请检查网络或稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column label="姓名" min-width="110">
            <template #default="{ row }">{{ row.realName || '—' }}</template>
          </el-table-column>
          <el-table-column label="手机号" min-width="130">
            <template #default="{ row }">{{ row.phone || '未填写' }}</template>
          </el-table-column>
          <el-table-column label="角色" width="100">
            <template #default="{ row }">{{ ROLE_LABEL[row.role as AdminRole] ?? row.role }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                :loading="togglingId === row.id"
                :disabled="togglingId !== null && togglingId !== row.id"
                @click="toggleStatus(row)"
              >
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无用户" /></template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="auser__pager">
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

    <el-dialog
      v-model="dialogVisible"
      :title="mode === 'create' ? '新增用户' : '编辑用户'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="mode === 'edit'" maxlength="50" />
        </el-form-item>
        <el-form-item v-if="mode === 'create'" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="学员" value="STUDENT" />
            <el-option label="教练" value="COACH" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.auser__head {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.auser__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.auser__new {
  margin-left: auto;
}
.auser__toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.auser__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
