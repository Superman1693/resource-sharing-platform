/**
 * 处理后端返回的聊天消息格式
 * 修复点：移除步骤9的 /(\S)\s+(\S)/g 正则，该正则会破坏正常文本中的空格
 */
export function processChatMessage(message) {
  if (!message) return ''

  let processed = message

  // 1. 移除行首 "data:" 前缀
  processed = processed.replace(/^data:\s*/gim, '')

  // 2. 处理字面量换行符和多余引号
  processed = processed.replace(/\\n/g, '\n')
  processed = processed.replace(/""/g, '"')
  if (processed.startsWith('"') && processed.endsWith('"') && processed.length > 1) {
    processed = processed.slice(1, -1)
  }

  // 3. 合并连续空行（超过两个换行压缩为两个）
  processed = processed.replace(/(\r?\n){3,}/g, '\n\n')

  // 4. 去除首尾空白
  processed = processed.trim()

  return processed
}

/**
 * 处理聊天消息流（多行合并）
 */
export function processChatMessageStream(stream) {
  const messages = stream.split('\n').filter(msg => msg.trim())
  return messages.map(msg => processChatMessage(msg)).join('\n\n')
}

/**
 * 处理 SSE 单条数据
 */
export function processSSEData(data) {
  let processed = data.replace(/^data:\s*/i, '').trim()
  processed = processed.replace(/\\n/g, '\n')
  processed = processed.replace(/""/g, '"')
  if (processed.startsWith('"') && processed.endsWith('"') && processed.length > 1) {
    processed = processed.slice(1, -1)
  }
  return processed.trim()
}
