<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  HomeFilled,
  EditPen,
  Van,
  ChatDotRound,
  ChatLineRound,
  User,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 有独立「科目首页」的科目（科目二/三 是预约类，首页还没建） */
const SUBJECT_HOME_PATHS = ['/student/subject/SUBJECT_1', '/student/subject/SUBJECT_4']

/** 是否全屏路由（如模拟考试答题页）：不渲染侧边栏与顶栏，只留页面内的唯一退出入口 */
const isFullscreen = computed(() => route.meta.fullscreen === true)

/**
 * 菜单高亮。
 *
 * <p>刷题页 / 错题本页的路径里不含科目（科目在 query 的 subjectCode 上），
 * 若不处理，进入刷题页后侧边菜单会"失去高亮"。这里把它们映射回对应科目的菜单项。</p>
 */
const activeMenu = computed(() => {
  if (route.path.startsWith('/student/subject/')) {
    return route.path
  }
  const sc = String(route.query.subjectCode ?? '')
  if (sc) {
    const target = `/student/subject/${sc}`
    if (SUBJECT_HOME_PATHS.includes(target)) return target
  }
  return route.path
})

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
  <!-- 全屏路由（模拟考试答题页）：隐藏侧边栏与顶栏，避免考试期间跳转到其他模块 -->
  <router-view v-if="isFullscreen" />

  <el-container v-else class="layout">
    <el-header class="layout__header">
      <div class="layout__brand">驾考智能辅导系统 · 学员端</div>
      <div class="layout__user">
        <span class="layout__name">
          {{ userStore.userInfo?.realName || userStore.userInfo?.username || '未登录' }}
        </span>
        <el-button link type="primary" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container class="layout__body">
      <el-aside width="210px" class="layout__aside">
        <el-menu :default-active="activeMenu" router class="layout__menu">
          <el-menu-item index="/student/dashboard">
            <el-icon><HomeFilled /></el-icon><span>首页</span>
          </el-menu-item>
          <el-menu-item index="/student/subject/SUBJECT_1">
            <el-icon><EditPen /></el-icon><span>科目一</span>
          </el-menu-item>
          <!-- 科目二/三 是预约类，独立首页待建；暂落到占位页 -->
          <el-menu-item index="/student/subject2">
            <el-icon><Van /></el-icon><span>科目二</span>
          </el-menu-item>
          <el-menu-item index="/student/subject3">
            <el-icon><Van /></el-icon><span>科目三</span>
          </el-menu-item>
          <el-menu-item index="/student/subject/SUBJECT_4">
            <el-icon><EditPen /></el-icon><span>科目四</span>
          </el-menu-item>
          <el-menu-item index="/student/ai">
            <el-icon><ChatDotRound /></el-icon><span>智能问答</span>
          </el-menu-item>
          <el-menu-item index="/student/chat">
            <el-icon><ChatLineRound /></el-icon><span>教练聊天</span>
          </el-menu-item>
          <el-menu-item index="/student/profile">
            <el-icon><User /></el-icon><span>个人中心</span>
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
