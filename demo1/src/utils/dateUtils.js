// src/utils/dateUtils.js
// 通用日期时间格式化工具

/**
 * 日期格式化 YYYY.MM.DD
 * @param {string|number|Date} time
 * @returns {string}
 */
export const formatDate = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 短日期格式化 YYYY-MM-DD
 * @param {string|number|Date} time
 * @param {string} fallback - 空值时的返回文本，默认 ''
 * @returns {string}
 */
export const formatShortDate = (time, fallback = '') => {
  if (!time) return fallback
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 完整日期时间格式化 YYYY-MM-DD HH:mm:ss
 * @param {string|number|Date} time
 * @returns {string}
 */
export const formatDateTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}

/**
 * 智能时间格式化（适用于会话/消息列表场景）
 * - 今天：显示 HH:mm
 * - 其他：显示 MM-DD HH:mm
 * @param {string|number|Date} time
 * @returns {string}
 */
export const formatSmartTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  if (isToday) return `${hours}:${minutes}`
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}

/**
 * 格式化为本地时间字符串（YYYY-MM-DD HH:mm:ss）
 * 适用于后端 UTC 时间转换为本地友好格式
 * @param {string|number|Date} time
 * @param {string} fallback - 空值时的返回文本，默认 '--'
 * @returns {string}
 */
export const formatLocalDateTime = (time, fallback = '--') => {
  if (!time) return fallback
  const date = new Date(time)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}
