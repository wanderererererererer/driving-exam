<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, ArrowRight } from '@element-plus/icons-vue'
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

const roleOptions: { label: string; value: UserRole; desc: string }[] = [
  { label: '学员', value: 'STUDENT', desc: '刷题 · 模考 · 预约' },
  { label: '教练', value: 'COACH', desc: '学员 · 预约 · 消息' },
  { label: '管理员', value: 'ADMIN', desc: '用户 · 题库 · 运营' },
]

onMounted(() => {
  userStore.restore()
})

const handleLogin = async (): Promise<void> => {
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
    const redirect = (route.query.redirect as string) || ROLE_HOME[form.role]
    router.push(redirect)
  } catch {
    // 错误提示由 request.ts 统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="login__panel">
      <div class="login__intro">
        <div class="login__brand">
          <span class="login__brand-mark">驾</span>
          <span>驾考智能辅导系统</span>
        </div>
        <h1 class="login__headline">让每一次练习，<br />都更接近通过。</h1>
        <p class="login__sub">
          面向学员、教练与管理员的一体化驾培学习与运营平台。
          学情分析、错题复习、模拟考试和智能问答，都在一个清晰的工作台里。
        </p>
        <div class="login__points">
          <span>真实学情</span>
          <span>模拟考试</span>
          <span>教练协同</span>
          <span>AI 辅助</span>
        </div>
      </div>

      <el-card class="login__card" shadow="never">
        <div class="login__card-head">
          <h2>欢迎回来</h2>
          <p>选择身份并登录你的工作台</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @keyup.enter="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              size="large"
              placeholder="请输入密码"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="登录身份" prop="role">
            <div class="login__roles">
              <button
                v-for="option in roleOptions"
                :key="option.value"
                type="button"
                class="login__role"
                :class="{ 'is-active': form.role === option.value }"
                @click="form.role = option.value"
              >
                <span class="login__role-name">{{ option.label }}</span>
                <span class="login__role-desc">{{ option.desc }}</span>
              </button>
            </div>
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login__submit"
            :loading="loading"
            @click="handleLogin"
          >
            进入工作台
            <el-icon class="login__submit-icon"><ArrowRight /></el-icon>
          </el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    radial-gradient(circle at 12% 18%, rgba(117, 151, 213, 0.14), transparent 32%),
    radial-gradient(circle at 88% 82%, rgba(123, 134, 199, 0.1), transparent 30%),
    var(--app-bg);
}

.login__panel {
  width: min(1060px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 64px;
  align-items: center;
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 650;
}
.login__brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--app-primary-soft);
  color: var(--app-primary-strong);
  font-weight: 700;
}
.login__headline {
  margin: 46px 0 18px;
  color: var(--app-text);
  font-size: clamp(30px, 3.4vw, 44px);
  line-height: 1.22;
  font-weight: 650;
  letter-spacing: -0.03em;
}
.login__sub {
  max-width: 520px;
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 15px;
  line-height: 1.9;
}
.login__points {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 28px;
}
.login__points span {
  padding: 5px 11px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.65);
  color: var(--app-text-regular);
  font-size: 12px;
}

.login__card {
  border-radius: 18px !important;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 50px rgba(38, 52, 73, 0.1) !important;
}
.login__card-head {
  margin-bottom: 24px;
}
.login__card-head h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
}
.login__card-head p {
  margin: 7px 0 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.login__roles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
}
.login__role {
  min-height: 58px;
  padding: 9px 8px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface);
  color: var(--app-text-regular);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;
  font-family: inherit;
}
.login__role:hover {
  border-color: #b7c8e1;
  background: var(--app-primary-faint);
}
.login__role.is-active {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
  color: var(--app-text);
  box-shadow: 0 0 0 1px var(--app-primary) inset;
}
.login__role-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
}
.login__role-desc {
  display: block;
  margin-top: 3px;
  color: var(--app-text-secondary);
  font-size: 10px;
  line-height: 1.35;
}

.login__submit {
  width: 100%;
  margin-top: 4px;
}
.login__submit-icon {
  margin-left: 6px;
}

@media (max-width: 900px) {
  .login__panel {
    grid-template-columns: 1fr;
    gap: 32px;
    max-width: 440px;
  }
  .login__intro {
    display: none;
  }
  .login {
    padding: 20px;
  }
}
</style>
