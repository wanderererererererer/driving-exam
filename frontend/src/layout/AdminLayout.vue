<script setup lang="ts">
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
      <div class="layout__brand">驾考智能辅导系统 · 管理端</div>
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
          <el-menu-item index="/admin/dashboard">
            <el-icon><DataLine /></el-icon><span>数据概览</span>
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
