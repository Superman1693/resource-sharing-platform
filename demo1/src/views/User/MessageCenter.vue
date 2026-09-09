<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MessageOutlined } from '@ant-design/icons-vue'
import { getConversationList, getUnreadMessageCount, markConversationRead, getOrCreateConversation } from '../../utils/api'
import { useUserStore } from '../../store/userLogin'
import ChatWindow from '../../components/ChatWindow.vue'
import UserAvatar from '../../components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const conversations = ref([])
const loading = ref(false)
const activeConversation = ref(null)
const totalUnread = ref(0)

// 获取会话列表
const fetchConversations = async () => {
  loading.value = true
  try {
    const res = await getConversationList({ page: 1, pageSize: 50 })
    conversations.value = res.data?.records || res.data || []
  } catch (_) {
    conversations.value = []
  } finally {
    loading.value = false
  }
}

// 获取未读消息数
const fetchUnreadCount = async () => {
  if (!userStore.isLogin) return
  try {
    const res = await getUnreadMessageCount()
    totalUnread.value = res.data || 0
  } catch (_) {}
}

// 选中会话：本地清零未读 + 通知服务端标记已读
const selectConversation = (conv) => {
  activeConversation.value = conv
  if (conv.unreadCount > 0) {
    totalUnread.value = Math.max(0, totalUnread.value - conv.unreadCount)
    conv.unreadCount = 0
    markConversationRead(conv.id).catch(() => {})
  }
}

// 通过 ?to=用户ID 发起新会话（来自用户主页/笔记页的"私信"按钮）
const openConversationWith = async (targetUserId) => {
  try {
    const res = await getOrCreateConversation(targetUserId)
    const conv = res.data
    if (!conv) return
    const existing = conversations.value.find((c) => c.id === conv.id)
    if (!existing) conversations.value.unshift(conv)
    selectConversation(existing || conv)
  } catch (err) {
    // 后端业务错误（如用户不存在）由拦截器提示，这里只兜底
  } finally {
    // 清掉 query，避免刷新后重复创建/选中
    router.replace({ path: route.path })
  }
}

// 发送消息后同步更新左侧会话列表（最新消息 + 置顶）
const onMessageSent = (msg) => {
  const conv = activeConversation.value
  if (!conv || !msg) return
  conv.lastMessage = msg.content?.length > 100 ? msg.content.slice(0, 100) + '...' : msg.content
  conv.lastMessageTime = msg.createTime
  conversations.value = [
    conv,
    ...conversations.value.filter((c) => c.id !== conv.id),
  ]
}

// 格式化时间
const formatTime = (timeStr) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  if (isToday) return `${hours}:${minutes}`
  const isThisYear = date.getFullYear() === now.getFullYear()
  const month = (date.getMonth() + 1).toString().padStart(2, '0')
  const day = date.getDate().toString().padStart(2, '0')
  if (isThisYear) return `${month}-${day}`
  return `${date.getFullYear()}-${month}-${day}`
}

let pollTimer = null

onMounted(() => {
  fetchConversations().then(() => {
    const targetUserId = route.query.to
    if (targetUserId) openConversationWith(Number(targetUserId))
  })
  fetchUnreadCount()
  pollTimer = setInterval(fetchUnreadCount, 60000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <div class="message-center">
    <!-- 左侧会话列表 -->
    <div class="conversation-panel">
      <div class="panel-header">
        <h3 class="panel-title">聊天</h3>
        <span v-if="totalUnread > 0" class="unread-badge">{{ totalUnread }}</span>
      </div>

      <div class="conversation-list">
        <a-spin :spinning="loading">
          <div v-if="!loading && conversations.length === 0" class="empty-conversations">
            <MessageOutlined class="empty-icon" />
            <p class="empty-text">暂无会话</p>
            <p class="empty-hint">在用户主页或笔记页点击「发私信」，即可开始对话</p>
          </div>

          <div
            v-for="conv in conversations"
            :key="conv.id"
            class="conversation-item"
            :class="{ active: activeConversation?.id === conv.id }"
            @click="selectConversation(conv)"
          >
            <div class="conv-avatar-wrap">
              <UserAvatar :size="46" :src="conv.otherAvatarUrl" class="conv-avatar">
                {{ (conv.otherUsername || '?').charAt(0) }}
              </UserAvatar>
              <span v-if="conv.unreadCount > 0" class="conv-unread">{{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}</span>
            </div>
            <div class="conv-info">
              <div class="conv-top">
                <span class="conv-name">{{ conv.otherUsername || '用户' }}</span>
                <span class="conv-time">{{ formatTime(conv.lastMessageTime) }}</span>
              </div>
              <div class="conv-last-msg">{{ conv.lastMessage || '暂无消息' }}</div>
            </div>
          </div>
        </a-spin>
      </div>
    </div>

    <!-- 右侧聊天窗口 -->
    <div class="chat-panel">
      <ChatWindow
        v-if="activeConversation"
        :conversation-id="activeConversation.id"
        :other-user-id="activeConversation.otherUserId"
        :other-user="{ username: activeConversation.otherUsername, avatarUrl: activeConversation.otherAvatarUrl }"
        @sent="onMessageSent"
      />
      <div v-else class="chat-empty">
        <div class="chat-empty-inner">
          <svg class="chat-empty-logo" viewBox="0 0 1024 1024" width="80" height="80" fill="#ddd">
            <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z"/>
            <path d="M688 352H336c-17.7 0-32 14.3-32 32v256c0 17.7 14.3 32 32 32h352c17.7 0 32-14.3 32-32V384c0-17.7-14.3-32-32-32zm-32 256H368V416h288v192z"/>
          </svg>
          <p class="chat-empty-text">选择一个会话开始聊天</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message-center {
  display: flex;
  height: calc(100vh - 64px - 72px);
  background: #f0f2f5;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--color-border-light);
}

/* ===== 左侧会话面板 ===== */
.conversation-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-right: 1px solid #e8e8e8;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 20px;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.panel-title {
  font-size: 1.0625rem;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0;
}

.unread-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #fa5151;
  color: #fff;
  font-size: 0.6875rem;
  font-weight: 600;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 空状态 */
.empty-conversations {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 48px;
  color: #ddd;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 0.875rem;
  color: #999;
  margin: 0 0 6px;
}

.empty-hint {
  font-size: 0.75rem;
  color: #bbb;
  margin: 0;
}

/* ===== 会话列表项（微信风格） ===== */
.conversation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 0.5px solid #f0f0f0;
  position: relative;
}

.conversation-item:hover {
  background: #f5f5f5;
}

.conversation-item.active {
  background: #e8e8e8;
}

.conv-avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.conv-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
  font-size: 1rem;
}

.conv-unread {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #fa5151;
  color: #fff;
  font-size: 0.625rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.conv-info {
  flex: 1;
  min-width: 0;
}

.conv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.conv-name {
  font-size: 0.9375rem;
  font-weight: 500;
  color: #1a1a1a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 0.6875rem;
  color: #b0b0b0;
  flex-shrink: 0;
  margin-left: 8px;
}

.conv-last-msg {
  font-size: 0.8125rem;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 右侧聊天面板 ===== */
.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #f0f2f5;
  overflow: hidden;
}

.chat-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-empty-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.chat-empty-logo {
  opacity: 0.3;
}

.chat-empty-text {
  font-size: 0.9375rem;
  color: #999;
  margin: 0;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .message-center {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 64px - 72px);
  }

  .conversation-panel {
    width: 100%;
    max-height: 50vh;
    border-right: none;
    border-bottom: 1px solid #e8e8e8;
  }

  .chat-panel {
    min-height: 400px;
  }
}
</style>
