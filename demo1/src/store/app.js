// store/app.js
// 应用级全局状态（主题、侧栏、全局 Loading）
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    /** 侧栏是否折叠 */
    sidebarCollapsed: false,
    /** 是否暗黑模式 */
    darkMode: false,
    /** 全局 Loading 计数（>0 显示 loading） */
    loadingCount: 0,
  }),

  getters: {
    /** 是否显示全局 loading */
    isLoading: (state) => state.loadingCount > 0,
  },

  actions: {
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    },

    toggleDarkMode() {
      this.darkMode = !this.darkMode
      // 同步到 document class
      document.documentElement.classList.toggle('dark', this.darkMode)
    },

    /** 开始 loading（支持并发嵌套） */
    startLoading() {
      this.loadingCount++
    },

    /** 结束 loading */
    stopLoading() {
      if (this.loadingCount > 0) {
        this.loadingCount--
      }
    },
  },

  persist: {
    key: 'appState',
    storage: localStorage,
    pick: ['sidebarCollapsed', 'darkMode']
  }
})
