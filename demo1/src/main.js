import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import { createPinia } from 'pinia'
import piniaPersistedstate from 'pinia-plugin-persistedstate'
import Antd from 'ant-design-vue'
import router from './router/router'
import { initErrorTracker, createVueErrorHandler } from './utils/errorTracker'
import { initPerformanceMonitoring } from './utils/performance'

import 'ant-design-vue/dist/reset.css'

// ── Pinia 初始化（含持久化插件） ──
const pinia = createPinia()
pinia.use(piniaPersistedstate)

const app = createApp(App)

app.use(pinia)
app.use(Antd)
app.use(router)

// ── Vue 全局错误处理 ──
app.config.errorHandler = createVueErrorHandler(() => {
  try {
    const info = JSON.parse(localStorage.getItem('userLogin') || '{}')
    return info.id || null
  } catch { return null }
})

// ── 全局 JS 错误 + 性能监控 ──
initErrorTracker({
  getUserId: () => {
    try {
      const info = JSON.parse(localStorage.getItem('userLogin') || '{}')
      return info.id || null
    } catch { return null }
  }
})

initPerformanceMonitoring((metric) => {
  // 性能指标仅在开发环境打印，生产环境可扩展为上报
  if (import.meta.env.DEV) {
    console.log(`[Perf] ${metric.name}:`, metric.value)
  }
})

app.mount('#app')
