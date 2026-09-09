/**
 * 前端性能监控工具
 * 采集 Web Vitals 指标：FCP、LCP、CLS、TTFB
 * 轻量实现，不依赖第三方 SDK
 */

/**
 * 采集 First Contentful Paint (FCP)
 * 首次内容绘制时间
 */
export function observeFCP(callback) {
  if (!window.PerformanceObserver) return
  const observer = new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (entry.name === 'first-contentful-paint') {
        callback({ name: 'FCP', value: Math.round(entry.startTime), rating: entry.startTime < 1800 ? 'good' : entry.startTime < 3000 ? 'needs-improvement' : 'poor' })
        observer.disconnect()
      }
    }
  })
  observer.observe({ type: 'paint', buffered: true })
}

/**
 * 采集 Largest Contentful Paint (LCP)
 * 最大内容绘制时间
 */
export function observeLCP(callback) {
  if (!window.PerformanceObserver) return
  const observer = new PerformanceObserver((list) => {
    const entries = list.getEntries()
    const lastEntry = entries[entries.length - 1]
    callback({ name: 'LCP', value: Math.round(lastEntry.startTime), rating: lastEntry.startTime < 2500 ? 'good' : lastEntry.startTime < 4000 ? 'needs-improvement' : 'poor' })
  })
  observer.observe({ type: 'largest-contentful-paint', buffered: true })
}

/**
 * 采集 Cumulative Layout Shift (CLS)
 * 累积布局偏移
 */
export function observeCLS(callback) {
  if (!window.PerformanceObserver) return
  let clsValue = 0
  const observer = new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (!entry.hadRecentInput) {
        clsValue += entry.value
      }
    }
    callback({ name: 'CLS', value: Math.round(clsValue * 1000) / 1000, rating: clsValue < 0.1 ? 'good' : clsValue < 0.25 ? 'needs-improvement' : 'poor' })
  })
  observer.observe({ type: 'layout-shift', buffered: true })
}

/**
 * 采集 Time to First Byte (TTFB)
 * 首字节时间
 */
export function observeTTFB(callback) {
  if (!window.PerformanceObserver) return
  const observer = new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (entry.entryType === 'navigation') {
        const ttfb = Math.round(entry.responseStart - entry.requestStart)
        callback({ name: 'TTFB', value: ttfb, rating: ttfb < 800 ? 'good' : ttfb < 1800 ? 'needs-improvement' : 'poor' })
        observer.disconnect()
      }
    }
  })
  observer.observe({ type: 'navigation', buffered: true })
}

/**
 * 采集页面加载时间
 */
export function getPageLoadMetrics() {
  if (!window.performance || !window.performance.timing) return null
  const timing = performance.timing
  return {
    // DNS 查询时间
    dns: timing.domainLookupEnd - timing.domainLookupStart,
    // TCP 连接时间
    tcp: timing.connectEnd - timing.connectStart,
    // 请求响应时间
    request: timing.responseEnd - timing.requestStart,
    // DOM 解析时间
    domParse: timing.domInteractive - timing.domLoading,
    // DOMContentLoaded 时间
    domContentLoaded: timing.domContentLoadedEventEnd - timing.navigationStart,
    // 页面完全加载时间
    load: timing.loadEventEnd - timing.navigationStart,
    // 首屏时间（近似）
    firstScreen: timing.domContentLoadedEventEnd - timing.navigationStart,
  }
}

/**
 * 初始化所有性能监控
 * @param {Function} onMetric - 指标回调 (metric) => void
 */
export function initPerformanceMonitoring(onMetric) {
  observeFCP(onMetric)
  observeLCP(onMetric)
  observeCLS(onMetric)
  observeTTFB(onMetric)

  // 页面加载完成后采集整体指标
  window.addEventListener('load', () => {
    setTimeout(() => {
      const metrics = getPageLoadMetrics()
      if (metrics) {
        onMetric({ name: 'PageLoad', value: metrics })
      }
    }, 0)
  })
}
