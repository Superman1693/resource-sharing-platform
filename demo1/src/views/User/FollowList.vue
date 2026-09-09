<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/userLogin'
import { getFollowList, getFollowerList } from '../../utils/api'
import { message } from 'ant-design-vue'
import { UserOutlined, MessageOutlined } from '@ant-design/icons-vue'
import FollowButton from '../../components/FollowButton.vue'
import UserAvatar from '../../components/UserAvatar.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 当前激活的 tab
const activeTab = ref('follow')

// 关注列表数据
const followList = ref([])
const followTotal = ref(0)
const followPage = ref(1)
const followLoading = ref(false)

// 粉丝列表数据
const followerList = ref([])
const followerTotal = ref(0)
const followerPage = ref(1)
const followerLoading = ref(false)

const pageSize = 20

// 根据路由参数初始化 tab
onMounted(() => {
  if (route.query.tab === 'follower') {
    activeTab.value = 'follower'
  }
  loadFollowList()
  loadFollowerList()
})

// 监听 tab 切换
watch(activeTab, (val) => {
  router.replace({ query: { tab: val } })
})

// 加载关注列表
const loadFollowList = async () => {
  try {
    followLoading.value = true
    const res = await getFollowList({
      page: followPage.value,
      pageSize
    })
    const data = res.data || res
    followList.value = data.records || data.list || data || []
    followTotal.value = data.total || followList.value.length
  } catch (err) {
    console.error('获取关注列表失败', err)
    message.error('获取关注列表失败')
  } finally {
    followLoading.value = false
  }
}

// 加载粉丝列表
const loadFollowerList = async () => {
  try {
    followerLoading.value = true
    const res = await getFollowerList({
      page: followerPage.value,
      pageSize
    })
    const data = res.data || res
    followerList.value = data.records || data.list || data || []
    followerTotal.value = data.total || followerList.value.length
  } catch (err) {
    console.error('获取粉丝列表失败', err)
    message.error('获取粉丝列表失败')
  } finally {
    followerLoading.value = false
  }
}

// 关注/取关回调
const handleFollow = () => {
  // 刷新列表以更新状态
  loadFollowList()
  loadFollowerList()
}

const handleUnfollow = () => {
  loadFollowList()
  loadFollowerList()
}

// 分页变化
const onFollowPageChange = (page) => {
  followPage.value = page
  loadFollowList()
}

const onFollowerPageChange = (page) => {
  followerPage.value = page
  loadFollowerList()
}

// 跳转用户主页
const goToUser = (userId) => {
  router.push(`/user/user/${userId}`)
}

// 发私信：跳转到私信中心并直接打开与对方的会话
const handleSendMessage = (userId) => {
  router.push({ path: '/user/messages', query: { to: userId } })
}
</script>

<template>
  <div class="follow-list-page">
    <!-- 顶部统计 -->
    <div class="stats-header">
      <div class="stat-item" :class="{ 'stat-item--active': activeTab === 'follow' }" @click="activeTab = 'follow'">
        <span class="stat-number">{{ followTotal }}</span>
        <span class="stat-label">关注</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item" :class="{ 'stat-item--active': activeTab === 'follower' }" @click="activeTab = 'follower'">
        <span class="stat-number">{{ followerTotal }}</span>
        <span class="stat-label">粉丝</span>
      </div>
    </div>

    <!-- Tabs 切换 -->
    <a-tabs v-model:activeKey="activeTab" class="follow-tabs">
      <!-- 关注 Tab -->
      <a-tab-pane key="follow" tab="我的关注">
        <div class="list-container">
          <a-spin :spinning="followLoading">
            <div v-if="followList.length === 0 && !followLoading" class="empty-state">
              <UserOutlined class="empty-icon" />
              <p>还没有关注任何人</p>
            </div>
            <div v-else class="user-list">
              <div v-for="user in followList" :key="user.id || user.userId" class="user-item">
                <div class="user-info" @click="goToUser(user.id || user.userId)">
                  <UserAvatar
                    :size="48"
                    :src="user.avatarUrl || user.avatar"
                    class="user-avatar"
                  >
                    {{ (user.username || user.userName || '?').charAt(0) }}
                  </UserAvatar>
                  <div class="user-details">
                    <span class="user-name">{{ user.username || user.userName || '未知用户' }}</span>
                    <span class="user-bio">{{ user.bio || user.signature || '这个人很懒，什么都没写~' }}</span>
                  </div>
                </div>
                <div class="user-actions">
                  <button class="msg-btn" @click.stop="handleSendMessage(user.id || user.userId)" title="发私信">
                    <MessageOutlined />
                  </button>
                  <FollowButton
                    :userId="user.id || user.userId"
                    :initialFollowing="true"
                    @follow="handleFollow"
                    @unfollow="handleUnfollow"
                  />
                </div>
              </div>
            </div>
          </a-spin>

          <!-- 分页 -->
          <div class="pagination-wrapper" v-if="followTotal > pageSize">
            <a-pagination
              v-model:current="followPage"
              :total="followTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onFollowPageChange"
            />
          </div>
        </div>
      </a-tab-pane>

      <!-- 粉丝 Tab -->
      <a-tab-pane key="follower" tab="我的粉丝">
        <div class="list-container">
          <a-spin :spinning="followerLoading">
            <div v-if="followerList.length === 0 && !followerLoading" class="empty-state">
              <UserOutlined class="empty-icon" />
              <p>还没有粉丝</p>
            </div>
            <div v-else class="user-list">
              <div v-for="user in followerList" :key="user.id || user.userId" class="user-item">
                <div class="user-info" @click="goToUser(user.id || user.userId)">
                  <UserAvatar
                    :size="48"
                    :src="user.avatarUrl || user.avatar"
                    class="user-avatar"
                  >
                    {{ (user.username || user.userName || '?').charAt(0) }}
                  </UserAvatar>
                  <div class="user-details">
                    <span class="user-name">{{ user.username || user.userName || '未知用户' }}</span>
                    <span class="user-bio">{{ user.bio || user.signature || '这个人很懒，什么都没写~' }}</span>
                  </div>
                </div>
                <div class="user-actions">
                  <button class="msg-btn" @click.stop="handleSendMessage(user.id || user.userId)" title="发私信">
                    <MessageOutlined />
                  </button>
                  <FollowButton
                    :userId="user.id || user.userId"
                    :initialFollowing="user.isFollowing || false"
                    @follow="handleFollow"
                    @unfollow="handleUnfollow"
                  />
                </div>
              </div>
            </div>
          </a-spin>

          <!-- 分页 -->
          <div class="pagination-wrapper" v-if="followerTotal > pageSize">
            <a-pagination
              v-model:current="followerPage"
              :total="followerTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onFollowerPageChange"
            />
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<style scoped>
.follow-list-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 16px;
}

/* 顶部统计 */
.stats-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  padding: 28px 0 20px;
  background: #ffffff;
  border-radius: var(--radius-lg, 12px);
  margin-bottom: 20px;
  border: 1px solid var(--color-border-light, #f1f5f9);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 8px 24px;
  border-radius: var(--radius-md, 8px);
  transition: all 0.25s var(--ease-out, ease);
}

.stat-item:hover {
  background: var(--color-bg-warm, #f8fafc);
}

.stat-item--active {
  background: var(--color-accent-glow, rgba(99, 102, 241, 0.08));
}

.stat-number {
  font-family: var(--font-display, sans-serif);
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--color-primary, #1e293b);
  line-height: 1.2;
}

.stat-item--active .stat-number {
  color: var(--color-accent, #6366f1);
}

.stat-label {
  font-size: 0.8125rem;
  color: var(--color-text-muted, #94a3b8);
  font-weight: 500;
}

.stat-divider {
  width: 1px;
  height: 40px;
  background: var(--color-border, #e2e8f0);
}

/* Tabs */
.follow-tabs {
  background: #ffffff;
  border-radius: var(--radius-lg, 12px);
  border: 1px solid var(--color-border-light, #f1f5f9);
  padding: 0 24px;
  min-height: 400px;
}

.follow-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.follow-tabs :deep(.ant-tabs-tab) {
  font-size: 0.9375rem;
  font-weight: 500;
  padding: 16px 0;
}

.follow-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: var(--color-accent, #6366f1);
  font-weight: 600;
}

.follow-tabs :deep(.ant-tabs-ink-bar) {
  background: var(--color-accent, #6366f1);
}

/* 列表容器 */
.list-container {
  padding: 16px 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: var(--color-text-muted, #94a3b8);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 16px;
  opacity: 0.4;
}

.empty-state p {
  font-size: 0.9375rem;
  margin: 0;
}

/* 用户列表 */
.user-list {
  display: flex;
  flex-direction: column;
}

.user-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border-light, #f1f5f9);
  transition: background 0.2s var(--ease-out, ease);
}

.user-item:last-child {
  border-bottom: none;
}

.user-item:hover {
  background: var(--color-bg-warm, #f8fafc);
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: var(--radius-md, 8px);
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.msg-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px solid var(--color-border, #e2e8f0);
  border-radius: 50%;
  background: transparent;
  color: var(--color-text-muted, #94a3b8);
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.msg-btn:hover {
  border-color: var(--color-accent, #6366f1);
  color: var(--color-accent, #6366f1);
  background: var(--color-accent-glow, rgba(99, 102, 241, 0.08));
}

.user-info {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.user-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--color-accent, #6366f1), var(--color-accent-light, #818cf8));
  color: #ffffff;
  font-weight: 600;
}

.user-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.user-name {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-primary, #1e293b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-bio {
  font-size: 0.8125rem;
  color: var(--color-text-muted, #94a3b8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 24px 0 8px;
}

.pagination-wrapper :deep(.ant-pagination-item-active) {
  border-color: var(--color-accent, #6366f1);
}

.pagination-wrapper :deep(.ant-pagination-item-active a) {
  color: var(--color-accent, #6366f1);
}

/* 响应式 */
@media (max-width: 640px) {
  .stats-header {
    gap: 16px;
  }

  .stat-item {
    padding: 8px 16px;
  }

  .stat-number {
    font-size: 1.375rem;
  }

  .follow-tabs {
    padding: 0 16px;
  }

  .user-item {
    padding: 12px 0;
  }
}
</style>
