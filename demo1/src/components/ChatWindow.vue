<script setup>
import { ref, watch, nextTick, computed, onUnmounted } from 'vue'
import { SendOutlined } from '@ant-design/icons-vue'
import UserAvatar from './UserAvatar.vue'
import { getMessageHistory, sendMessage, markConversationRead } from '../utils/api'
import { useUserStore } from '../store/userLogin'
import { message as antMessage } from 'ant-design-vue'

const props = defineProps({
  conversationId: { type: [Number, String], required: true },
  otherUserId: { type: [Number, String], default: null },
  otherUser: { type: Object, default: () => ({ username: '', avatarUrl: '' }) }
})

const emit = defineEmits(['sent'])

const userStore = useUserStore()
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const sending = ref(false)
const messageListRef = ref(null)

// 将会话中对方发来的消息标记为已读（有未读时才请求）
const markReadIfNeeded = async () => {
  const hasUnread = messages.value.some(
    (m) => Number(m.senderId) !== Number(userStore.id) && !m.isRead
  )
  if (!hasUnread) return
  try {
    await markConversationRead(props.conversationId)
    messages.value.forEach((m) => {
      if (Number(m.senderId) !== Number(userStore.id)) m.isRead = 1
    })
  } catch (_) {}
}

// 获取消息历史（后端按时间倒序返回，这里反转成旧→新展示）
// silent=true 时用于轮询，不触发 loading 避免列表闪烁
const fetchMessages = async (silent = false) => {
  if (!props.conversationId) return
  const prevCount = messages.value.length
  if (!silent) loading.value = true
  try {
    const res = await getMessageHistory({ conversationId: props.conversationId })
    const records = res.data?.records || res.data || []
    messages.value = Array.isArray(records) ? [...records].reverse() : []
    await markReadIfNeeded()
  } catch (_) {
    if (!silent) messages.value = []
  } finally {
    if (!silent) loading.value = false
    // 首次加载或消息数变化时才滚动到底部，避免打断用户回看历史
    if (!silent || messages.value.length !== prevCount) scrollToBottom()
  }
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) {
      messageListRef.value.scrollTop = messageListRef.value.scrollHeight
    }
  })
}

// 发送消息
const handleSend = async () => {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  sending.value = true
  try {
    const res = await sendMessage({
      receiverId: props.otherUserId,
      content: text
    })
    if (res.data) {
      messages.value.push(res.data)
    } else {
      messages.value.push({
        id: Date.now(),
        senderId: userStore.id,
        content: text,
        createTime: new Date().toISOString()
      })
    }
    inputText.value = ''
    scrollToBottom()
    // 通知父组件更新会话列表的最新消息
    emit('sent', res.data || { content: text, createTime: new Date().toISOString() })
  } catch (_) {
    antMessage.error('发送失败，请重试')
  } finally {
    sending.value = false
  }
}

// Enter 发送，Shift+Enter 换行
const handleKeydown = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

const isSelf = (msg) => Number(msg.senderId) === Number(userStore.id)

// 按日期分组消息
const groupedMessages = computed(() => {
  const groups = []
  let lastDate = ''
  for (const msg of messages.value) {
    const d = new Date(msg.createTime)
    const dateStr = `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`
    if (dateStr !== lastDate) {
      groups.push({ type: 'date', date: dateStr })
      lastDate = dateStr
    }
    groups.push({ type: 'msg', data: msg })
  }
  return groups
})

// 格式化日期标题
const formatDateLabel = (dateStr) => {
  const d = new Date(dateStr)
  const now = new Date()
  const today = `${now.getFullYear()}-${(now.getMonth()+1).toString().padStart(2,'0')}-${now.getDate().toString().padStart(2,'0')}`
  const yesterday = new Date(now.getTime() - 86400000)
  const ydStr = `${yesterday.getFullYear()}-${(yesterday.getMonth()+1).toString().padStart(2,'0')}-${yesterday.getDate().toString().padStart(2,'0')}`
  if (dateStr === today) return '今天'
  if (dateStr === ydStr) return '昨天'
  return `${d.getMonth()+1}月${d.getDate()}日`
}

// 格式化消息时间
const formatMsgTime = (timeStr) => {
  if (!timeStr) return ''
  const d = new Date(timeStr)
  return `${d.getHours().toString().padStart(2,'0')}:${d.getMinutes().toString().padStart(2,'0')}`
}

// 轮询刷新消息，让对方的回复自动出现
let pollTimer = null
const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(() => {
    if (props.conversationId && document.visibilityState === 'visible') {
      fetchMessages(true)
    }
  }, 5000)
}
const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

watch(() => props.conversationId, (newVal) => {
  if (newVal) {
    fetchMessages()
    startPolling()
  } else {
    stopPolling()
  }
}, { immediate: true })

onUnmounted(stopPolling)
</script>

<template>
  <div class="chat-window">
    <!-- 聊天头部 -->
    <div class="chat-header">
      <span class="chat-header-name">{{ otherUser.username || '私信' }}</span>
    </div>

    <!-- 消息列表 -->
    <div ref="messageListRef" class="message-list">
      <div v-if="loading" class="loading-wrap">
        <a-spin />
      </div>
      <template v-else>
        <div v-if="messages.length === 0" class="empty-messages">
          <p class="empty-text">暂无聊天记录</p>
          <p class="empty-hint">发送一条消息开始对话吧</p>
        </div>

        <template v-for="(item, idx) in groupedMessages" :key="idx">
          <!-- 日期分割线 -->
          <div v-if="item.type === 'date'" class="date-divider">
            <span class="date-label">{{ formatDateLabel(item.date) }}</span>
          </div>

          <!-- 消息气泡 -->
          <div
            v-else
            class="message-row"
            :class="{ 'msg-self': isSelf(item.data), 'msg-other': !isSelf(item.data) }"
          >
            <!-- 头像统一放第一个 DOM 位置：对方消息(row)头像在左；自己消息(row-reverse)头像在右 -->
            <UserAvatar v-if="!isSelf(item.data)" :size="40" :src="otherUser.avatarUrl" class="msg-avatar" />
            <UserAvatar v-else :size="40" :src="userStore.avatarUrl" class="msg-avatar" />

            <div class="msg-content-wrap">
              <div class="message-bubble" :class="{ 'bubble-self': isSelf(item.data), 'bubble-other': !isSelf(item.data) }">
                {{ item.data.content }}
              </div>
              <span class="msg-time">{{ formatMsgTime(item.data.createTime) }}</span>
            </div>
          </div>
        </template>
      </template>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <a-textarea
        v-model:value="inputText"
        placeholder="发送消息..."
        :auto-size="{ minRows: 1, maxRows: 4 }"
        :maxlength="2000"
        @keydown="handleKeydown"
        class="chat-textarea"
        allow-clear
      />
      <a-button
        type="primary"
        :loading="sending"
        :disabled="!inputText.trim()"
        @click="handleSend"
        class="send-btn"
      >
        发送
      </a-button>
    </div>
  </div>
</template>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f0f2f5;
  overflow: hidden;
}

/* ===== 头部 ===== */
.chat-header {
  display: flex;
  align-items: center;
  padding: 14px 20px;
  background: #ffffff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.chat-header-name {
  font-weight: 600;
  font-size: 1rem;
  color: #1a1a1a;
}

/* ===== 消息列表 ===== */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  -webkit-overflow-scrolling: touch;
}

.loading-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.empty-messages {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.empty-text {
  font-size: 0.9375rem;
  color: #999;
  margin: 0 0 6px;
}

.empty-hint {
  font-size: 0.8125rem;
  color: #bbb;
  margin: 0;
}

/* ===== 日期分割线 ===== */
.date-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px 0 8px;
}

.date-label {
  font-size: 0.75rem;
  color: #b0b0b0;
  background: #dadada;
  padding: 2px 10px;
  border-radius: 4px;
  line-height: 1.6;
}

/* ===== 消息行 ===== */
.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 4px;
}

.msg-self {
  flex-direction: row-reverse;
}

.msg-other {
  flex-direction: row;
}

.msg-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.msg-content-wrap {
  display: flex;
  flex-direction: column;
  max-width: 65%;
}

.msg-self .msg-content-wrap {
  align-items: flex-end;
}

.msg-other .msg-content-wrap {
  align-items: flex-start;
}

.msg-time {
  font-size: 0.6875rem;
  color: #b0b0b0;
  margin-top: 4px;
  padding: 0 4px;
}

/* ===== 消息气泡（微信风格） ===== */
.message-bubble {
  padding: 10px 14px;
  border-radius: 6px;
  word-break: break-word;
  line-height: 1.6;
  font-size: 0.9375rem;
  position: relative;
}

/* 对方消息：白色气泡 + 小三角 */
.bubble-other {
  background: #ffffff;
  color: #333;
  border-radius: 6px;
  position: relative;
}

.bubble-other::before {
  content: '';
  position: absolute;
  left: -6px;
  top: 12px;
  width: 0;
  height: 0;
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
  border-right: 6px solid #ffffff;
}

/* 自己消息：绿色气泡 + 小三角 */
.bubble-self {
  background: #95ec69;
  color: #333;
  border-radius: 6px;
  position: relative;
}

.bubble-self::before {
  content: '';
  position: absolute;
  right: -6px;
  top: 12px;
  width: 0;
  height: 0;
  border-top: 6px solid transparent;
  border-bottom: 6px solid transparent;
  border-left: 6px solid #95ec69;
}

/* ===== 输入区域 ===== */
.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 12px 20px;
  background: #f5f5f5;
  border-top: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.chat-textarea {
  flex: 1;
  border-radius: 6px !important;
  resize: none;
  font-size: 0.9375rem;
  border: 1px solid #ddd !important;
  background: #fff !important;
}

.chat-textarea:focus {
  border-color: #95ec69 !important;
  box-shadow: 0 0 0 2px rgba(149, 236, 105, 0.2) !important;
}

.send-btn {
  flex-shrink: 0;
  height: 36px;
  padding: 0 20px;
  border-radius: 6px !important;
  background: #07c160 !important;
  border-color: #07c160 !important;
  font-weight: 500;
}

.send-btn:hover {
  background: #06ad56 !important;
  border-color: #06ad56 !important;
}

.send-btn:disabled {
  background: #a0d9b1 !important;
  border-color: #a0d9b1 !important;
}
</style>
