/**
 * 统一的 Markdown 渲染配置模块
 *
 * 将所有组件中散落的 marked.setOptions + DOMPurify.sanitize 收拢于此，
 * 消除重复配置，保证全局一致。
 *
 * 用法：
 *   import { renderMarkdown, MARKDOWN_SANITIZE } from '@/utils/markdown'
 *   const html = await renderMarkdown(rawText, 'full')
 *
 * 支持的 sanitize 预设 (MARKDOWN_SANITIZE):
 *   'full'    — 笔记详情等完整内容（含表格、图片、任务列表、SVG）
 *   'preview' — 发布页预览
 *   'summary' — 列表摘要（最精简）
 *   'chat'    — AI 聊天
 */
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'

// ===================== marked 全局配置 =====================

marked.setOptions({
  gfm: true,       // GitHub Flavored Markdown（表格、任务列表、删除线等）
  breaks: true,    // 换行符转 <br>，与语雀行为一致
  silent: true,    // 解析出错不抛异常，返回原始文本
})

// 重写默认 renderer：链接默认新窗口打开（兼容语雀行为）
const defaultRenderer = new marked.Renderer()
defaultRenderer.link = function ({ href, title, text }) {
  const titleAttr = title ? ` title="${title}"` : ''
  return `<a href="${href}"${titleAttr} target="_blank" rel="noopener">${text}</a>`
}

marked.use({ renderer: defaultRenderer })

// 代码高亮扩展（通过 marked.use 生效，兼容 marked v12+）
// 注意：marked v12+ 移除了 setOptions.highlight，必须通过扩展实现
marked.use({
  renderer: {
    code({ text, lang }) {
      const language = lang && hljs.getLanguage(lang) ? lang : null
      const highlighted = language
        ? hljs.highlight(text, { language }).value
        : hljs.highlightAuto(text).value
      const langClass = language ? ` class="hljs language-${language}"` : ''
      return `<pre><code${langClass}>${highlighted}</code></pre>`
    },
    // 图片：加 referrerpolicy=no-referrer 绕过语雀等 CDN 防盗链；lazy 加载
    image({ href, title, text }) {
      const alt = String(text || '').replace(/"/g, '&quot;')
      const titleAttr = title ? ` title="${String(title).replace(/"/g, '&quot;')}"` : ''
      return `<img src="${href}" alt="${alt}"${titleAttr} referrerpolicy="no-referrer" loading="lazy" />`
    },
  },
})

// ===================== DOMPurify 预设 =====================

const SANITIZE_PRESETS = {
  full: {
    // 笔记详情：支持完整 Markdown 语法 + 任务列表 + 图片 + SVG 图标
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'p', 'br', 'strong', 'em', 'code', 'pre',
      'ul', 'ol', 'li', 'blockquote', 'a', 'hr',
      'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'input', 'del', 'img', 'span', 'div', 'svg', 'path',
    ],
    ALLOWED_ATTR: [
      'href', 'target', 'rel', 'class', 'type', 'checked', 'disabled',
      'src', 'alt', 'title', 'width', 'height', 'style',
      'viewBox', 'd', 'fill', 'stroke', 'referrerpolicy', 'loading',
    ],
  },

  preview: {
    // 发布页预览：支持标题、链接等常用元素，不含图片/表格
    ALLOWED_TAGS: [
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'p', 'br', 'strong', 'em', 'code', 'pre',
      'ul', 'ol', 'li', 'blockquote', 'a', 'hr',
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
  },

  summary: {
    // 列表摘要：最精简，仅保留基本排版
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'code', 'ul', 'ol', 'li', 'blockquote'],
    ALLOWED_ATTR: ['class'],
  },

  chat: {
    // AI 聊天：支持表格、标题等，包含 span/div（流式渲染需要）
    ALLOWED_TAGS: [
      'p', 'br', 'strong', 'em', 'code', 'pre',
      'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
      'blockquote', 'a', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
      'hr', 'span', 'div',
    ],
    ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
    FORCE_BODY: true,
  },
}

// ===================== 导出函数 =====================

/**
 * 渲染 Markdown 并通过 DOMPurify 清洗
 * @param {string} content - Markdown 原始文本
 * @param {'full'|'preview'|'summary'|'chat'} [preset='full'] - DOMPurify 预设
 * @returns {Promise<string>} 清洗后的 HTML 字符串
 */
export async function renderMarkdown(content, preset = 'full') {
  if (!content) return ''
  const rawHtml = await marked.parse(String(content))
  const options = SANITIZE_PRESETS[preset] || SANITIZE_PRESETS.full
  return DOMPurify.sanitize(String(rawHtml), options)
}

/**
 * 纯文本转义（用于搜索高亮等不需要 Markdown 解析的场景）
 * @param {string} text
 * @returns {string}
 */
export function escapeHtml(text) {
  if (text == null) return ''
  return String(text)
    .replace(/&(?!amp;|lt;|gt;|quot;|#39;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export { marked, DOMPurify, SANITIZE_PRESETS as MARKDOWN_SANITIZE }
