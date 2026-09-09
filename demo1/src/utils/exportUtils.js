/**
 * 笔记导出工具函数
 */
import { marked } from 'marked'

marked.setOptions({ gfm: true, breaks: true, silent: true })

/**
 * 导出为 Markdown 文件
 */
export const exportAsMarkdown = (note) => {
  const filename = `${sanitizeFilename(note.title || '未命名笔记')}.md`
  const content = buildMarkdownContent(note)
  downloadText(content, filename, 'text/markdown')
}

/**
 * 导出为纯文本
 */
export const exportAsText = (note) => {
  const filename = `${sanitizeFilename(note.title || '未命名笔记')}.txt`
  const content = stripMarkdown(note.content || '')
  downloadText(content, filename, 'text/plain')
}

/**
 * 导出为可打印 HTML（可打印为 PDF）
 */
export const exportAsPrintableHTML = async (notes) => {
  const html = await buildPrintableHTML(notes)
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const win = window.open(url, '_blank')
  if (win) {
    // 等待文档与图片渲染完成后再弹出打印，避免内容截断
    win.onload = () => {
      setTimeout(() => win.print(), 300)
    }
  }
  setTimeout(() => URL.revokeObjectURL(url), 60000)
}

/**
 * 导出单个笔记为 PDF（通过浏览器打印）
 */
export const exportAsPDF = (note) => {
  return exportAsPrintableHTML([note])
}

/**
 * 批量导出笔记（逐个下载 .md 文件）
 */
export const exportAsZip = async (notes) => {
  notes.forEach((note, i) => {
    setTimeout(() => exportAsMarkdown(note), i * 300)
  })
}

// ========== 辅助函数 ==========

function buildMarkdownContent(note) {
  const parts = []
  if (note.title) parts.push(`# ${note.title}\n`)
  if (note.summary) parts.push(`> ${note.summary}\n`)
  if (note.category) parts.push(`**分类：** ${note.category}\n`)
  if (note.tagList?.length) parts.push(`**标签：** ${note.tagList.join(', ')}\n`)
  if (note.publishTime) parts.push(`**发布时间：** ${formatDate(new Date(note.publishTime))}\n`)
  parts.push('---\n')
  if (note.content) parts.push(note.content)
  return parts.join('\n')
}

async function buildPrintableHTML(notes) {
  // 逐篇将 Markdown 渲染为 HTML，保证导出内容与页面预览排版一致
  const contentsHtml = await Promise.all(
    notes.map(async (note) => {
      let contentHtml = ''
      if (note.content) {
        try {
          contentHtml = await marked.parse(String(note.content))
        } catch (e) {
          contentHtml = `<pre>${escapeHTML(String(note.content))}</pre>`
        }
      }
      return `
    <div class="note">
      <h1>${escapeHTML(note.title || '未命名笔记')}</h1>
      <div class="meta">
        ${note.author ? `作者：${escapeHTML(String(note.author))}` : ''}
        ${note.publishTime ? ` · ${formatDate(new Date(note.publishTime))}` : ''}
        ${note.category ? ` · ${escapeHTML(String(note.category))}` : ''}
        ${note.tagList?.length ? ` · ${note.tagList.map((t) => escapeHTML(String(t))).join(' / ')}` : ''}
      </div>
      <div class="content">${contentHtml}</div>
    </div>`
    })
  )

  const styles = `
    <style>
      * { margin: 0; padding: 0; box-sizing: border-box; }
      body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC',
          'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
        padding: 40px;
        color: #1a1a1a;
        line-height: 1.8;
        font-size: 14px;
      }
      .note { margin-bottom: 48px; }
      .note:last-child { margin-bottom: 0; }
      h1 { font-size: 26px; line-height: 1.4; margin-bottom: 8px; }
      h2 { font-size: 20px; margin: 24px 0 12px; padding-bottom: 6px; border-bottom: 1px solid #e5e7eb; }
      h3 { font-size: 17px; margin: 20px 0 8px; }
      h4, h5, h6 { font-size: 15px; margin: 16px 0 8px; }
      p { margin: 10px 0; text-align: justify; word-break: break-word; }
      .meta { color: #666; font-size: 12px; margin-bottom: 20px; padding-bottom: 12px; border-bottom: 1px solid #e0e0e0; }
      .content { font-size: 14px; }
      .content ul, .content ol { margin: 10px 0; padding-left: 28px; }
      .content li { margin: 4px 0; }
      .content pre {
        background: #f5f5f5;
        padding: 12px 16px;
        border-radius: 4px;
        overflow-x: hidden;
        white-space: pre-wrap;
        word-break: break-all;
        margin: 12px 0;
        page-break-inside: avoid;
      }
      .content pre code { background: none; padding: 0; font-size: 13px; line-height: 1.6; }
      .content code { background: #f0f0f0; padding: 2px 5px; border-radius: 3px; font-size: 13px; word-break: break-all; }
      .content blockquote { border-left: 3px solid #2563eb; padding: 8px 16px; margin: 12px 0; background: #f8f9fa; color: #444; }
      .content img { max-width: 100%; height: auto; page-break-inside: avoid; margin: 8px 0; }
      .content table { border-collapse: collapse; width: 100%; margin: 12px 0; page-break-inside: avoid; }
      .content th, .content td { border: 1px solid #d1d5db; padding: 8px 12px; text-align: left; }
      .content th { background: #f3f4f6; }
      .content a { color: #2563eb; text-decoration: none; word-break: break-all; }
      .content hr { border: none; border-top: 1px solid #e0e0e0; margin: 24px 0; }
      @media print {
        body { padding: 0; }
        .note { page-break-after: always; }
        .note:last-child { page-break-after: auto; }
      }
    </style>
  `

  return `<!DOCTYPE html><html><head><meta charset="utf-8"><title>CodeVerse 笔记导出</title>${styles}</head><body>${contentsHtml.join(
    '<hr class="note-divider">'
  )}</body></html>`
}

function sanitizeFilename(name) {
  return name.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_').slice(0, 100)
}

function downloadText(content, filename, mimeType) {
  const blob = new Blob([content], { type: `${mimeType};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function stripMarkdown(md) {
  return md
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_~>-]/g, '')
}

function formatDate(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function escapeHTML(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}
