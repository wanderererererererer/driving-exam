import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken, getUserRaw } from '@/utils/auth'

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
        // 教练聊天：与教练端学员消息共用 views/common/ChatView.vue，role 决定接口前缀与文案
        path: 'chat',
        name: 'StudentChat',
        component: () => import('@/views/common/ChatView.vue'),
        props: { role: 'student' },
        meta: { title: '教练聊天' },
      },
      {
        // 个人中心：学员/教练/管理员共用，复用现有 auth 接口
        path: 'profile',
        name: 'StudentProfile',
        component: () => import('@/views/common/ProfileView.vue'),
        meta: { title: '个人中心' },
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
        // 逐题成绩报告：已结束考试可从答题页或考试记录进入
        path: 'exam/:examId/report',
        name: 'StudentExamReport',
        component: () => import('@/views/student/ExamReportView.vue'),
        meta: { title: '考试报告' },
      },
      {
        // 考试记录：按科目（subjectCode 在 query 上）查看历史考试
        path: 'exam-records',
        name: 'StudentExamRecords',
        component: () => import('@/views/student/ExamRecordsView.vue'),
        meta: { title: '考试记录' },
      },
      {
        // 预约（科目二/三共用；subjectCode 在 query 上，仅接受 SUBJECT_2 / SUBJECT_3，
        // 缺参或非法由页面提示并跳回首页）
        path: 'appointment',
        name: 'StudentAppointment',
        component: () => import('@/views/student/AppointmentView.vue'),
        meta: { title: '预约' },
      },
      // 历史占位路径兼容：旧版菜单曾指向 /student/subject2、/student/subject3
      { path: 'subject2', redirect: { path: '/student/appointment', query: { subjectCode: 'SUBJECT_2' } } },
      { path: 'subject3', redirect: { path: '/student/appointment', query: { subjectCode: 'SUBJECT_3' } } },
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
        // 预约管理：当前教练名下预约（后端无默认状态过滤，页面默认筛选 PENDING；
        // 后端不支持科目筛选）
        path: 'appointments',
        name: 'CoachAppointments',
        component: () => import('@/views/coach/AppointmentsView.vue'),
        meta: { title: '预约管理' },
      },
      {
        // 学员管理：当前教练名下有效绑定学员 + 四科学情（本阶段只读）
        path: 'students',
        name: 'CoachStudents',
        component: () => import('@/views/coach/StudentsView.vue'),
        meta: { title: '学员管理' },
      },
      {
        // 学员消息：与学员端教练聊天共用 views/common/ChatView.vue
        path: 'chat',
        name: 'CoachChat',
        component: () => import('@/views/common/ChatView.vue'),
        props: { role: 'coach' },
        meta: { title: '学员消息' },
      },
      {
        // 个人中心：学员/教练/管理员共用，复用现有 auth 接口
        path: 'profile',
        name: 'CoachProfile',
        component: () => import('@/views/common/ProfileView.vue'),
        meta: { title: '个人中心' },
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
        component: () => import('@/views/admin/DashboardView.vue'),
        meta: { title: '数据概览' },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UsersView.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'bindings',
        name: 'AdminBindings',
        component: () => import('@/views/admin/BindingsView.vue'),
        meta: { title: '绑定管理' },
      },
      {
        path: 'knowledge-points',
        name: 'AdminKnowledgePoints',
        component: () => import('@/views/admin/KnowledgePointsView.vue'),
        meta: { title: '知识点管理' },
      },
      {
        path: 'questions',
        name: 'AdminQuestions',
        component: () => import('@/views/admin/QuestionsView.vue'),
        meta: { title: '题库管理' },
      },
      {
        path: 'papers',
        name: 'AdminPapers',
        component: () => import('@/views/admin/PapersView.vue'),
        meta: { title: '试卷管理' },
      },
      {
        // 预约查看：管理员全量只读列表，不提供审批/拒绝/完成/取消
        path: 'appointments',
        name: 'AdminAppointments',
        component: () => import('@/views/admin/AppointmentsView.vue'),
        meta: { title: '预约查看' },
      },
      {
        path: 'ai-stats',
        name: 'AdminAiStats',
        component: () => import('@/views/admin/AiStatsView.vue'),
        meta: { title: 'AI 问答统计' },
      },
      {
        // 个人中心：复用现有 /api/auth/me、/profile、/password
        path: 'profile',
        name: 'AdminProfile',
        component: () => import('@/views/common/ProfileView.vue'),
        meta: { title: '个人中心' },
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

/* 全局前置守卫：未登录跳登录页，并按路由 meta.role 做前端角色边界 */
const ROLE_HOME: Record<string, string> = {
  STUDENT: '/student',
  COACH: '/coach',
  ADMIN: '/admin',
}

/**
 * 仅用于前端路由体验，不能替代后端授权。
 * localStorage 中的角色可能被篡改，真正的数据权限仍由 JWT + PathRoleRule 负责。
 */
function getStoredRole(): string | null {
  const raw = getUserRaw()
  if (!raw) return null
  try {
    const info = JSON.parse(raw) as { role?: unknown }
    return typeof info.role === 'string' && info.role ? info.role : null
  } catch {
    return null
  }
}

router.beforeEach((to) => {
  if (to.path === '/login') return true
  if (!getToken()) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const requiredRole = to.matched
    .map((record) => record.meta.role)
    .find((role): role is string => typeof role === 'string' && role.length > 0)

  if (!requiredRole) return true

  const currentRole = getStoredRole()
  if (!currentRole) {
    // Token 存在但本地角色信息缺失/损坏：不猜测角色，回到登录页重新建立登录态。
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (currentRole !== requiredRole) {
    // 已登录但角色不匹配：回到该角色自己的首页，避免停留在错误端页面外壳。
    const home = ROLE_HOME[currentRole]
    return home ? { path: home } : { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
