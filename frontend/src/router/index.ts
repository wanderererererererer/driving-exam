import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/auth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' },
  },

  /* ------------------------------- 学员端 ------------------------------- */
  {
    path: '/student',
    component: () => import('@/layout/StudentLayout.vue'),
    redirect: '/student/dashboard',
    meta: { role: 'STUDENT' },
    children: [
      {
        path: 'dashboard',
        name: 'StudentDashboard',
        component: () => import('@/views/student/Dashboard.vue'),
        meta: { title: '学员首页' },
      },
      {
        // 科目首页：科目一 / 科目四的练习入口
        path: 'subject/:subjectCode',
        name: 'StudentSubjectHome',
        component: () => import('@/views/student/SubjectHomeView.vue'),
        meta: { title: '科目首页' },
      },
      {
        // 刷题页：模式由 query 的 mode 决定（SEQUENCE / CHAPTER / RANDOM / RETRY）
        path: 'study',
        name: 'StudentStudy',
        component: () => import('@/views/student/StudyView.vue'),
        meta: { title: '刷题' },
      },
      {
        // 错题本：按科目（subjectCode 在 query 上）
        path: 'wrong-book',
        name: 'StudentWrongBook',
        component: () => import('@/views/student/WrongBookView.vue'),
        meta: { title: '错题本' },
      },
      {
        // 智能问答：可选 query subjectCode（从科目首页跳入时带上下文）
        path: 'ai',
        name: 'StudentAiChat',
        component: () => import('@/views/student/AiChatView.vue'),
        meta: { title: '智能问答' },
      },
      {
        // 学情分析：按科目（subjectCode 在 query 上）
        path: 'analysis',
        name: 'StudentAnalysis',
        component: () => import('@/views/student/AnalysisView.vue'),
        meta: { title: '学情分析' },
      },
      {
        // 模拟考试：试卷列表 + 开始考试入口（科目来自 query subjectCode；由科目一/四页面进入）
        path: 'exam',
        name: 'StudentExam',
        component: () => import('@/views/student/ExamView.vue'),
        meta: { title: '模拟考试' },
      },
      {
        // 模拟考试答题页：按 examId 恢复并作答（答题页第一阶段；不含交卷/报告）
        // fullscreen：答题期间隐藏学生端侧边栏与顶栏，页内只保留唯一「退出考试」入口
        path: 'exam/:examId',
        name: 'StudentExamAnswer',
        component: () => import('@/views/student/ExamAnswerView.vue'),
        meta: { title: '模拟考试答题', fullscreen: true },
      },
      {
        // 考试记录：按科目（subjectCode 在 query 上）查看历史考试
        path: 'exam-records',
        name: 'StudentExamRecords',
        component: () => import('@/views/student/ExamRecordsView.vue'),
        meta: { title: '考试记录' },
      },
      // 其余菜单项暂落到占位页
      {
        path: ':pathMatch(.*)*',
        name: 'StudentComingSoon',
        component: () => import('@/views/common/ComingSoon.vue'),
      },
    ],
  },

  /* ------------------------------- 教练端 ------------------------------- */
  {
    path: '/coach',
    component: () => import('@/layout/CoachLayout.vue'),
    redirect: '/coach/dashboard',
    meta: { role: 'COACH' },
    children: [
      {
        path: 'dashboard',
        name: 'CoachDashboard',
        component: () => import('@/views/coach/Dashboard.vue'),
        meta: { title: '教练工作台' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'CoachComingSoon',
        component: () => import('@/views/common/ComingSoon.vue'),
      },
    ],
  },

  /* ------------------------------ 管理员端 ------------------------------ */
  {
    path: '/admin',
    component: () => import('@/layout/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { role: 'ADMIN' },
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '数据概览' },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'AdminComingSoon',
        component: () => import('@/views/common/ComingSoon.vue'),
      },
    ],
  },

  /* -------------------------------- 404 -------------------------------- */
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

/* 全局前置守卫：未登录跳登录页 */
router.beforeEach((to) => {
  if (to.path === '/login') return true
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
