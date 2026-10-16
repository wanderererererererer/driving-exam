import { defineStore } from 'pinia'
import { loginApi, type LoginParams, type UserRole } from '@/api/auth'
import {
  USER_KEY,
  getToken,
  setToken as persistToken,
  removeToken,
  removeUser,
} from '@/utils/auth'

// 角色类型统一从 api 层引入并 re-export，避免重复定义与 api ↔ store 循环引用
export type { UserRole }

/** 本地存储的登录用户信息 */
export interface UserInfo {
  userId?: number
  username?: string
  realName?: string
  role?: UserRole
}

function readUser(): UserInfo | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    // 初始即从 localStorage 恢复，刷新页面保持登录
    token: getToken(),
    userInfo: readUser() as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    currentRole: (state) => state.userInfo?.role || ('' as UserRole | ''),
  },
  actions: {
    /**
     * 登录：成功后写入 token 与用户信息。
     * 失败时直接抛出异常，由调用方决定后续处理（错误提示由 request 拦截器统一弹出）。
     */
    async login(params: LoginParams) {
      const res = await loginApi(params)
      this.setToken(res.token)
      this.setUserInfo({
        userId: res.userId,
        username: res.username,
        realName: res.realName,
        role: res.role,
      })
      return res
    },

    setToken(token: string) {
      this.token = token
      persistToken(token)
    },

    setUserInfo(user: UserInfo) {
      this.userInfo = user
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    },

    /** 从 localStorage 恢复登录态（用于 401 跳回登录页后同步 state） */
    restore() {
      this.token = getToken()
      this.userInfo = readUser()
    },

    /** 退出登录：清空 state 与 localStorage */
    logout() {
      this.token = ''
      this.userInfo = null
      removeToken()
      removeUser()
    },
  },
})
