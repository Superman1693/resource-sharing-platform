<template>
  <div ref="wrapperRef" class="vditor-editor-wrapper" :class="{ 'is-fullscreen': isFullscreen }">
    <!-- 全屏切换按钮 -->
    <button class="fullscreen-toggle" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏编辑'">
      <FullscreenExitOutlined v-if="isFullscreen" />
      <FullscreenOutlined v-else />
    </button>

    <!-- Vditor 编辑器容器 -->
    <div ref="vditorRef" class="vditor-container"></div>

    <!-- 底部状态栏 -->
    <div class="editor-statusbar">
      <span class="status-item">{{ wordCount }} 字</span>
      <span class="status-item">约 {{ readingTime }} 分钟阅读</span>
      <span class="status-item" v-if="uploading">图片上传中...</span>
      <span class="status-spacer" />
      <span class="status-item">Vditor</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { FullscreenOutlined, FullscreenExitOutlined } from '@ant-design/icons-vue'
import { message as antMessage } from 'ant-design-vue'
import { uploadImage } from '../utils/api'
import { useUserStore } from '../store/userLogin'
import Vditor from 'vditor'
import 'vditor/dist/index.css'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '支持 Markdown 格式，请输入内容...'
  },
  height: {
    type: Number,
    default: 500
  }
})

const emit = defineEmits(['update:modelValue', 'htmlChanged', 'wordCount'])

const vditorRef = ref(null)
const isFullscreen = ref(false)
const uploading = ref(false)
let vditorInstance = null

// 字数统计
const wordCount = computed(() => {
  const text = props.modelValue || ''
  return text.replace(/\s/g, '').length
})

const readingTime = computed(() => {
  const mins = Math.ceil(wordCount.value / 400)
  return mins < 1 ? '< 1' : mins
})

// 切换全屏（使用浏览器原生 Fullscreen API，彻底解决层级问题）
const wrapperRef = ref(null)

const toggleFullscreen = async () => {
  const wrapper = wrapperRef.value
  if (!wrapper) return

  try {
    if (!document.fullscreenElement) {
      await wrapper.requestFullscreen()
    } else {
      await document.exitFullscreen()
    }
  } catch (err) {
    // 降级：用 CSS 模拟全屏
    isFullscreen.value = !isFullscreen.value
    nextTick(() => {
      if (vditorInstance) vditorInstance.resize()
    })
  }
}

// 监听全屏状态变化（包括 ESC 退出全屏的情况）
const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
  nextTick(() => {
    if (vditorInstance) vditorInstance.resize()
  })
}

// 初始化 Vditor
const initVditor = () => {
  if (!vditorRef.value) return

  vditorInstance = new Vditor(vditorRef.value, {
    // 编辑器高度
    height: props.height,
    // 模式：ir 即时渲染（输入 Markdown 语法立即渲染，不显示 ** 等源码符）
    mode: 'ir',
    // 主题
    theme: 'classic',
    // 编辑器主题
    toolbarConfig: {
      // 隐藏内置全屏按钮（使用自定义的）
      hide: false,
      pin: true
    },
    // 工具栏配置（带中文提示）
    toolbar: [
      {
        name: 'emoji',
        tipPosition: 's',
        tip: '表情'
      },
      {
        name: 'headings',
        tipPosition: 's',
        tip: '标题'
      },
      {
        name: 'bold',
        tipPosition: 's',
        tip: '粗体 (Ctrl+B)'
      },
      {
        name: 'italic',
        tipPosition: 's',
        tip: '斜体 (Ctrl+I)'
      },
      {
        name: 'strike',
        tipPosition: 's',
        tip: '删除线 (Ctrl+D)'
      },
      '|',
      {
        name: 'line',
        tipPosition: 's',
        tip: '分割线'
      },
      {
        name: 'quote',
        tipPosition: 's',
        tip: '引用'
      },
      {
        name: 'list',
        tipPosition: 's',
        tip: '无序列表'
      },
      {
        name: 'ordered-list',
        tipPosition: 's',
        tip: '有序列表'
      },
      {
        name: 'check',
        tipPosition: 's',
        tip: '任务列表'
      },
      '|',
      {
        name: 'code',
        tipPosition: 's',
        tip: '代码块'
      },
      {
        name: 'inline-code',
        tipPosition: 's',
        tip: '行内代码'
      },
      {
        name: 'table',
        tipPosition: 's',
        tip: '表格'
      },
      '|',
      {
        name: 'link',
        tipPosition: 's',
        tip: '链接 (Ctrl+K)'
      },
      {
        name: 'upload-image',
        tipPosition: 's',
        tip: '上传图片',
        className: 'right',
        icon: '<svg viewBox="0 0 1024 1024"><path d="M959.877 128l0.123 0.123v767.775l-0.123 0.122H64.102l-0.122-0.122V128.123L64.102 128h895.775zM960 64H64C28.795 64 0 92.795 0 128v768c0 35.205 28.795 64 64 64h896c35.205 0 64-28.795 64-64V128c0-35.205-28.795-64-64-64zM832 288c0 53.024-42.976 96-96 96s-96-42.976-96-96 42.976-96 96-96 96 42.976 96 96zM448 896h128v128H448v-128z"></path></svg>'
      },
      {
        name: 'import-markdown',
        tipPosition: 's',
        tip: '导入 Markdown 文件',
        className: 'right',
        icon: '<svg viewBox="0 0 1024 1024"><path d="M832 128H192c-35.2 0-64 28.8-64 64v640c0 35.2 28.8 64 64 64h640c35.2 0 64-28.8 64-64V192c0-35.2-28.8-64-64-64zm-64 704H256V192h512v640zM384 448h256v64H384zm0 128h256v64H384zm0-256h256v64H384z"></path></svg>',
        click: () => triggerImportMarkdown()
      },
      '|',
      {
        name: 'undo',
        tipPosition: 's',
        tip: '撤销 (Ctrl+Z)'
      },
      {
        name: 'redo',
        tipPosition: 's',
        tip: '重做 (Ctrl+Y)'
      },
      '|',
      {
        name: 'edit-mode',
        tipPosition: 's',
        tip: '切换编辑模式'
      },
      {
        name: 'more',
        tipPosition: 's',
        tip: '更多工具',
        toolbar: [
          {
            name: 'both',
            tipPosition: 's',
            tip: '双栏模式'
          },
          {
            name: 'preview',
            tipPosition: 's',
            tip: '预览模式'
          },
          {
            name: 'outline',
            tipPosition: 's',
            tip: '大纲导航'
          },
          {
            name: 'code-theme',
            tipPosition: 's',
            tip: '代码主题'
          },
          {
            name: 'content-theme',
            tipPosition: 's',
            tip: '内容主题'
          },
          {
            name: 'export',
            tipPosition: 's',
            tip: '导出'
          },
          {
            name: 'devtools',
            tipPosition: 's',
            tip: '开发者工具'
          },
          {
            name: 'info',
            tipPosition: 's',
            tip: '关于 Vditor'
          },
          {
            name: 'help',
            tipPosition: 's',
            tip: '帮助'
          }
        ]
      }
    ],
    // 预览配置
    preview: {
      // 预览主题
      theme: {
        current: 'light'
      },
      // 代码块主题
      markdown: {
        // 代码块行号
        lineNumbers: true,
        // TOC
        toc: true,
        // 关闭自动换行（解决每行空一行的问题）
        autoSpace: false
      },
      // 数学公式
      math: {
        engine: 'KaTeX'
      },
      // 禁用自定义按钮（复制到公众号、复制到知乎等）
      actions: []
    },
    // Markdown 配置
    markdown: {
      // 关闭 breaks（单换行不转 <br>，避免每行空一行）
      breaks: false,
      // 自动空格
      autoSpace: false,
      // 段落开头空两格
      paragraphBeginningSpace: false
    },
    // 图片上传配置
    upload: {
      // 最大文件大小 5MB
      max: 5 * 1024 * 1024,
      // 允许的文件类型
      accept: 'image/*',
      // 使用 handler 自定义上传，动态获取 token（避免初始化时固化导致刷新后过期）
      handler: (files, callback) => {
        const promises = Array.from(files).map(file => uploadImageManually(file, callback))
        Promise.all(promises).then(() => {
          // 所有文件上传完成后通知 Vditor
          callback('')
        }).catch(() => {
          callback('图片上传失败')
        })
      }
    },
    // 值变化回调
    input: (value) => {
      emit('update:modelValue', value)
    },
    // HTML 变化回调
    htmlChanged: (html) => {
      emit('htmlChanged', html)
      // 计算字数
      emit('wordCount', wordCount.value)
    },
    // 快捷键
    keymap: {
      // Ctrl+B 粗体
      'ctrl+b': () => {
        vditorInstance.bold()
      },
      // Ctrl+I 斜体
      'ctrl+i': () => {
        vditorInstance.italic()
      },
      // Ctrl+K 链接
      'ctrl+k': () => {
        vditorInstance.insertValue('[](https://)')
      },
      // Ctrl+D 删除线
      'ctrl+d': () => {
        vditorInstance.strike()
      }
    },
    // 初始值
    value: props.modelValue || '',
    // 占位符
    placeholder: props.placeholder,
    // 提示
    hint: {
      // @ 提示
      at: (value) => {
        // 可以在这里添加 @ 用户提示
      }
    },
    // 缓存：禁用，由父组件通过 v-model 管理内容
    cache: {
      enable: false
    }
  })
}

// 手动上传图片
const uploadImageManually = async (file, callback) => {
  if (!file || !file.type?.startsWith('image/')) {
    antMessage.warning('仅支持图片文件')
    if (callback) callback('仅支持图片文件')
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    antMessage.warning('图片不能超过 5MB')
    if (callback) callback('图片不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadImage(formData)

    if (res?.code !== 0) {
      throw new Error(res?.description || res?.message || '图片上传失败')
    }

    const url = typeof res.data === 'string' ? res.data : res.data?.url
    if (!url) {
      throw new Error('上传成功但未返回图片地址')
    }

    // 插入图片到编辑器
    const imgMarkdown = `![${file.name || 'image'}](${url})`
    vditorInstance.insertValue(imgMarkdown)
    antMessage.success('图片已上传并插入')
  } catch (err) {
    antMessage.error(err?.description || err?.message || '图片上传失败')
    if (callback) callback(err?.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

// 导入 Markdown 文件（工具栏按钮触发）：读取本地 .md/.txt 填充编辑器
const triggerImportMarkdown = () => {
  if (!vditorInstance) return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.md,.markdown,.txt,text/markdown,text/plain'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    if (file.size > 2 * 1024 * 1024) {
      antMessage.warning('Markdown 文件不能超过 2MB')
      return
    }
    try {
      const text = await file.text()
      if (!text?.trim()) {
        antMessage.warning('文件内容为空')
        return
      }
      vditorInstance.setValue(text)
      antMessage.success('Markdown 内容已导入')
    } catch (e) {
      antMessage.error('导入失败，请重试')
    }
  }
  input.click()
}

// 监听外部值变化
watch(
  () => props.modelValue,
  (newValue) => {
    if (vditorInstance && newValue !== vditorInstance.getValue()) {
      vditorInstance.setValue(newValue)
    }
  }
)

// 添加预览工具栏中文提示
const addPreviewToolbarTips = () => {
  nextTick(() => {
    setTimeout(() => {
      const previewActions = document.querySelectorAll('.vditor-preview__action .vditor-preview__action--icon')
      const tipMap = {
        'desktop': '桌面端预览',
        'tablet': '平板端预览',
        'mobile': '手机/微信预览'
      }

      previewActions.forEach(btn => {
        // 检查按钮类型
        const svg = btn.querySelector('svg')
        if (!svg) return

        // 根据 SVG 图标判断类型
        const svgContent = svg.innerHTML
        if (svgContent.includes('M3 4a1') || btn.title === 'Desktop') {
          btn.setAttribute('data-tip', tipMap.desktop)
        } else if (svgContent.includes('M4 4a2') || btn.title === 'Tablet') {
          btn.setAttribute('data-tip', tipMap.tablet)
        } else if (svgContent.includes('M7 2a2') || btn.title === 'Mobile') {
          btn.setAttribute('data-tip', tipMap.mobile)
        }

        // 添加悬停事件显示中文提示
        btn.addEventListener('mouseenter', showTip)
        btn.addEventListener('mouseleave', hideTip)
      })
    }, 500)
  })
}

const showTip = (e) => {
  const tipText = e.target.getAttribute('data-tip') || e.target.closest('[data-tip]')?.getAttribute('data-tip')
  if (!tipText) return

  let tip = document.getElementById('vditor-custom-tip')
  if (!tip) {
    tip = document.createElement('div')
    tip.id = 'vditor-custom-tip'
    tip.style.cssText = `
      position: fixed;
      background: #333;
      color: #fff;
      padding: 6px 12px;
      border-radius: 6px;
      font-size: 12px;
      white-space: nowrap;
      box-shadow: 0 2px 12px rgba(0,0,0,0.15);
      z-index: 10000;
      pointer-events: none;
      transition: opacity 0.2s;
    `
    document.body.appendChild(tip)
  }

  tip.textContent = tipText
  tip.style.opacity = '1'
  tip.style.visibility = 'visible'

  const rect = e.target.getBoundingClientRect()
  tip.style.left = `${rect.left + rect.width / 2 - tip.offsetWidth / 2}px`
  tip.style.top = `${rect.top - tip.offsetHeight - 8}px`
}

const hideTip = () => {
  const tip = document.getElementById('vditor-custom-tip')
  if (tip) {
    tip.style.opacity = '0'
    tip.style.visibility = 'hidden'
  }
}

onMounted(() => {
  initVditor()
  addPreviewToolbarTips()
  document.addEventListener('fullscreenchange', handleFullscreenChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  // 如果还在全屏，先退出
  if (document.fullscreenElement) {
    document.exitFullscreen().catch(() => {})
  }
  // 销毁编辑器实例
  if (vditorInstance) {
    vditorInstance.destroy()
    vditorInstance = null
  }
  // 恢复 body 滚动
  document.body.style.overflow = ''
})

// 暴露方法给父组件
defineExpose({
  getValue: () => vditorInstance?.getValue() || '',
  getHTML: () => vditorInstance?.getHTML() || '',
  insertValue: (value) => vditorInstance?.insertValue(value),
  focus: () => vditorInstance?.focus(),
  blur: () => vditorInstance?.blur()
})
</script>

<style scoped>
.vditor-editor-wrapper {
  position: relative;
  border: 1px solid var(--color-border, #e0e0e0);
  border-radius: var(--radius-md, 8px);
  overflow: hidden;
  transition: all 0.2s;
}

.vditor-editor-wrapper:focus-within {
  border-color: var(--color-accent, #1890ff);
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.vditor-editor-wrapper:fullscreen,
.vditor-editor-wrapper.is-fullscreen {
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  width: 100vw !important;
  height: 100vh !important;
  z-index: 2147483647 !important;
  border-radius: 0 !important;
  border: none !important;
  background: #fff !important;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.vditor-editor-wrapper:fullscreen .vditor-container,
.vditor-editor-wrapper.is-fullscreen .vditor-container {
  flex: 1 !important;
  overflow: hidden !important;
}

.vditor-editor-wrapper:fullscreen .vditor-container :deep(.vditor),
.vditor-editor-wrapper.is-fullscreen .vditor-container :deep(.vditor) {
  height: 100% !important;
  border: none !important;
}

.vditor-editor-wrapper:fullscreen .vditor-container :deep(.vditor-content),
.vditor-editor-wrapper.is-fullscreen .vditor-container :deep(.vditor-content) {
  height: calc(100% - 40px) !important;
}

.vditor-editor-wrapper:fullscreen .vditor-container :deep(.vditor-sv),
.vditor-editor-wrapper:fullscreen .vditor-container :deep(.vditor-ir),
.vditor-editor-wrapper:fullscreen .vditor-container :deep(.vditor-wysiwyg),
.vditor-editor-wrapper.is-fullscreen .vditor-container :deep(.vditor-sv),
.vditor-editor-wrapper.is-fullscreen .vditor-container :deep(.vditor-ir),
.vditor-editor-wrapper.is-fullscreen .vditor-container :deep(.vditor-wysiwyg) {
  height: 100% !important;
  min-height: unset !important;
}

.vditor-container {
  width: 100%;
}

/* 覆盖 Vditor 默认样式 */
.vditor-container :deep(.vditor) {
  border: none;
  border-radius: 0;
}

.vditor-container :deep(.vditor-toolbar) {
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
  background: var(--color-bg-warm, #fafafa);
  padding: 6px 10px;
}

.vditor-container :deep(.vditor-content) {
  border: none;
}

.vditor-container :deep(.vditor-ir) {
  min-height: 400px;
}

.vditor-container :deep(.vditor-sv) {
  min-height: 400px;
}

.vditor-container :deep(.vditor-preview) {
  min-height: 400px;
}

/* 全屏切换按钮 */
.fullscreen-toggle {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 10;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  color: var(--color-text-secondary, #666);
  transition: all 0.2s;
}

.fullscreen-toggle:hover {
  background: var(--color-accent-glow, rgba(24, 144, 255, 0.1));
  color: var(--color-accent, #1890ff);
}

/* 工具栏按钮提示样式美化 */
.vditor-container :deep(.vditor-toolbar) {
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
  background: var(--color-bg-warm, #fafafa);
  padding: 6px 10px;
}

.vditor-container :deep(.vditor-toolbar__item) {
  position: relative;
}

.vditor-container :deep(.vditor-toolbar__item:hover) {
  background-color: var(--color-accent-glow, rgba(24, 144, 255, 0.1));
  border-radius: 4px;
}

/* 自定义提示框样式 */
.vditor-container :deep(.vditor-tip) {
  background: #333;
  color: #fff;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  z-index: 100;
}

.vditor-container :deep(.vditor-tip--s::before) {
  border-bottom-color: #333;
}

.vditor-container :deep(.vditor-tip--n::before) {
  border-top-color: #333;
}

/* 工具栏按钮活动状态 */
.vditor-container :deep(.vditor-toolbar__item.vditor-toolbar__item--current) {
  background-color: var(--color-accent-glow, rgba(24, 144, 255, 0.15));
  border-radius: 4px;
}

/* 工具栏分隔线美化 */
.vditor-container :deep(.vditor-toolbar__divider) {
  margin: 0 6px;
  height: 20px;
  border-left: 1px solid var(--color-border, #e0e0e0);
}

/* 更多按钮下拉菜单美化 */
.vditor-container :deep(.vditor-toolbar__item--more) {
  border-radius: 4px;
}

.vditor-container :deep(.vditor-hint) {
  background: #fff;
  border: 1px solid var(--color-border, #e0e0e0);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  padding: 4px;
}

.vditor-container :deep(.vditor-hint li) {
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 13px;
}

.vditor-container :deep(.vditor-hint li:hover) {
  background: var(--color-accent-glow, rgba(24, 144, 255, 0.1));
  color: var(--color-accent, #1890ff);
}

.vditor-container :deep(.vditor-hint li.vditor-hint--current) {
  background: var(--color-accent, #1890ff);
  color: #fff;
}

/* 预览工具栏按钮中文提示 */
.vditor-container :deep(.vditor-preview__action) {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Desktop 按钮提示 */
.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="desktop"]) {
  position: relative;
}

.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="desktop"]::after) {
  content: '桌面端';
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: #333;
  color: #fff;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s;
  margin-bottom: 6px;
}

.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="desktop"]:hover::after) {
  opacity: 1;
  visibility: visible;
}

/* Tablet 按钮提示 */
.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="tablet"]) {
  position: relative;
}

.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="tablet"]::after) {
  content: '平板端';
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: #333;
  color: #fff;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s;
  margin-bottom: 6px;
}

.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="tablet"]:hover::after) {
  opacity: 1;
  visibility: visible;
}

/* Mobile 按钮提示 */
.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="mobile"]) {
  position: relative;
}

.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="mobile"]::after) {
  content: '手机/微信';
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: #333;
  color: #fff;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  opacity: 0;
  visibility: hidden;
  transition: all 0.2s;
  margin-bottom: 6px;
}

.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon[data-type="mobile"]:hover::after) {
  opacity: 1;
  visibility: visible;
}

/* 预览工具栏整体样式 */
.vditor-container :deep(.vditor-preview__action) {
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
  padding: 4px 8px;
  background: var(--color-bg-warm, #fafafa);
}

/* 预览工具栏按钮悬停效果 */
.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon:hover) {
  background-color: var(--color-accent-glow, rgba(24, 144, 255, 0.1));
  border-radius: 4px;
}

/* 预览工具栏按钮激活状态 */
.vditor-container :deep(.vditor-preview__action .vditor-preview__action--icon.vditor-preview__action--current) {
  background-color: var(--color-accent-glow, rgba(24, 144, 255, 0.15));
  color: var(--color-accent, #1890ff);
  border-radius: 4px;
}

/* 底部状态栏 */
.editor-statusbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 6px 14px;
  background: var(--color-bg-warm, #fafafa);
  border-top: 1px solid var(--color-border-light, #f0f0f0);
  font-size: 11px;
  color: var(--color-text-muted, #999);
}

.status-spacer {
  flex: 1;
}

.status-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
