// store/userLogin.js
// 用户登录状态管理（使用 pinia-plugin-persistedstate 自动持久化）
import { defineStore } from 'pinia'
import { userLogin as apiLogin } from '../utils/api'
import { userLogout as apiLogout } from '../utils/api'
import { message } from 'ant-design-vue'
import { useNotificationStore } from './notification'
import { clearStarScope } from '../utils/starScope'

export const useUserStore = defineStore('userLogin', {
  state: () => ({
    id: null,
    userAccount: '',
    userRole: 0,       // 0=普通用户, 1=管理员
    userStatus: 0,     // 0=正常, 1=封禁
    isLogin: false,
    avatarUrl: '',
    username: '',
    phone: '',
    createTime: '',
    token: '',
  }),

  actions: {
    setUserInfo(userInfo) {
      this.id = userInfo.id
      this.userAccount = userInfo.userAccount
      this.userRole = userInfo.userRole
      this.userStatus = userInfo.userStatus
      this.isLogin = true
      this.avatarUrl = userInfo.avatarUrl
      this.username = userInfo.username
      this.phone = userInfo.phone
      this.createTime = userInfo.createTime
      this.token = userInfo.token || ''
    },

    async login(userAccount, userPassword, rememberMe = false) {
      try {
        const res = await apiLogin({ userAccount, userPassword, rememberMe })
        const userInfo = { ...res.data }
        if (!userInfo.token) {
          throw new Error('登录成功但未获取到 token')
        }
        this.setUserInfo(userInfo)
        message.success('登录成功')
        // 登录成功后建立 WebSocket 连接，接收实时通知
        try {
          useNotificationStore().connect()
        } catch (e) {
          console.warn('WebSocket 连接失败', e)
        }
        return true
      } catch (error) {
        console.error('登录失败', error)
        message.error(error?.description || error?.message || '登录异常，请重试')
        return false
      }
    },

    async logout() {
      try {
        await apiLogout()
        message.success('退出登录成功')
      } catch (error) {
        console.error('退出登录接口调用失败', error)
        message.warning('后端退出失败，但已清除本地状态')
      } finally {
        // 登出时断开 WebSocket，避免登出后仍接收推送
        try {
          useNotificationStore().disconnect()
        } catch (e) { /* 忽略 */ }
        // 登出时清除星球作用域：避免下一个登录用户继承上一个用户的星球上下文
        clearStarScope()
        // $reset() 会重置 state 到初始值，persist 插件会同步清除 localStorage
        this.$reset()
      }
    }
  },

  // pinia-plugin-persistedstate 自动持久化配置
  // 替代手动 localStorage.setItem/getItem
  persist: {
    key: 'userLogin',
    storage: localStorage,
    pick: ['id', 'userAccount', 'userRole', 'userStatus', 'isLogin', 'avatarUrl', 'username', 'phone', 'createTime', 'token']
  }
})
