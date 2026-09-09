// 流式数据处理工具

const getAuthHeaders = () => {
  // 优先从 pinia-plugin-persistedstate 持久化的 userLogin store 中读取
  try {
    const userLogin = JSON.parse(localStorage.getItem('userLogin') || '{}')
    if (userLogin.token) return { Authorization: `Bearer ${userLogin.token}` }
  } catch {}
  // 兼容旧版 localStorage 存储
  const token = localStorage.getItem('token') || (() => {
    try { return JSON.parse(localStorage.getItem('userInfo') || '{}').token } catch { return '' }
  })()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

/**
 * 通用流式数据处理函数
 */
export const handleStreamData = async (url, options, onChunk, onError, onComplete, onProgress) => {
  try {
    const mergedHeaders = { ...(options?.headers || {}), ...getAuthHeaders() }
    const response = await fetch(url, { ...options, headers: mergedHeaders })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)

    const totalBytes = Number(response.headers.get('content-length')) || 0
    let receivedBytes = 0
    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      receivedBytes += value.length
      if (onProgress && totalBytes > 0) {
        onProgress(Math.round((receivedBytes / totalBytes) * 100))
      }
      onChunk(decoder.decode(value, { stream: true }))
    }
    if (onComplete) onComplete()
  } catch (error) {
    if (onError) onError(error)
  }
}

/**
 * EventSource 实现（服务器主动推送）
 */
export const createEventSource = (url, eventHandlers, options = {}) => {
  if (!window.EventSource) {
    if (eventHandlers.error) eventHandlers.error(new Error('Browser does not support EventSource'))
    return null
  }
  try {
    const eventSource = new EventSource(url, options)
    if (eventHandlers.message) eventSource.onmessage = (e) => eventHandlers.message(e.data)
    if (eventHandlers.error) eventSource.onerror = (e) => eventHandlers.error(e)
    if (eventHandlers.open) eventSource.onopen = () => eventHandlers.open()
    if (eventHandlers.events) {
      Object.entries(eventHandlers.events).forEach(([name, handler]) => {
        eventSource.addEventListener(name, (e) => handler(e.data))
      })
    }
    return eventSource
  } catch (error) {
    if (eventHandlers.error) eventHandlers.error(error)
    return null
  }
}

export const closeEventSource = (eventSource) => {
  if (eventSource && eventSource.readyState !== EventSource.CLOSED) {
    eventSource.close()
  }
}

/**
 * 解析单条 SSE 行内容，返回纯文本字符串或 null（跳过）
 *
 * 后端实际格式：data:""内容""\n\n
 * 即每个 chunk 经过了 JSON.stringify 后再拼 data: 前缀，
 * 导致内容被双层双引号包裹：data:"" 内容 ""
 *
 * 处理步骤：
 * 1. 去掉行首 data: 前缀
 * 2. 剥离所有外层连续双引号（""xxx"" → xxx）
 * 3. 尝试 JSON.parse 处理标准 JSON 字符串（"xxx" → xxx）
 * 4. 跳过空行、[DONE]
 */
/**
 * 解析单条 SSE 行内容，返回纯文本字符串或 null（跳过）
 *
 * 后端（ChatServiceImpl.sendMessageStream）已在 map() 中完成内容清洗：
 * - 去掉首尾引号
 * - 过滤空 chunk
 * Spring WebFlux TEXT_EVENT_STREAM_VALUE 自动添加 "data:" 前缀和 "\n\n" 分隔符
 * 所以每帧格式是标准 SSE：data:纯文本内容\n\n
 *
 * 兼容处理：保留对旧版双引号格式的降级处理，防止后端版本回退时出错
 */
const parseSSELine = (line) => {
  let text = line.trim()
  if (!text) return null

  // 跳过 SSE 元数据行
  if (text.startsWith('event:') || text.startsWith('id:') || text.startsWith('retry:')) {
    return null
  }

  // 必须有 data: 前缀，否则跳过
  if (!text.startsWith('data:')) return null

  // 剥离 data: 前缀
  text = text.replace(/^data:\s*/i, '').trim()

  if (!text || text === '[DONE]') return null

  // 先剥离多层双引号包裹（后端两次序列化导致 ""内容"" 格式）
  while (text.startsWith('""') && text.endsWith('""') && text.length > 4) {
    text = text.slice(2, -2).trim()
  }
  if (text.startsWith('""')) text = text.slice(2).trim()
  if (text.endsWith('""')) text = text.slice(0, -2).trim()

  if (!text || text === '[DONE]') return null

  // 再尝试 JSON.parse（处理单层引号 "内容" 的标准 JSON 字符串）
  try {
    const parsed = JSON.parse(text)
    if (typeof parsed === 'string') return parsed.replace(/\\n/g, '\n') || null
    if (parsed && typeof parsed.content === 'string') return parsed.content || null
    return null
  } catch {
    // 非 JSON：处理转义换行符后直接返回
    return text.replace(/\\n/g, '\n') || null
  }
}

/**
 * 流式聊天消息发送
 */

export const sendStreamChatMessage = async (body, onChunk, onError, onComplete) => {
  let reader
  try {
    const response = await fetch('/api/chat/message/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...getAuthHeaders() },
      body: JSON.stringify(body),
    })

    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
    if (!response.body) throw new Error('No response body')

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 兼容两种情况：
      // 1. 真实换行符（标准 SSE）：\n\n
      // 2. 字面转义字符串（后端将 \n 序列化为 \\n）：\\n\\n
      const hasRealNewlines = buffer.includes('\n\n')
      const hasEscapedNewlines = buffer.includes('\\n\\n')

      if (!hasRealNewlines && !hasEscapedNewlines) continue

      let frames
      if (hasRealNewlines) {
        frames = buffer.split('\n\n')
        buffer = frames.pop() ?? ''
        for (const frame of frames) {
          for (const line of frame.split('\n')) {
            const text = parseSSELine(line)
            if (text) onChunk(text)
          }
        }
      } else {
        // 字面 \n\n：整个 buffer 是一个被序列化的字符串，先整体解析
        processEscapedBuffer(buffer, onChunk)
        buffer = ''
      }
    }

    // 处理缓冲区剩余内容
    if (buffer.trim()) {
      if (buffer.includes('\n')) {
        for (const line of buffer.split('\n')) {
          const text = parseSSELine(line)
          if (text) onChunk(text)
        }
      } else if (buffer.includes('\\n')) {
        processEscapedBuffer(buffer, onChunk)
      } else {
        const text = parseSSELine(buffer)
        if (text) onChunk(text)
      }
    }

    if (onComplete) onComplete()
  } catch (error) {
    if (onError) onError(error)
    else throw error
  } finally {
    if (reader) reader.releaseLock()
  }
}

/**
 * 处理字面转义的 SSE 流（\n 未被解码为真实换行，而是 \\n 字符串）
 * 格式示例：event:message\ndata:""内容""\n\nevent:message\ndata:""内容""\n\n
 */
const processEscapedBuffer = (buffer, onChunk) => {
  // 按字面 \n\n 切帧
  const frames = buffer.split('\\n\\n')
  for (const frame of frames) {
    // 按字面 \n 切行
    for (const line of frame.split('\\n')) {
      const text = parseSSELine(line)
      if (text) onChunk(text)
    }
  }
}

export const parseStreamData = (data) => {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

export const isStreamApiSupported = () => !!window.fetch && !!window.ReadableStream
export const isEventSourceSupported = () => !!window.EventSource
