/**
 * 前端错误追踪工具
 * 捕获 Vue 组件错误、全局 JS 错误、Promise 异常、资源加载错误
 * 异步上报到后端，不阻塞用户操作
 */

import axios from 'axios'

/** 错误上报队列（批量发送，减少请求次数） */
let errorQueue = []
let flushTimer = null

/**
 * 上报错误到后端
 * @param {Object} errorInfo - 错误信息
 */
function reportError(errorInfo) {
  errorQueue.push({
    ...errorInfo,
    timestamp: Date.now(),
    url: window.location.href,
    userAgent: navigator.userAgent,
  })

  // 每 5 秒批量发送一次
  if (!flushTimer) {
    flushTimer = setTimeout(flushErrors, 5000)
  }

  // 队列超过 10 条立即发送
  if (errorQueue.length >= 10) {
    flushErrors()
  }
}

/**
 * 批量发送错误日志
 */
function flushErrors() {
  if (errorQueue.length === 0) return

  const errors = [...errorQueue]
  errorQueue = []
  clearTimeout(flushTimer)
  flushTimer = null

  // 使用 sendBeacon 优先（页面关闭时不丢失），降级为 fetch
  const payload = JSON.stringify({ errors })
  const url = '/api/error/report'

  if (navigator.sendBeacon) {
    const blob = new Blob([payload], { type: 'application/json' })
    navigator.sendBeacon(url, blob)
  } else {
    axios.post(url, { errors }).catch(() => {
      // 上报失败静默处理，不抛出异常
    })
  }
}

/**
 * 初始化全局错误监控
 * @param {Object} options
 * @param {Function} options.getUserId - 获取当前用户 ID 的函数
 */
export function initErrorTracker(options = {}) {
  // 1. 捕获全局 JS 错误
  window.onerror = (message, source, lineno, colno, error) => {
    reportError({
      type: 'js-error',
      message: String(message),
      source: source || '',
      lineno: lineno || 0,
      colno: colno || 0,
      stack: error?.stack || '',
      userId: options.getUserId?.() || null,
    })
  }

  // 2. 捕获未处理的 Promise 异常
  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason
    reportError({
      type: 'unhandled-rejection',
      message: reason?.message || String(reason),
      stack: reason?.stack || '',
      userId: options.getUserId?.() || null,
    })
  })

  // 3. 捕获资源加载错误（图片、脚本、CSS 等）
  window.addEventListener('error', (event) => {
    if (event.target && (event.target.tagName === 'IMG' || event.target.tagName === 'SCRIPT' || event.target.tagName === 'LINK')) {
      reportError({
        type: 'resource-error',
        message: `资源加载失败: ${event.target.src || event.target.href}`,
        tagName: event.target.tagName,
        userId: options.getUserId?.() || null,
      })
    }
  }, true)

  // 页面关闭前发送剩余错误
  window.addEventListener('beforeunload', flushErrors)
}

/**
 * 创建 Vue 全局错误处理器
 * 用于 app.config.errorHandler
 * @param {Function} getUserId - 获取当前用户 ID
 */
export function createVueErrorHandler(getUserId) {
  return (err, vm, info) => {
    reportError({
      type: 'vue-error',
      message: err?.message || String(err),
      stack: err?.stack || '',
      componentInfo: info,
      componentName: vm?.$options?.name || vm?.$options?._componentTag || 'unknown',
      userId: getUserId?.() || null,
    })
  }
}

/**
 * 手动上报错误（供业务代码主动调用）
 * @param {Error|string} error - 错误对象或消息
 * @param {Object} extra - 额外信息
 */
export function trackError(error, extra = {}) {
  reportError({
    type: 'manual',
    message: error?.message || String(error),
    stack: error?.stack || '',
    ...extra,
  })
}
