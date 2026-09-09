<script setup>
import { computed, onMounted, onUnmounted, ref, defineAsyncComponent } from 'vue'

// 动态导入 PDF 组件
const VuePdfEmbed = defineAsyncComponent({
  loader: () => import('vue-pdf-embed'),
  errorComponent: null,
  loadingComponent: null,
  delay: 200,
  timeout: 3000
})

const pdfPath = '/resume.pdf'
const hasLoadError = ref(false)
const oldTitle = document.title
const isMobile = ref(false)
const usePdfEmbed = ref(false)
const pdfLoading = ref(true)
const pdfScale = ref(2.0) // 移动端默认 2 倍渲染，提高清晰度

// 检测是否为移动设备
const checkMobile = () => {
  const ua = navigator.userAgent.toLowerCase()
  return /mobile|android|iphone|ipad|phone/i.test(ua) || window.innerWidth < 768
}

const pdfSrc = computed(() => `${pdfPath}#view=FitH`)
const pdfDownloadSrc = computed(() => pdfPath)

const handleLoadError = () => {
  hasLoadError.value = true
  pdfLoading.value = false
}

const handlePdfError = (error) => {
  console.error('PDF 加载失败:', error)
  usePdfEmbed.value = false
  pdfLoading.value = false
}

const handlePdfRendered = () => {
  pdfLoading.value = false
}

const openPdfInNewTab = () => {
  window.open(pdfPath, '_blank', 'noopener,noreferrer')
}

onMounted(async () => {
  document.title = '个人简历'
  isMobile.value = checkMobile()
  
  // 移动端使用 PDF 渲染组件
  if (isMobile.value) {
    // 移动端禁用缩放，确保 PDF 按设计尺寸显示
    const viewport = document.querySelector('meta[name="viewport"]')
    if (viewport) {
      viewport.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes')
    }
    
    try {
      const pdfjsLib = await import('pdfjs-dist')
      pdfjsLib.GlobalWorkerOptions.workerSrc = `//cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version}/pdf.worker.min.js`
      usePdfEmbed.value = true
      
      // 根据设备像素比调整渲染质量
      const dpr = window.devicePixelRatio || 1
      pdfScale.value = Math.max(2.5, dpr * 1.8)
    } catch (error) {
      console.warn('PDF.js 加载失败，使用 iframe 降级方案', error)
      usePdfEmbed.value = false
      pdfLoading.value = false
    }
  } else {
    pdfLoading.value = false
  }
  
  window.addEventListener('resize', () => {
    const wasMobile = isMobile.value
    isMobile.value = checkMobile()
    if (wasMobile !== isMobile.value) {
      usePdfEmbed.value = isMobile.value
    }
  })
})

onUnmounted(() => {
  document.title = oldTitle
  // 恢复 viewport
  const viewport = document.querySelector('meta[name="viewport"]')
  if (viewport) {
    viewport.setAttribute('content', 'width=device-width, initial-scale=1.0')
  }
})
</script>

<template>
  <div class="resume-viewer-page">
    <div class="ambient ambient-a" />
    <div class="ambient ambient-b" />

    <header class="topbar" v-if="!hasLoadError">
      <div class="brand">
        <span class="brand-dot" />
        <span class="brand-label">RESUME PREVIEW</span>
      </div>
      <div class="actions">
        <button class="action-btn ghost" @click="openPdfInNewTab">新窗口</button>
        <a class="action-btn solid" :href="pdfDownloadSrc" download>下载</a>
      </div>
    </header>

    <main class="viewer-shell" v-if="!hasLoadError">
      <!-- 移动端使用 vue-pdf-embed 渲染 -->
      <div v-if="usePdfEmbed" class="pdf-embed-container">
        <div v-if="pdfLoading" class="loading-indicator">
          <div class="spinner"></div>
          <p>正在加载简历...</p>
        </div>
        <VuePdfEmbed
          v-if="VuePdfEmbed"
          :source="pdfPath"
          :scale="pdfScale"
          class="pdf-embed"
          @rendering-failed="handlePdfError"
          @rendered="handlePdfRendered"
        />
      </div>
      <!-- 桌面端使用 iframe -->
      <iframe
        v-else
        class="resume-iframe"
        :src="pdfSrc"
        title="resume-pdf"
        @error="handleLoadError"
      />
    </main>

    <div v-else class="error-wrap">
      <h1>未找到简历文件</h1>
      <p>请将你的 PDF 放到 public 目录,并命名为 resume.pdf。</p>
      <p>
        当前预期地址：
        <a href="/resume.pdf" target="_blank" rel="noopener noreferrer">/resume.pdf</a>
      </p>
    </div>
  </div>
</template>

<style scoped>
.resume-viewer-page {
  position: relative;
  overflow: hidden;
  width: 100%;
  min-height: 100vh;
  padding: 18px;
  background: linear-gradient(140deg, #f4f8ff 0%, #ecf7f6 48%, #eaf4ff 100%);
  font-family: 'Avenir Next', 'DIN Alternate', 'Trebuchet MS', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.ambient {
  position: absolute;
  border-radius: 50%;
  filter: blur(44px);
  pointer-events: none;
}

.ambient-a {
  width: 340px;
  height: 340px;
  right: -80px;
  top: -70px;
  background: rgba(14, 165, 233, 0.24);
}

.ambient-b {
  width: 380px;
  height: 380px;
  left: -120px;
  bottom: -110px;
  background: rgba(45, 212, 191, 0.22);
}

.topbar {
  position: relative;
  z-index: 2;
  max-width: 1400px;
  margin: 0 auto 12px;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.brand-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: linear-gradient(120deg, #0ea5e9, #14b8a6);
  box-shadow: 0 0 0 5px rgba(20, 184, 166, 0.16);
}

.brand-label {
  letter-spacing: 0.14em;
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-btn {
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.action-btn:hover {
  transform: translateY(-1px);
}

.action-btn.ghost {
  background: #ffffff;
  color: #0f172a;
  border-color: #d7e0ea;
}

.action-btn.solid {
  color: #ffffff;
  background: linear-gradient(120deg, #0f172a, #1d4ed8);
  box-shadow: 0 10px 22px rgba(29, 78, 216, 0.28);
}

.viewer-shell {
  position: relative;
  z-index: 1;
  width: min(1400px, 100%);
  margin: 0 auto;
  height: calc(100vh - 18px * 2 - 34px - 12px - 24px);
  border-radius: 20px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  overflow: hidden;
  background: #0b1220;
  box-shadow:
    0 24px 60px rgba(15, 23, 42, 0.18),
    0 8px 20px rgba(15, 23, 42, 0.08);
}

.resume-iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
  background: #0b1220;
}

.pdf-embed-container {
  width: 100%;
  height: 100%;
  overflow: auto;
  background: #f8f9fa;
  -webkit-overflow-scrolling: touch;
}

.pdf-embed {
  width: 100%;
  height: auto;
  display: block;
}

/* 移动端 PDF 页面样式优化 */
.pdf-embed-container :deep(.vue-pdf-embed) {
  width: 100% !important;
}

.pdf-embed-container :deep(.vue-pdf-embed > div) {
  width: 100% !important;
  margin: 0 auto;
}

.pdf-embed-container :deep(canvas) {
  width: 100% !important;
  height: auto !important;
  display: block;
  margin-bottom: 1px;
}

.loading-indicator {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  gap: 16px;
  min-height: 200px;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid rgba(14, 165, 233, 0.2);
  border-top-color: #0ea5e9;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-indicator p {
  margin: 0;
  color: #0f172a;
  font-size: 14px;
}

.error-wrap {
  min-height: calc(100vh - 36px);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 8px;
  text-align: center;
  color: #0f172a;
  padding: 24px;
}

.error-wrap h1 {
  margin: 0;
  font-size: 28px;
}

.error-wrap p {
  margin: 0;
  color: #334155;
}

.error-wrap a {
  color: #1d4ed8;
}

@media (max-width: 768px) {
  .resume-viewer-page {
    padding: 0;
    background: #f8f9fa;
  }

  .ambient {
    display: none;
  }

  .topbar {
    margin: 0;
    border-radius: 0;
    padding: 8px 12px;
    border-left: none;
    border-right: none;
    border-top: none;
  }

  .brand-label {
    font-size: 11px;
  }

  .actions {
    gap: 6px;
  }

  .action-btn {
    height: 30px;
    padding: 0 10px;
    font-size: 12px;
  }

  .viewer-shell {
    width: 100%;
    height: calc(100vh - 47px);
    border-radius: 0;
    border: none;
    margin: 0;
    box-shadow: none;
    background: #f8f9fa;
  }

  .pdf-embed-container {
    padding: 0;
    background: #f8f9fa;
  }
}
</style>
