// store/notification.js
// 通知状态管理（未读数、WebSocket 实时推送）
import { defineStore } from 'pinia'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getUnreadNotificationCount } from '../utils/api'

export const useNotificationStore = defineStore('notification', {
  state: () => ({
    /** 未读通知数量 */
    unreadCount: 0,
    /** WebSocket 连接状态 */
    wsConnected: false,
    /** STOMP 客户端实例（store 内单例，避免重复连接） */
    stompClient: null,
  }),

  actions: {
    /** 从后端拉取未读数 */
    async fetchUnreadCount() {
      try {
        const res = await getUnreadNotificationCount()
        this.unreadCount = res.data || 0
      } catch (e) {
        console.warn('获取未读通知数失败', e)
      }
    },

    /** WebSocket 推送时递增未读数 */
    incrementUnread() {
      this.unreadCount++
    },

    /** 标记全部已读后重置 */
    resetUnread() {
      this.unreadCount = 0
    },

    /** 设置 WebSocket 连接状态 */
    setWsConnected(connected) {
      this.wsConnected = connected
    },

    /**
     * 建立 WebSocket 连接（幂等：已连接则不重复建立）
     * STOMP 连接时携带 JWT，后端校验通过后把连接绑定到当前用户；
     * 订阅 /user/queue/notification（Spring 会按 principal 解析成本人的队列），
     * 收到推送即递增未读数。
     */
    connect() {
      if (this.stompClient?.active || this.wsConnected) return
      if (typeof window === 'undefined') return

      // 从持久化的 userLogin 读取 token
      let token = ''
      try {
        const userLogin = JSON.parse(localStorage.getItem('userLogin') || '{}')
        token = userLogin.token || localStorage.getItem('token') || ''
      } catch { /* 忽略解析失败 */ }

      if (!token) return

      const client = new Client({
        webSocketFactory: () => new SockJS('/api/ws'),
        connectHeaders: { Authorization: `Bearer ${token}` },
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          // 订阅用户专属通知队列：/user/queue/notification
          client.subscribe('/user/queue/notification', () => {
            this.incrementUnread()
          })
          this.setWsConnected(true)
          // 连接成功时校准一次未读数（兜底离线期间遗漏的推送）
          this.fetchUnreadCount()
        },
        onStompError: (frame) => {
          console.warn('WebSocket STOMP 错误', frame?.headers?.message || frame?.body)
          this.setWsConnected(false)
        },
        onWebSocketClose: () => {
          this.setWsConnected(false)
        },
      })
      this.stompClient = client
      client.activate()
    },

    /** 断开连接（登出时调用，幂等） */
    disconnect() {
      if (this.stompClient) {
        try {
          this.stompClient.deactivate()
        } catch (e) {
          console.warn('WebSocket 断开失败', e)
        }
        this.stompClient = null
      }
      this.setWsConnected(false)
    },
  },
})
