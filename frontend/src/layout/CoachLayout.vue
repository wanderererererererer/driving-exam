<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { HomeFilled, User, Calendar, ChatDotRound, Setting } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    .then(() => {
      userStore.logout()
      router.push('/login')
    })
    .catch(() => {})
}
</script>

<template>
  <el-container class="layout">
    <el-header class="layout__header">
      <div class="layout__brand">驾考智能辅导系统 · 教练端</div>
      <div class="layout__user">
        <span class="layout__name">
          {{ userStore.userInfo?.realName || userStore.userInfo?.username || '未登录' }}
        </span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container class="layout__body">
      <el-aside width="210px" class="layout__aside">
        <el-menu :default-active="route.path" router class="layout__menu">
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

      <el-main class="layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}
.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.layout__brand {
  font-size: 18px;
  font-weight: 600;
}
.layout__user {
  display: flex;
  align-items: center;
  gap: 12px;
}
.layout__name {
  color: #606266;
}
.layout__body {
  height: calc(100vh - 60px);
}
.layout__aside {
  background: #fff;
  border-right: 1px solid #e4e7ed;
}
.layout__menu {
  border-right: none;
}
.layout__main {
  background: #f5f7fa;
}
</style>
