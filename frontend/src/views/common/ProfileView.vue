<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  getMeApi,
  updatePasswordApi,
  updateProfileApi,
  type UserInfoVO,
} from '@/api/auth'
import { useUserStore } from '@/store/user'

/**
 * 学员 / 教练 / 管理员共用 · 个人中心。
 *
 * <p>完全复用现有认证接口：{@code GET /api/auth/me}、
 * {@code PUT /api/auth/profile}、{@code PUT /api/auth/password}；
 * 不新增角色专用资料接口，不伪造资料读取。</p>
 */
const userStore = useUserStore()

const loading = ref(false)
const loadError = ref(false)
const me = ref<UserInfoVO | null>(null)

const profileFormRef = ref<FormInstance>()
const profileSaving = ref(false)
const profileForm = reactive({
  realName: '',
  phone: '',
})

const profileRules: FormRules = {
  realName: [{ max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' }],
  phone: [{ max: 20, message: '手机号不能超过 20 个字符', trigger: 'blur' }],
}

const passwordFormRef = ref<FormInstance>()
const passwordSaving = ref(false)
const passwordForm = reactive({
  oldPwd: '',
  newPwd: '',
  confirm: '',
})

const passwordRules: FormRules = {
  oldPwd: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPwd: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirm: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }],
}

function roleText(role: string | undefined): string {
  if (role === 'STUDENT') return '学员'
  if (role === 'COACH') return '教练'
  if (role === 'ADMIN') return '管理员'
  return role || '—'
}

function statusText(status: number | undefined): string {
  if (status === 1) return '启用'
  if (status === 0) return '禁用'
  return '—'
}

function syncStore(info: UserInfoVO): void {
  userStore.setUserInfo({
    userId: info.id,
    username: info.username,
    realName: info.realName,
    role: info.role,
  })
}

async function loadProfile(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const info = await getMeApi()
    me.value = info
    profileForm.realName = info.realName ?? ''
    profileForm.phone = info.phone ?? ''
    syncStore(info)
  } catch {
    // 错误提示由 request.ts 拦截器统一弹出
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function saveProfile(): Promise<void> {
  if (!profileFormRef.value || profileSaving.value) return
  const ok = await profileFormRef.value.validate().catch(() => false)
  if (!ok) return

  profileSaving.value = true
  try {
    await updateProfileApi({
      realName: profileForm.realName.trim() || undefined,
      phone: profileForm.phone.trim() || undefined,
    })
    ElMessage.success('个人资料已更新')
    await loadProfile() // 重新读取，确认数据真实生效
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    profileSaving.value = false
  }
}

async function changePassword(): Promise<void> {
  if (!passwordFormRef.value || passwordSaving.value) return
  const ok = await passwordFormRef.value.validate().catch(() => false)
  if (!ok) return
  if (passwordForm.newPwd !== passwordForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  passwordSaving.value = true
  try {
    await updatePasswordApi({
      oldPwd: passwordForm.oldPwd,
      newPwd: passwordForm.newPwd,
    })
    ElMessage.success('密码已修改')
    passwordForm.oldPwd = ''
    passwordForm.newPwd = ''
    passwordForm.confirm = ''
    passwordFormRef.value.clearValidate()
  } catch {
    // 错误提示由拦截器统一弹出（如 1104 原密码不正确）
  } finally {
    passwordSaving.value = false
  }
}

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <div class="apf">
    <div class="apf__head">
      <h2 class="apf__title">个人中心</h2>
      <span class="apf__desc">复用现有认证接口；资料与密码修改成功后重新读取资料。</span>
    </div>

    <el-result
      v-if="loadError"
      icon="error"
      title="个人资料加载失败"
      sub-title="请检查网络或稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="loadProfile">重试</el-button>
      </template>
    </el-result>

    <template v-else>
      <el-card v-loading="loading" shadow="never" class="apf__card">
        <template #header>
          <span class="apf__card-title">账号信息</span>
        </template>
        <el-descriptions v-if="me" :column="3" border>
          <el-descriptions-item label="用户名">{{ me.username }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleText(me.role) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(me.status) }}</el-descriptions-item>
        </el-descriptions>

        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-width="90px"
          class="apf__form"
        >
          <el-form-item label="姓名" prop="realName">
            <el-input v-model="profileForm.realName" maxlength="50" placeholder="真实姓名" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="profileForm.phone" maxlength="20" placeholder="手机号" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="profileSaving" @click="saveProfile">
              保存资料
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="apf__card">
        <template #header>
          <span class="apf__card-title">修改密码</span>
        </template>
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-width="90px"
          class="apf__form"
        >
          <el-form-item label="原密码" prop="oldPwd">
            <el-input
              v-model="passwordForm.oldPwd"
              type="password"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPwd">
            <el-input
              v-model="passwordForm.newPwd"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirm">
            <el-input
              v-model="passwordForm.confirm"
              type="password"
              show-password
              autocomplete="new-password"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordSaving" @click="changePassword">
              修改密码
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.apf__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.apf__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.apf__desc {
  font-size: 13px;
  color: #687589;
}
.apf__card {
  margin-bottom: 16px;
}
.apf__card-title {
  font-weight: 600;
}
.apf__form {
  margin-top: 16px;
  max-width: 520px;
}
</style>
