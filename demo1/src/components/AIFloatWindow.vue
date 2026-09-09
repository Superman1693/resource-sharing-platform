<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import {
  SendOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  RobotOutlined, MoreOutlined, LoadingOutlined,
  MinusOutlined, CloseOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '../store/userLogin'
import logoUrl from '../assets/images/logo.png'
import UserAvatar from './UserAvatar.vue'
import { getChatSessions, createChatSession, updateChatSession, deleteChatSession, getChatMessages } from '../utils/api'
import { sendStreamChatMessage } from '../utils/stream'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true, silent: true })

// 预渲染的消息 HTML 缓存
const messageHtmlCache = ref({})

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['update:visible'])

const userStore = useUserStore()
const userInfo = computed(() => ({
  username: userStore.username || '用户',
  avatarUrl: userStore.avatarUrl || ''
}))

const sessions = ref([])
const currentSessionId = ref('')
const loadingSessions = ref(false)
const messages = ref([])
const loadingMessages = ref(false)
const inputMessage = ref('')
const sending = ref(false)
const streaming = ref(false)
const editingSession = ref(null)
const editSessionName = ref('')
const showEditModal = ref(false)
const messagesContainerRef = ref(null)

const windowState = ref({
  width: 760, height: 560,
  x: Math.max(20, window.innerWidth - 800),
  y: Math.max(20, (window.innerHeight - 560) / 2),
  isDragging: false, isResizing: false, isMinimized: false,
  dragStart: { x: 0, y: 0 },
  resizeStart: { x: 0, y: 0, width: 0, height: 0 }
})

const currentSession = computed(() => sessions.value.find(s => s.sessionId === currentSessionId.value) || null)
const canLoadChatSessions = computed(() => userStore.isLogin)

// ── 拖拽 ──────────────────────────────────────────────────
let rafId = null, lastMX = 0, lastMY = 0

const startDrag = (e) => {
  if (!e.target.closest('.window-header')) return
  e.preventDefault()
  windowState.value.isDragging = true
  windowState.value.dragStart = { x: e.clientX - windowState.value.x, y: e.clientY - windowState.value.y }
}

const onDrag = (e) => {
  if (!windowState.value.isDragging) return
  lastMX = e.clientX; lastMY = e.clientY
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    windowState.value.x = lastMX - windowState.value.dragStart.x
    windowState.value.y = lastMY - windowState.value.dragStart.y
    rafId = null
  })
}

const endDrag = () => {
  windowState.value.isDragging = false
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
}

// ── 调整大小 ───────────────────────────────────────────────
let resizeRafId = null, lastRX = 0, lastRY = 0

const startResize = (e) => {
  e.preventDefault(); e.stopPropagation()
  windowState.value.isResizing = true
  windowState.value.resizeStart = {
    x: e.clientX, y: e.clientY,
    width: windowState.value.width, height: windowState.value.height
  }
}

const onResize = (e) => {
  if (!windowState.value.isResizing) return
  lastRX = e.clientX; lastRY = e.clientY
  if (resizeRafId) return
  resizeRafId = requestAnimationFrame(() => {
    windowState.value.width = Math.max(560, windowState.value.resizeStart.width + (lastRX - windowState.value.resizeStart.x))
    windowState.value.height = Math.max(420, windowState.value.resizeStart.height + (lastRY - windowState.value.resizeStart.y))
    resizeRafId = null
  })
}

const endResize = () => {
  windowState.value.isResizing = false
  if (resizeRafId) { cancelAnimationFrame(resizeRafId); resizeRafId = null }
}

const toggleMinimize = () => { windowState.value.isMinimized = !windowState.value.isMinimized }
const closeWindow = () => emit('update:visible', false)

// ── 会话 ───────────────────────────────────────────────────
// 只刷新会话列表，不触发消息重载（用于流式完成后更新侧栏预览）
const refreshSessionsOnly = async () => {
  if (!canLoadChatSessions.value) return
  try {
    const res = await getChatSessions()
    if (res.code === 0 && res.data) {
      sessions.value = res.data
    }
  } catch { /* 静默失败，不影响主流程 */ }
}

const fetchSessions = async () => {
  if (!canLoadChatSessions.value) {
    sessions.value = []
    currentSessionId.value = ''
    return
  }
  loadingSessions.value = true
  try {
    const res = await getChatSessions()
    if (res.code === 0 && res.data) {
      sessions.value = res.data
      if (sessions.value.length > 0 && !currentSessionId.value) {
        currentSessionId.value = sessions.value[0].sessionId
        await fetchMessages(currentSessionId.value)
      }
    }
  } catch { antMessage.error('获取会话列表失败') }
  finally { loadingSessions.value = false }
}

const fetchMessages = async (sessionId) => {
  if (!sessionId) return
  loadingMessages.value = true
  try {
    const res = await getChatMessages(sessionId)
    if (res.code === 0 && res.data) {
      messages.value = res.data
      // 异步渲染所有消息的 HTML
      messages.value.forEach(async (msg) => {
        messageHtmlCache.value[msg.messageId] = await renderMessageAsync(msg.content)
      })
      await nextTick(); scrollToBottom()
    }
  } catch { antMessage.error('获取消息列表失败') }
  finally { loadingMessages.value = false }
}

const handleCreateSession = async () => {
  try {
    const res = await createChatSession({ sessionName: '新对话' })
    if (res.code === 0 && res.data) {
      sessions.value.unshift(res.data)
      currentSessionId.value = res.data.sessionId
      messages.value = []
      antMessage.success('创建会话成功')
    }
  } catch { antMessage.error('创建会话失败') }
}

const handleSwitchSession = async (sessionId) => {
  if (sessionId === currentSessionId.value) return
  currentSessionId.value = sessionId
  messages.value = []
  await fetchMessages(sessionId)
}

const handleDeleteSession = async (sessionId, e) => {
  e.stopPropagation()
  try {
    const res = await deleteChatSession(sessionId)
    if (res.code === 0) {
      sessions.value = sessions.value.filter(s => s.sessionId !== sessionId)
      if (currentSessionId.value === sessionId) {
        currentSessionId.value = sessions.value[0]?.sessionId || ''
        currentSessionId.value ? await fetchMessages(currentSessionId.value) : (messages.value = [])
      }
      antMessage.success('删除会话成功')
    }
  } catch { antMessage.error('删除会话失败') }
}

const handleEditSession = (session, e) => {
  e.stopPropagation()
  editingSession.value = session
  editSessionName.value = session.sessionName
  showEditModal.value = true
}

const handleSaveSessionName = async () => {
  if (!editSessionName.value.trim()) { antMessage.warning('会话名称不能为空'); return }
  try {
    const res = await updateChatSession({ sessionId: editingSession.value.sessionId, sessionName: editSessionName.value.trim() })
    if (res.code === 0) {
      const s = sessions.value.find(s => s.sessionId === editingSession.value.sessionId)
      if (s) s.sessionName = editSessionName.value.trim()
      showEditModal.value = false
      antMessage.success('更新会话成功')
    }
  } catch { antMessage.error('更新会话失败') }
}

// ── 发送消息 ───────────────────────────────────────────────
const handleSendMessage = async () => {
  if (!inputMessage.value.trim() || sending.value) return
  const content = inputMessage.value.trim()
  inputMessage.value = ''

  const userMsgId = 'user-' + Date.now()
  messages.value.push({
    messageId: userMsgId,
    sessionId: currentSessionId.value,
    senderType: 'user',
    content,
    timestamp: new Date().toISOString()
  })
  // 立即渲染用户消息，否则模板读不到缓存会显示空气泡
  messageHtmlCache.value[userMsgId] = await renderMessageAsync(content)
  await nextTick(); scrollToBottom()

  sending.value = true
  streaming.value = true

  try {
    // 构造 ChatRequest：后端支持在同一请求里传 sessionId + content + sessionName
    // 若无会话，传 sessionName 让后端自动创建，无需前端单独调 createSession
    const chatRequest = {
      content,
      sessionId: currentSessionId.value || undefined,
      sessionName: currentSessionId.value ? undefined : content.slice(0, 20),
      streaming: true
    }

    // 用普通对象 push 进 ref 数组，Vue 会自动代理
    // 通过 aiMsgIndex 索引操作 messages.value 中的元素，确保响应式追踪
    const aiMsgIndex = messages.value.length
    const aiMsgId = 'ai-' + Date.now()
    messages.value.push({
      messageId: aiMsgId,
      sessionId: currentSessionId.value,
      senderType: 'ai',
      content: '',
      timestamp: new Date().toISOString(),
      isStreaming: true
    })
    await nextTick(); scrollToBottom()

    await sendStreamChatMessage(
      chatRequest,
      async (chunk) => {
        if (!messages.value[aiMsgIndex]) return
        if (chunk.startsWith('[ERROR]')) {
          messages.value[aiMsgIndex].content = chunk.replace('[ERROR]', '').trim() || '响应异常，请稍后重试'
          messages.value[aiMsgIndex].isStreaming = false
          streaming.value = false
          messageHtmlCache.value[aiMsgId] = await renderMessageAsync(messages.value[aiMsgIndex].content)
          return
        }
        messages.value[aiMsgIndex].content += chunk
        messageHtmlCache.value[aiMsgId] = await renderMessageAsync(messages.value[aiMsgIndex].content)
        scrollToBottom()
      },
      (error) => {
        antMessage.error('消息处理失败: ' + error.message)
        if (messages.value[aiMsgIndex]) messages.value[aiMsgIndex].isStreaming = false
        streaming.value = false
      },
      async () => {
        if (messages.value[aiMsgIndex]) messages.value[aiMsgIndex].isStreaming = false
        streaming.value = false
        // 只刷新侧栏会话预览，不重载 messages（避免覆盖流式累积的内容）
        await refreshSessionsOnly()
        if (!currentSessionId.value && sessions.value.length > 0) {
          currentSessionId.value = sessions.value[0].sessionId
        }
      }
    )
  } catch {
    antMessage.error('发送消息失败')
    const last = messages.value[messages.value.length - 1]
    if (last?.senderType === 'ai') {
      last.isStreaming = false
      last.content = last.content || '发送失败，请重试'
    }
  } finally {
    sending.value = false
    streaming.value = false
  }
}

const scrollToBottom = () => {
  if (messagesContainerRef.value) {
    messagesContainerRef.value.scrollTop = messagesContainerRef.value.scrollHeight
  }
}

const formatTime = (ts) => {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const formatDate = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const today = new Date(); today.setHours(0,0,0,0)
  const msgDay = new Date(d); msgDay.setHours(0,0,0,0)
  const diff = today - msgDay
  if (diff === 0) return '今天'
  if (diff === 86400000) return '昨天'
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

/**
 * Markdown 渲染 + DOMPurify XSS 清理
 * 防止 AI 返回恶意 HTML/脚本
 */
const formatMessageContent = (msg) => {
  return messageHtmlCache.value[msg.messageId] || ''
}

// 异步渲染消息内容
const renderMessageAsync = async (content) => {
  if (!content) return ''
  try {
    const rawHtml = await marked.parse(content)
    return DOMPurify.sanitize(rawHtml, {
      ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'code', 'pre', 'ul', 'ol', 'li',
                     'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'blockquote', 'a',
                     'table', 'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'span', 'div'],
      ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
      FORCE_BODY: true,
    })
  } catch {
    return content
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\n/g, '<br>')
  }
}

watch(() => messages.value.length, () => nextTick(scrollToBottom))


// ===== 新增修复点：监听窗口打开，自动刷新数据 =====
watch(() => props.visible, async (newVal) => {
  if (newVal && canLoadChatSessions.value) {
    await fetchSessions()
    // 如果存在当前选中的会话，同步刷新它的消息记录
    if (currentSessionId.value) {
      await fetchMessages(currentSessionId.value)
    }
  }
})


onMounted(() => {
  fetchSessions()
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
  document.addEventListener('mousemove', onResize)
  document.addEventListener('mouseup', endResize)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', endResize)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="ai-float-window"
      :class="{ 'is-dragging': windowState.isDragging, 'is-resizing': windowState.isResizing }"
      :style="{
        width: windowState.width + 'px',
        height: windowState.isMinimized ? '56px' : windowState.height + 'px',
        left: windowState.x + 'px',
        top: windowState.y + 'px'
      }"
      @mousedown="startDrag"
    >
      <!-- 头部 -->
      <div class="window-header">
        <div class="window-title">
          <span class="title-icon"><RobotOutlined /></span>
          <span>{{ currentSession?.sessionName || 'AI 助手' }}</span>
        </div>
        <div class="window-controls">
          <button class="ctrl-btn" @click.stop="toggleMinimize" :title="windowState.isMinimized ? '展开' : '最小化'">
            <MinusOutlined />
          </button>
          <button class="ctrl-btn ctrl-close" @click.stop="closeWindow" title="关闭">
            <CloseOutlined />
          </button>
        </div>
      </div>

      <!-- 主体 -->
      <div v-if="!windowState.isMinimized" class="window-body">
        <!-- 左侧会话列表 -->
        <div class="session-sidebar">
          <div class="sidebar-header">
            <span class="sidebar-title">会话</span>
            <button class="new-session-btn" @click="handleCreateSession" title="新建会话">
              <PlusOutlined />
            </button>
          </div>

          <div class="session-list">
            <div v-if="loadingSessions" class="list-placeholder">
              <LoadingOutlined spin /> 加载中...
            </div>
            <div v-else-if="sessions.length === 0" class="list-placeholder">
              <RobotOutlined style="font-size:24px;margin-bottom:8px;" />
              <p>暂无会话</p>
              <button class="new-session-btn-lg" @click="handleCreateSession">开始对话</button>
            </div>
            <template v-else>
              <div
                v-for="session in sessions"
                :key="session.sessionId"
                class="session-item"
                :class="{ active: session.sessionId === currentSessionId }"
                @click="handleSwitchSession(session.sessionId)"
              >
                <div class="session-info">
                  <div class="session-name">{{ session.sessionName }}</div>
                  <div v-if="session.lastMessage" class="session-preview">{{ session.lastMessage }}</div>
                </div>
                <a-dropdown placement="bottomRight" trigger="click">
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click="(e) => handleEditSession(session, e)">
                        <EditOutlined /> 重命名
                      </a-menu-item>
                      <a-menu-item danger @click="(e) => handleDeleteSession(session.sessionId, e)">
                        <DeleteOutlined /> 删除
                      </a-menu-item>
                    </a-menu>
                  </template>
                  <div class="session-more" @click.stop><MoreOutlined /></div>
                </a-dropdown>
              </div>
            </template>
          </div>
        </div>

        <!-- 右侧聊天区 -->
        <div class="chat-main">
          <div class="chat-header">
            <span class="chat-title">{{ currentSession?.sessionName || 'AI 助手' }}</span>
            <span class="chat-status" :class="{ typing: streaming }">
              {{ streaming ? '正在输入...' : '在线' }}
            </span>
          </div>

          <div class="messages-container" ref="messagesContainerRef">
            <div v-if="loadingMessages" class="msg-placeholder">
              <LoadingOutlined spin /> 加载消息...
            </div>

            <div v-else-if="messages.length === 0" class="welcome-screen">
              <div class="welcome-card">
                <div class="welcome-icon"><RobotOutlined /></div>
                <h3>你好，我是 AI 助手</h3>
                <p>可以帮你解答问题、写代码、提供建议</p>
                <div class="quick-actions">
                  <div class="quick-item" @click="inputMessage = '帮我写一段代码'">💻 写代码</div>
                  <div class="quick-item" @click="inputMessage = '解释一下这个概念'">📚 解释概念</div>
                  <div class="quick-item" @click="inputMessage = '给我一些建议'">💡 寻求建议</div>
                </div>
              </div>
            </div>

            <template v-else>
              <div
                v-for="(msg, idx) in messages"
                :key="msg.messageId || idx"
                class="message-item"
                :class="msg.senderType"
              >
                <div class="msg-avatar">
                  <!-- 用户头像：无头像时显示项目默认头像 -->
                  <UserAvatar
                    v-if="msg.senderType === 'user'"
                    :src="userInfo.avatarUrl"
                    :size="32"
                    class="avatar-user"
                  />
                  <!-- AI 头像：使用机器人图片 -->
                  <a-avatar
                    v-else
                    :src="logoUrl"
                    :size="32"
                    class="avatar-ai"
                  />
                </div>
                <div class="msg-content">
                  <div class="msg-bubble">
                    <div class="msg-text" v-html="formatMessageContent(msg)"></div>
                    <div v-if="msg.isStreaming" class="typing-dots">
                      <span></span><span></span><span></span>
                    </div>
                  </div>
                  <div class="msg-time">{{ formatTime(msg.timestamp) }}</div>
                </div>
              </div>
            </template>
          </div>

          <!-- 输入区 -->
          <div class="input-area">
            <div class="input-row">
              <a-textarea
                v-model:value="inputMessage"
                placeholder="输入消息，Enter 发送..."
                :auto-size="{ minRows: 1, maxRows: 4 }"
                :disabled="sending"
                @pressEnter="(e) => { e.preventDefault(); handleSendMessage() }"
              />
              <button
                class="send-btn"
                :disabled="!inputMessage.trim() || sending"
                @click="handleSendMessage"
              >
                <LoadingOutlined v-if="sending" />
                <SendOutlined v-else />
              </button>
            </div>
            <div class="input-hint">Shift+Enter 换行</div>
          </div>
        </div>
      </div>

      <div class="resize-handle" @mousedown.stop="startResize"></div>
    </div>

    <a-modal v-model:open="showEditModal" title="重命名会话" @ok="handleSaveSessionName" @cancel="showEditModal = false">
      <a-input v-model:value="editSessionName" placeholder="请输入会话名称" @pressEnter="handleSaveSessionName" />
    </a-modal>
  </Teleport>
</template>

<style scoped>
/* ── 窗口容器 ─────────────────────────────────────────────── */
.ai-float-window {
  position: fixed;
  z-index: 1000;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e4e7;
  box-shadow: 0 20px 60px rgba(0,0,0,0.12), 0 8px 24px rgba(0,0,0,0.06);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  will-change: left, top, width, height;
  contain: layout style;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
  transition: box-shadow 0.2s ease;
}

.ai-float-window.is-dragging,
.ai-float-window.is-resizing {
  transition: none;
  box-shadow: 0 28px 80px rgba(0,0,0,0.18), 0 12px 32px rgba(0,0,0,0.1);
  user-select: none;
}

/* ── 头部 ─────────────────────────────────────────────────── */
.window-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 52px;
  background: #fff;
  border-bottom: 1px solid #f4f4f5;
  cursor: move;
  flex-shrink: 0;
  /* 主题色顶部线条 */
  background-image: linear-gradient(var(--color-accent) 0, var(--color-accent) 2px, #fff 2px);
}

.window-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
  letter-spacing: -0.01em;
}

.title-icon {
  width: 26px;
  height: 26px;
  background: var(--color-accent-glow);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-accent);
  font-size: 14px;
}

.window-controls {
  display: flex;
  gap: 6px;
}

.ctrl-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #71717a;
  font-size: 13px;
  transition: background 0.15s, color 0.15s;
  padding: 0;
}

.ctrl-btn:hover { background: #f4f4f5; color: #18181b; }
.ctrl-close:hover { background: rgba(239,68,68,0.08); color: #ef4444; }

/* ── 主体布局 ─────────────────────────────────────────────── */
.window-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* ── 会话侧栏 ─────────────────────────────────────────────── */
.session-sidebar {
  width: 200px;
  flex-shrink: 0;
  border-right: 1px solid #f4f4f5;
  display: flex;
  flex-direction: column;
  background: #fafafa;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid #f4f4f5;
  background: #fff;
}

.sidebar-title {
  font-size: 12px;
  font-weight: 600;
  color: #71717a;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.new-session-btn {
  width: 26px;
  height: 26px;
  border: 1px solid #e4e4e7;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-accent);
  font-size: 12px;
  transition: all 0.15s;
  padding: 0;
}

.new-session-btn:hover {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: #fff;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px;
}

.session-list::-webkit-scrollbar { width: 4px; }
.session-list::-webkit-scrollbar-thumb { background: #e4e4e7; border-radius: 2px; }

.list-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 12px;
  color: #a1a1aa;
  font-size: 12px;
  gap: 6px;
  text-align: center;
}

.list-placeholder p { margin: 0; }

.new-session-btn-lg {
  margin-top: 8px;
  padding: 6px 14px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.15s;
}

.new-session-btn-lg:hover { background: var(--color-accent-light); }

.session-item {
  display: flex;
  align-items: center;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 2px;
  gap: 6px;
}

.session-item:hover { background: #f0f0f0; }

.session-item.active {
  background: var(--color-accent);
  color: #fff;
}

.session-info { flex: 1; min-width: 0; }

.session-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: inherit;
}

.session-preview {
  font-size: 11px;
  color: #a1a1aa;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 2px;
}

.session-item.active .session-preview { color: rgba(255,255,255,0.6); }

.session-more {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.15s;
  color: inherit;
  flex-shrink: 0;
}

.session-item:hover .session-more { opacity: 1; }
.session-more:hover { background: rgba(0,0,0,0.08); }
.session-item.active .session-more:hover { background: rgba(255,255,255,0.12); }

/* ── 聊天主区 ─────────────────────────────────────────────── */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  height: 48px;
  border-bottom: 1px solid #f4f4f5;
  flex-shrink: 0;
}

.chat-title {
  font-size: 14px;
  font-weight: 600;
  color: #18181b;
}

.chat-status {
  font-size: 12px;
  color: #52c41a;
  display: flex;
  align-items: center;
  gap: 5px;
}

.chat-status::before {
  content: '';
  width: 6px;
  height: 6px;
  background: #52c41a;
  border-radius: 50%;
}

.chat-status.typing { color: var(--color-accent); }
.chat-status.typing::before { background: var(--color-accent); animation: blink 1s infinite; }

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* ── 消息列表 ─────────────────────────────────────────────── */
.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px 18px;
  background: #f8fafc;
}

.messages-container::-webkit-scrollbar { width: 6px; }
.messages-container::-webkit-scrollbar-track { background: transparent; }
.messages-container::-webkit-scrollbar-thumb { background: #e4e4e7; border-radius: 3px; }
.messages-container::-webkit-scrollbar-thumb:hover { background: #d4d4d8; }

.msg-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: #a1a1aa;
  font-size: 13px;
}

.welcome-screen {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.welcome-card {
  text-align: center;
  padding: 28px 24px;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #f4f4f5;
  box-shadow: 0 4px 16px rgba(0,0,0,0.05);
  max-width: 300px;
  animation: fadeUp 0.4s ease;
}

@keyframes fadeUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.welcome-icon {
  width: 64px;
  height: 64px;
  background: var(--color-accent-glow);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: var(--color-accent);
  margin: 0 auto 16px;
}

.welcome-card h3 {
  font-size: 16px;
  font-weight: 600;
  color: #18181b;
  margin: 0 0 8px;
}

.welcome-card p {
  font-size: 13px;
  color: #71717a;
  margin: 0 0 18px;
  line-height: 1.5;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.quick-item {
  padding: 7px 12px;
  background: #f4f4f5;
  border-radius: 8px;
  font-size: 12px;
  color: #18181b;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 500;
}

.quick-item:hover {
  background: var(--color-accent-glow);
  color: var(--color-accent);
}

/* ── 消息气泡 ─────────────────────────────────────────────── */
.message-item {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  animation: fadeUp 0.25s ease;
}

.message-item.user { flex-direction: row-reverse; }

.msg-avatar { flex-shrink: 0; margin-top: 2px; }

.avatar-user {
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-light) 100%);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  border: 2px solid #e4e4e7;
}

.avatar-ai {
  border: 2px solid var(--color-accent-glow);
  border-radius: 50%;
  background: #f4f4f5;
}

.msg-content { max-width: 72%; }
.message-item.user .msg-content { text-align: right; }

.msg-bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 14px;
  text-align: left;
  max-width: 100%;
  word-break: break-word;
}

/* AI 气泡 */
.message-item.ai .msg-bubble {
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 4px 14px 14px 14px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

/* 用户气泡 */
.message-item.user .msg-bubble {
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-light) 100%);
  color: #fff;
  border-radius: 14px 4px 14px 14px;
  box-shadow: 0 2px 8px rgba(27,53,87,0.25);
}

.msg-text {
  font-size: 14px;
  line-height: 1.65;
  color: inherit;
}

/* Markdown 内容样式 */
.message-item.ai .msg-text :deep(p) { margin: 0 0 8px; }
.message-item.ai .msg-text :deep(p:last-child) { margin-bottom: 0; }
.message-item.ai .msg-text :deep(pre) {
  background: #f4f4f5;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-size: 13px;
  margin: 8px 0;
  border: 1px solid #e4e4e7;
}
.message-item.ai .msg-text :deep(code) {
  background: var(--color-accent-glow);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--color-accent);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}
.message-item.ai .msg-text :deep(pre code) {
  background: none;
  padding: 0;
  color: #18181b;
}
.message-item.ai .msg-text :deep(ul), .message-item.ai .msg-text :deep(ol) {
  padding-left: 18px;
  margin: 6px 0;
}
.message-item.ai .msg-text :deep(li) { margin-bottom: 4px; }
.message-item.ai .msg-text :deep(blockquote) {
  border-left: 3px solid var(--color-accent);
  margin: 8px 0;
  padding: 4px 12px;
  color: #71717a;
  background: var(--color-accent-glow);
  border-radius: 0 6px 6px 0;
}
.message-item.ai .msg-text :deep(h1),
.message-item.ai .msg-text :deep(h2),
.message-item.ai .msg-text :deep(h3) {
  font-weight: 600;
  color: #18181b;
  margin: 10px 0 6px;
}
.message-item.ai .msg-text :deep(a) { color: var(--color-accent); text-decoration: underline; }
.message-item.ai .msg-text :deep(hr) { border: none; border-top: 1px solid #e4e4e7; margin: 10px 0; }
.message-item.ai .msg-text :deep(table) { border-collapse: collapse; width: 100%; font-size: 13px; }
.message-item.ai .msg-text :deep(th), .message-item.ai .msg-text :deep(td) {
  border: 1px solid #e4e4e7;
  padding: 6px 10px;
}
.message-item.ai .msg-text :deep(th) { background: #f4f4f5; font-weight: 600; }

.message-item.user .msg-text :deep(*) { color: #fff; }

.typing-dots {
  display: flex;
  gap: 4px;
  padding: 4px 0 2px;
}

.typing-dots span {
  width: 5px;
  height: 5px;
  background: #a1a1aa;
  border-radius: 50%;
  animation: typingBounce 1.2s infinite ease-in-out both;
}

.typing-dots span:nth-child(1) { animation-delay: -0.32s; }
.typing-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes typingBounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

.msg-time {
  font-size: 11px;
  color: #a1a1aa;
  margin-top: 4px;
}

.message-item.user .msg-time { text-align: right; }

/* ── 输入区 ───────────────────────────────────────────────── */
.input-area {
  padding: 12px 16px 10px;
  border-top: 1px solid #f4f4f5;
  background: #fff;
  flex-shrink: 0;
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}

.input-row :deep(.ant-input) {
  flex: 1;
  border-radius: 10px;
  border: 1.5px solid #e4e4e7;
  padding: 10px 14px;
  font-size: 14px;
  resize: none;
  background: #fafafa;
  color: #18181b;
  line-height: 1.5;
  transition: border-color 0.15s, box-shadow 0.15s;
  font-family: inherit;
}

.input-row :deep(.ant-input:focus) {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-glow);
  background: #fff;
  outline: none;
}

.input-row :deep(.ant-input:hover) { border-color: #8ba7c4; }

.send-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 10px;
  background: var(--color-accent);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  transition: background 0.15s, transform 0.15s;
}

.send-btn:hover:not(:disabled) { background: var(--color-accent-light); transform: translateY(-1px); }
.send-btn:active:not(:disabled) { transform: translateY(0); }
.send-btn:disabled { background: #e4e4e7; cursor: not-allowed; color: #a1a1aa; }

.input-hint {
  font-size: 11px;
  color: #a1a1aa;
  margin-top: 6px;
  text-align: right;
}

/* ── 调整大小手柄 ─────────────────────────────────────────── */
.resize-handle {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, #d4d4d8 50%);
  border-radius: 0 0 16px 0;
}

.resize-handle:hover { background: linear-gradient(135deg, transparent 50%, var(--color-accent) 50%); }

/* ── 响应式 ───────────────────────────────────────────────── */
@media (max-width: 768px) {
  .ai-float-window {
    width: 94vw !important;
    height: 78vh !important;
    left: 3vw !important;
    top: 11vh !important;
  }

  .session-sidebar { width: 140px; }
  .session-name { font-size: 12px; }
}

@media (max-width: 480px) {
  .ai-float-window {
    width: 98vw !important;
    height: 82vh !important;
    left: 1vw !important;
    top: 9vh !important;
    border-radius: 12px;
  }

  .session-sidebar { width: 120px; }
  .msg-content { max-width: 82%; }
}
</style>
