// request.js
import axios from 'axios'
import { message } from 'ant-design-vue'
import router from '../router/router'
import { API_ERROR_CODES, normalizeBaseResponse } from './apiContract'

const getToken = () => {
  // 优先从 pinia-plugin-persistedstate 持久化的 userLogin store 中读取
  try {
    const userLogin = JSON.parse(localStorage.getItem('userLogin') || '{}')
    if (userLogin.token) return userLogin.token
  } catch {}
  // 兼容旧版 localStorage 存储
  const localToken = localStorage.getItem('token')
  if (localToken) return localToken
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    if (userInfo.token) return userInfo.token
  } catch {}
  return ''
}

const clearAuthStorage = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('userLogin')
}

// 创建 axios 实例
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api', // 适配环境变量
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
})

// 响应拦截器（核心：适配后端业务异常格式）
request.interceptors.response.use(
  (response) => {
    const res = normalizeBaseResponse(response.data)
    // 后端统一返回格式：{ code: 0, data: ..., description: '' }
    if (res.code !== API_ERROR_CODES.SUCCESS) {
      // 未登录/登录过期：清除本地凭证并跳转登录页
      // （必须在这里处理：fulfilled 回调中抛出的错误不会进入同一对拦截器的 rejected 回调）
      if (res.code === API_ERROR_CODES.NOT_LOGIN) {
        message.warning('未登录或登录已过期，请重新登录')
        clearAuthStorage()
        router.replace('/login')
      }
      // 业务异常：抛出包含业务信息的错误
      const error = new Error(res.description || '请求失败')
      error.isBusinessError = true // 标记为业务异常
      error.code = res.code        // 异常码
      error.description = res.description // 异常描述
      throw error
    }
    return res
  },
  (error) => {
    // HTTP 异常处理
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        message.warning('未登录或登录已过期，请重新登录')
        clearAuthStorage()
        router.replace('/login')
      } else if (status === 403) {
        message.error('无权限执行该操作')
      } else if (status === 404) {
        message.error('请求资源不存在')
      } else if (status === 500) {
        message.error('服务器内部错误，请稍后重试')
      }
    } else {
      // 网络异常
      message.error('网络异常，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

// 请求拦截器（添加 token）
request.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

export default request