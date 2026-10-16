<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore, type UserRole } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  role: 'STUDENT' as UserRole,
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const ROLE_HOME: Record<UserRole, string> = {
  STUDENT: '/student',
  COACH: '/coach',
  ADMIN: '/admin',
}

// 进入登录页时与 localStorage 同步一次（例如 401 跳回后清理残留登录态）
onMounted(() => {
  userStore.restore()
})

const handleLogin = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({
      username: form.username,
      password: form.password,
      role: form.role,
    })
    ElMessage.success('登录成功')
    // 优先跳转 redirect，否则按角色进入对应端首页
    const redirect = (route.query.redirect as string) || ROLE_HOME[form.role]
    router.push(redirect)
  } catch {
    // 失败提示已由 request.ts 响应拦截器统一弹出，这里只负责停止 loading、不重复提示
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <el-card class="login__card">
      <template #header>
        <div class="login__title">驾考智能辅导系统</div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="70px"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="学员" value="STUDENT" />
            <el-option label="教练" value="COACH" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0eafc, #cfdef3);
}
.login__card {
  width: 380px;
}
.login__title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
}
</style>
