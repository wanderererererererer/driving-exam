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

const displayName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.username || '学员',
)
const avatarText = computed(() => displayName.value.slice(0, 1))

/** 有独立「科目首页」的科目（科目二/三 是预约类，首页还没建） */
const SUBJECT_HOME_PATHS = ['/student/subject/SUBJECT_1', '/student/subject/SUBJECT_4']

/** 是否全屏路由（如模拟考试答题页）：不渲染侧边栏与顶栏 */
const isFullscreen = computed(() => route.meta.fullscreen === true)

/** 菜单高亮：刷题/错题/分析页通过 query 回映到科目首页 */
const activeMenu = computed(() => {
  if (route.path.startsWith('/student/subject/')) {
    return route.path
  }
  if (route.path === '/student/appointment') {
    const sc = String(route.query.subjectCode ?? '')
    if (sc === 'SUBJECT_2' || sc === 'SUBJECT_3') {
      return `/student/appointment?subjectCode=${sc}`
    }
    return route.path
  }
  const sc = String(route.query.subjectCode ?? '')
  if (sc) {
    const target = `/student/subject/${sc}`
    if (SUBJECT_HOME_PATHS.includes(target)) return target
  }
  return route.path
})

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
  <router-view v-if="isFullscreen" />

  <el-container v-else class="app-layout app-layout--student">
    <el-header class="app-header">
      <div class="app-brand">
        <span class="app-brand__mark">驾</span>
        <span>驾考智能辅导系统</span>
        <span class="app-brand__sub">学员端</span>
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
        <el-menu :default-active="activeMenu" router class="app-menu">
          <el-menu-item index="/student/dashboard">
            <el-icon><HomeFilled /></el-icon><span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/student/subject/SUBJECT_1">
            <el-icon><EditPen /></el-icon><span>科目一</span>
          </el-menu-item>
          <el-menu-item index="/student/appointment?subjectCode=SUBJECT_2">
            <el-icon><Van /></el-icon><span>科目二</span>
          </el-menu-item>
          <el-menu-item index="/student/appointment?subjectCode=SUBJECT_3">
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

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
