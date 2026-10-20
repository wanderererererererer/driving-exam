<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { HomeFilled, User, Calendar, ChatDotRound, Setting } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '教练',
)
const avatarText = computed(() => displayName.value.slice(0, 1))

const handleLogout = (): void => {
  ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消',
  })
    .then(() => {
      userStore.logout()
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<template>
  <el-container class="app-layout app-layout--coach">
    <el-header class="app-header">
      <div class="app-brand">
        <span class="app-brand__mark">驾</span>
        <span>驾考智能辅导系统</span>
        <span class="app-brand__sub">教练端</span>
      </div>
      <div class="app-header__right">
        <div class="app-user">
          <span class="app-user__avatar">{{ avatarText }}</span>
          <span>{{ displayName }}</span>
        </div>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container class="app-body">
      <el-aside class="app-aside">
        <el-menu :default-active="route.path" router class="app-menu">
          <el-menu-item index="/coach/dashboard">
            <el-icon><HomeFilled /></el-icon><span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/coach/students">
            <el-icon><User /></el-icon><span>学员管理</span>
          </el-menu-item>
          <el-menu-item index="/coach/appointments">
            <el-icon><Calendar /></el-icon><span>预约管理</span>
          </el-menu-item>
          <el-menu-item index="/coach/chat">
            <el-icon><ChatDotRound /></el-icon><span>学员消息</span>
          </el-menu-item>
          <el-menu-item index="/coach/profile">
            <el-icon><Setting /></el-icon><span>个人中心</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
