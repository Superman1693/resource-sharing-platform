// src/utils/constant.js
// 内容管理-类型/状态常量配置
export const CONTENT_TYPE_OPTIONS = [
  { label: '文章', value: 'article' },
  { label: '问题', value: 'question' },
  { label: '笔记', value: 'note' },
]

export const CONTENT_STATUS_OPTIONS = [
  { label: '已发布', value: 'published' },
  { label: '草稿', value: 'draft' },
  { label: '已删除', value: 'deleted' },
]

export const CONTENT_TYPE_COLOR = {
  article: 'green',
  question: 'orange',
  note: 'purple'
}

export const CONTENT_STATUS_COLOR = {
  published: 'green',
  draft: 'blue',
  deleted: 'red'
}

// ── 分类常量 ──
export const CATEGORY_OPTIONS = [
  { label: '前端开发', value: 'frontend' },
  { label: '后端开发', value: 'backend' },
  { label: '算法与数据结构', value: 'algorithm' },
  { label: '数据库', value: 'database' },
  { label: '其他', value: 'other' },
]

/** 分类 value → 完整标签（前端开发、后端开发 …） */
export const CATEGORY_TEXT = Object.fromEntries(
  CATEGORY_OPTIONS.map(o => [o.value, o.label])
)

/** 分类 value → 短标签（前端、后端 …），用于标签空间有限的场景 */
export const CATEGORY_TEXT_SHORT = {
  frontend: '前端',
  backend: '后端',
  algorithm: '算法',
  database: '数据库',
  other: '其他',
}

// ── 资源类型常量 ──
export const RESOURCE_TYPE_OPTIONS = [
  { label: '文档', value: 'document' },
  { label: '视频', value: 'video' },
  { label: '代码', value: 'code' },
  { label: '其他', value: 'other' },
]