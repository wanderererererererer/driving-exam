import { http } from '@/utils/request'

/** 用户角色 */
export type UserRole = 'STUDENT' | 'COACH' | 'ADMIN'

/** 登录入参，字段对齐后端 LoginDTO */
export interface LoginParams {
  username: string
  password: string
  role: UserRole
}

/** 登录返回，字段对齐后端 LoginVO */
export interface LoginResult {
  token: string
  userId: number
  username: string
  realName?: string
  role: UserRole
}

/** 用户信息，字段对齐后端 UserVO（无 password） */
export interface UserInfoVO {
  id: number
  username: string
  realName?: string
  phone?: string
  avatar?: string
  role: UserRole
  status: number
}

/** 修改个人资料入参，字段对齐后端 ProfileUpdateDTO */
export interface ProfileUpdateParams {
  realName?: string
  phone?: string
  avatar?: string
}

/** 修改密码入参，字段对齐后端 PasswordUpdateDTO */
export interface PasswordUpdateParams {
  oldPwd: string
  newPwd: string
}

const BASE = '/auth'

/** 登录 */
export function loginApi(data: LoginParams) {
  return http.post<LoginResult>(`${BASE}/login`, data)
}

/** 当前登录用户 */
export function getMeApi() {
  return http.get<UserInfoVO>(`${BASE}/me`)
}

/** 修改个人资料 */
export function updateProfileApi(data: ProfileUpdateParams) {
  return http.put<void>(`${BASE}/profile`, data)
}

/** 修改密码 */
export function updatePasswordApi(data: PasswordUpdateParams) {
  return http.put<void>(`${BASE}/password`, data)
}
