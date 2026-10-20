<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  DataLine,
  User,
  Link,
  Collection,
  Document,
  Tickets,
  Calendar,
  ChatDotRound,
  Setting,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '管理员',
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
  <el-container class="app-layout app-layout--admin">
    <el-header class="app-header">
      <div class="app-brand">
        <span class="app-brand__mark">驾</span>
        <span>驾考智能辅导系统</span>
        <span class="app-brand__sub">管理端</span>
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
          <el-menu-item index="/admin/dashboard">
            <el-icon><DataLine /></el-icon><span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon><span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/bindings">
            <el-icon><Link /></el-icon><span>绑定管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/knowledge-points">
            <el-icon><Collection /></el-icon><span>知识点管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/questions">
            <el-icon><Document /></el-icon><span>题库管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/papers">
            <el-icon><Tickets /></el-icon><span>试卷管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/appointments">
            <el-icon><Calendar /></el-icon><span>预约查看</span>
          </el-menu-item>
          <el-menu-item index="/admin/ai-stats">
            <el-icon><ChatDotRound /></el-icon><span>AI 问答统计</span>
          </el-menu-item>
          <el-menu-item index="/admin/profile">
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
