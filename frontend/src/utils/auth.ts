/**
 * 登录态本地存取（独立工具，避免 request 与 router 循环依赖）
 */

export const TOKEN_KEY = 'driving_exam_token'
export const USER_KEY = 'driving_exam_user'

export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export function getUserRaw(): string | null {
  return localStorage.getItem(USER_KEY)
}

export function setUserRaw(value: string): void {
  localStorage.setItem(USER_KEY, value)
}

export function removeUser(): void {
  localStorage.removeItem(USER_KEY)
}

/** 是否已登录 */
export function isLoggedIn(): boolean {
  return !!getToken()
}

/** 清空全部登录态 */
export function clearAuth(): void {
  removeToken()
  removeUser()
}
