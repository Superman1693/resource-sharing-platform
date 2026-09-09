<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { BellOutlined } from '@ant-design/icons-vue'
import UserAvatar from './UserAvatar.vue'
import { useUserStore } from '../store/userLogin'
import {
  getNotificationList,
  getUnreadNotificationCount,
  markNotificationRead,
  markAllNotificationsRead,
} from '../utils/api'

const router = useRouter()
const userStore = useUserStore()

const unreadCount = ref(0)
const notifications = ref([])
const loading = ref(false)
const open = ref(false)

const fetchUnreadCount = async () => {
  if (!userStore.isLogin) return
  try {
    const res = await getUnreadNotificationCount()
    unreadCount.value = res.data || 0
  } catch (_) {}
}

const fetchNotifications = async () => {
  if (!userStore.isLogin) return
  loading.value = true
  try {
    const res = await getNotificationList({ page: 1, pageSize: 10 })
    notifications.value = res.data?.records || []
  } catch (_) {
  } finally {
    loading.value = false
  }
}

watch(open, (val) => {
  if (val) fetchNotifications()
})

const handleMarkRead = async (item) => {
  if (item.isRead === 1) return
  try {
    await markNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch (_) {}
}

// 根据通知类型跳转到对应页面
const goNotificationTarget = (item) => {
  if (!item || item.targetId == null) return
  const type = item.type || ''
  if (type === 'like_note' || type === 'comment_note' || type === 'reply_comment') {
    router.push(`/user/noteDetail/${item.targetId}`)
  } else if (type === 'join_star') {
    router.push(`/user/starDetail/${item.targetId}`)
  }
  // report_comment 等其余类型无用户侧页面，仅标已读
}

const handleNotificationClick = (item) => {
  open.value = false
  goNotificationTarget(item)
  handleMarkRead(item)
}

const handleMarkAllRead = async () => {
  try {
    await markAllNotificationsRead()
    notifications.value.forEach(n => { n.isRead = 1 })
    unreadCount.value = 0
  } catch (_) {}
}

let timer = null
onMounted(() => {
  fetchUnreadCount()
  timer = setInterval(fetchUnreadCount, 60000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <a-dropdown v-model:open="open" trigger="click" placement="bottomRight">
    <div class="bell-wrap" @click.stop>
      <a-badge :count="unreadCount" :overflow-count="99">
        <BellOutlined class="bell-icon" />
      </a-badge>
    </div>

    <template #overlay>
      <div class="notif-panel" @click.stop>
        <div class="notif-header">
          <span class="notif-title">通知</span>
          <a v-if="unreadCount > 0" class="notif-read-all" @click="handleMarkAllRead">全部已读</a>
        </div>

        <a-spin :spinning="loading">
          <div v-if="notifications.length === 0" class="notif-empty">暂无通知</div>
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notif-item"
            :class="{ unread: item.isRead === 0, clickable: item.targetId != null }"
            @click="handleNotificationClick(item)"
          >
            <UserAvatar :size="32" :src="item.senderAvatar" class="notif-avatar" />
            <div class="notif-body">
              <p class="notif-content">{{ item.content }}</p>
              <span class="notif-time">{{ new Date(item.createTime).toLocaleString() }}</span>
            </div>
            <span v-if="item.isRead === 0" class="notif-dot" />
          </div>
        </a-spin>
      </div>
    </template>
  </a-dropdown>
</template>

<style scoped>
.bell-wrap {
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast);
}

.bell-wrap:hover {
  background: var(--color-bg-warm);
}

.bell-icon {
  font-size: 18px;
  color: var(--color-text-muted);
  transition: color var(--duration-fast);
}

.bell-wrap:hover .bell-icon {
  color: var(--color-text);
}

.notif-panel {
  width: 360px;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  border: 1px solid var(--color-border-light);
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--color-border-light);
}

.notif-title {
  font-weight: 600;
  font-size: 0.9375rem;
  color: var(--color-primary);
}

.notif-read-all {
  font-size: 0.8125rem;
  color: var(--color-accent);
  cursor: pointer;
  transition: color var(--duration-fast);
}

.notif-read-all:hover {
  color: var(--color-accent-light);
}

.notif-empty {
  text-align: center;
  padding: 40px 20px;
  color: var(--color-text-muted);
  font-size: 0.875rem;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background var(--duration-fast);
  position: relative;
}

.notif-item:hover {
  background: var(--color-bg);
}

.notif-item.unread {
  background: var(--color-accent-glow);
}

.notif-avatar {
  flex-shrink: 0;
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-content {
  font-size: 0.875rem;
  color: var(--color-text);
  line-height: 1.5;
  margin: 0 0 4px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notif-time {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.notif-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-accent);
  flex-shrink: 0;
  margin-top: 4px;
}
</style>
