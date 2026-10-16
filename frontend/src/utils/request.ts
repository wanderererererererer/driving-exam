import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { clearAuth, getToken } from '@/utils/auth'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
})

/* ------------------------------- 请求拦截器 ------------------------------- */
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

/* ------------------------------- 响应拦截器 ------------------------------- */
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端统一返回 ApiResponse：{ code, message, data }
    if (res.code === 200) {
      // 解包，直接返回业务数据
      return res.data
    }
    // 业务失败（HTTP 200 + code !== 200）
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // 主动取消的请求（如组件卸载时 abort）不算错误：静默忽略，不弹提示
    if (axios.isCancel(error)) {
      return Promise.reject(error)
    }

    const status = error.response?.status
    if (status === 401) {
      // 未认证 / 登录失效：清 token 并跳转登录页
      clearAuth()
      ElMessage.error('登录已失效，请重新登录')
      if (router.currentRoute.value.path !== '/login') {
        router.push({
          path: '/login',
          query: { redirect: router.currentRoute.value.fullPath },
        })
      }
    } else if (status === 403) {
      ElMessage.error('无权限访问')
    } else {
      const msg = error.response?.data?.message || error.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

/**
 * 统一请求方法。
 * 拦截器已解包 ApiResponse.data，因此这里直接返回业务数据类型 T。
 */
export const http = {
  get<T = any>(url: string, params?: object, config?: AxiosRequestConfig) {
    return service.get<T, T>(url, { params, ...config })
  },
  post<T = any>(url: string, data?: object, config?: AxiosRequestConfig) {
    return service.post<T, T>(url, data, config)
  },
  put<T = any>(url: string, data?: object, config?: AxiosRequestConfig) {
    return service.put<T, T>(url, data, config)
  },
  delete<T = any>(url: string, config?: AxiosRequestConfig) {
    return service.delete<T, T>(url, config)
  },
}

export default service
