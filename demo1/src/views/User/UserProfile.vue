<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { EyeOutlined, LikeOutlined, MessageOutlined, ArrowLeftOutlined, TagsOutlined, ToolOutlined, FileTextOutlined, QuestionCircleOutlined, ReadOutlined, FolderOpenOutlined, AppstoreOutlined } from '@ant-design/icons-vue'
import { message as antMessage } from 'ant-design-vue'
import { useUserStore } from '../../store/userLogin'
import { getPublicUser, getNoteList, getResourceList, checkFollowStatus, followUser, unfollowUser } from '../../utils/api'
import UserAvatar from '../../components/UserAvatar.vue'
import { CATEGORY_TEXT_SHORT } from '../../utils/constant'
import { formatDate } from '../../utils/dateUtils'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const userInfo = ref(null)
const notes = ref([])
const resources = ref([])
const activeTab = ref('all')
const followInfo = ref({ isFollowing: false, followingCount: 0, followerCount: 0 })
const categoryText = CATEGORY_TEXT_SHORT

// 内容类型 Tab（全部/文章/问题/笔记 + 资源）
const contentTypeByTab = { all: undefined, article: 'article', question: 'question', note: 'note' }
const resourceTypeText = { document: '文档', video: '视频', code: '代码', other: '其他' }

// 技能标签（逗号分隔 → 数组）
const skillList = computed(() =>
  (userInfo.value?.skills || '').split(/[,，、]/).map((s) => s.trim()).filter(Boolean)
)

const isSelf = computed(() => {
  const targetId = Number(route.params.userId)
  return !targetId || targetId === userStore.id
})

// 加载用户信息
const loadUserInfo = async () => {
  const userId = Number(route.params.userId)
  if (!userId || isSelf.value) {
    router.replace('/user/home')
    return
  }
  try {
    const res = await getPublicUser(userId)
    userInfo.value = res.data || null
  } catch (_) {}
}

// 加载用户笔记（按内容类型 Tab 筛选）
const loadNotes = async () => {
  const userId = Number(route.params.userId)
  if (!userId) return
  loading.value = true
  try {
    const res = await getNoteList({
      userId,
      sortType: 'latest',
      pageSize: 20,
      contentType: contentTypeByTab[activeTab.value],
    })
    const data = res.data || {}
    notes.value = Array.isArray(data.records) ? data.records : []
  } catch (_) {
    notes.value = []
  } finally {
    loading.value = false
  }
}

// 加载用户上传的资源
const loadResources = async () => {
  const userId = Number(route.params.userId)
  if (!userId) return
  loading.value = true
  try {
    const res = await getResourceList({ userId, status: 'enabled' })
    resources.value = Array.isArray(res.data) ? res.data : []
  } catch (_) {
    resources.value = []
  } finally {
    loading.value = false
  }
}

// Tab 切换
const handleTabChange = (key) => {
  activeTab.value = key
  if (key === 'resources') {
    loadResources()
  } else {
    loadNotes()
  }
}

// 打开资源详情
const viewResource = (res) => router.push(`/user/resourceDetail/${res.id}`)

// 检查关注状态
const loadFollowStatus = async () => {
  const userId = Number(route.params.userId)
  if (!userId) return
  try {
    const res = await checkFollowStatus(userId)
    if (res.data) {
      followInfo.value = res.data
    }
  } catch (_) {}
}

// 关注/取消关注
const handleFollow = async () => {
  const userId = Number(route.params.userId)
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  try {
    if (followInfo.value.isFollowing) {
      await unfollowUser(userId)
      followInfo.value.isFollowing = false
      followInfo.value.followerCount = Math.max(0, (followInfo.value.followerCount || 1) - 1)
      antMessage.success('已取消关注')
    } else {
      await followUser(userId)
      followInfo.value.isFollowing = true
      followInfo.value.followerCount = (followInfo.value.followerCount || 0) + 1
      antMessage.success('关注成功')
    }
  } catch (err) {
    antMessage.error(err?.description || err?.message || '操作失败')
  }
}

// 发私信：跳转到私信中心并直接打开与对方的会话
const handleSendMessage = () => {
  const userId = Number(route.params.userId)
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  router.push({ path: '/user/messages', query: { to: userId } })
}

const viewNote = (note) => router.push(`/user/noteDetail/${note.id}`)

onMounted(() => {
  loadUserInfo()
  loadNotes()
  loadFollowStatus()
})

watch(() => route.params.userId, () => {
  if (route.params.userId) {
    loadUserInfo()
    loadNotes()
    loadFollowStatus()
  }
})
</script>

<template>
  <div class="user-profile-page">
    <!-- 返回按钮 -->
    <button class="back-btn" @click="router.back()">
      <ArrowLeftOutlined /> 返回
    </button>

    <!-- 用户信息卡片 -->
    <div class="profile-card" v-if="userInfo">
      <div class="profile-header">
        <UserAvatar :size="72" :src="userInfo.avatarUrl" class="profile-avatar">
          {{ (userInfo.username || '?').charAt(0) }}
        </UserAvatar>
        <div class="profile-info">
          <h2 class="profile-name">{{ userInfo.username || '未知用户' }}</h2>
          <p class="profile-bio">{{ userInfo.bio || '这个人很懒，什么都没写~' }}</p>
          <div v-if="skillList.length > 0" class="profile-skills">
            <TagsOutlined class="skills-icon" />
            <a-tag v-for="skill in skillList" :key="skill" color="blue" class="skill-tag">{{ skill }}</a-tag>
          </div>
          <div v-if="userInfo.services" class="profile-services">
            <ToolOutlined class="skills-icon" />
            <span class="services-text">可提供：{{ userInfo.services }}</span>
          </div>
          <div class="profile-stats">
            <span class="stat">
              <strong>{{ notes.length || 0 }}</strong> 笔记
            </span>
            <span class="stat">
              <strong>{{ followInfo.followingCount || 0 }}</strong> 关注
            </span>
            <span class="stat">
              <strong>{{ followInfo.followerCount || 0 }}</strong> 粉丝
            </span>
          </div>
        </div>
      </div>
      <div class="profile-actions">
        <button
          class="follow-btn"
          :class="{ 'following': followInfo.isFollowing }"
          @click="handleFollow"
        >
          {{ followInfo.isFollowing ? '已关注' : '+ 关注' }}
        </button>
        <button
          v-if="Number(route.params.userId) !== Number(userStore.id)"
          class="msg-btn"
          @click="handleSendMessage"
        >
          <MessageOutlined /> 发私信
        </button>
      </div>
    </div>

    <!-- 用户发布的内容：笔记/问题/文章/资源 Tab -->
    <div class="notes-section">
      <h3 class="section-title">TA 发布的内容</h3>
      <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
        <a-tab-pane key="all">
          <template #tab><AppstoreOutlined /> 全部</template>
        </a-tab-pane>
        <a-tab-pane key="note">
          <template #tab><FileTextOutlined /> 笔记</template>
        </a-tab-pane>
        <a-tab-pane key="question">
          <template #tab><QuestionCircleOutlined /> 问题</template>
        </a-tab-pane>
        <a-tab-pane key="article">
          <template #tab><ReadOutlined /> 文章</template>
        </a-tab-pane>
        <a-tab-pane key="resources">
          <template #tab><FolderOpenOutlined /> 资源</template>
        </a-tab-pane>
      </a-tabs>

      <!-- 资源 Tab -->
      <template v-if="activeTab === 'resources'">
        <a-spin :spinning="loading">
          <div v-if="!loading && resources.length === 0" class="empty-notes">
            <p>暂无资源</p>
          </div>
          <div v-else class="notes-list">
            <div
              v-for="res in resources"
              :key="res.id"
              class="note-card"
              @click="viewResource(res)"
            >
              <div class="note-body">
                <div class="note-top">
                  <span class="note-cat">{{ resourceTypeText[res.resourceType] || '其他' }}</span>
                  <span class="note-time">下载 {{ res.downloadCount || 0 }} 次</span>
                </div>
                <h3 class="note-title">{{ res.name || res.title }}</h3>
                <p class="note-summary">{{ res.description }}</p>
              </div>
            </div>
          </div>
        </a-spin>
      </template>

      <!-- 笔记/问题/文章 Tab -->
      <template v-else>
        <a-spin :spinning="loading">
          <div v-if="!loading && notes.length === 0" class="empty-notes">
            <p>暂无内容</p>
          </div>
          <div v-else class="notes-list">
            <div
              v-for="note in notes"
              :key="note.id"
              class="note-card"
              @click="viewNote(note)"
            >
              <div class="note-body">
                <div class="note-top">
                  <span class="note-cat">{{ categoryText[note.category] || '其他' }}</span>
                  <time class="note-time">{{ formatDate(note.publishTime) }}</time>
                </div>
                <h3 class="note-title">{{ note.title }}</h3>
                <p class="note-summary">{{ note.summary }}</p>
                <div class="note-stats">
                  <span><EyeOutlined /> {{ note.viewCount || 0 }}</span>
                  <span><LikeOutlined /> {{ note.likeCount || 0 }}</span>
                </div>
              </div>
              <img v-if="note.coverImage" :src="note.coverImage" class="note-cover" />
            </div>
          </div>
        </a-spin>
      </template>
    </div>
  </div>
</template>

<style scoped>
.user-profile-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 16px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  margin: 16px 0;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

/* ===== 用户信息卡片 ===== */
.profile-card {
  background: #fff;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  padding: 28px;
  margin-bottom: 24px;
}

.profile-header {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.profile-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
  font-size: 1.5rem;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  font-size: 1.375rem;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 6px;
}

.profile-bio {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  margin: 0 0 12px;
  line-height: 1.6;
}

.profile-skills {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}
.skills-icon {
  color: var(--color-accent);
  margin-right: 2px;
}
.skill-tag {
  margin: 0;
}
.profile-services {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.services-text {
  flex: 1;
  min-width: 0;
}

.profile-stats {
  display: flex;
  gap: 24px;
}

.profile-stats .stat {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.profile-stats .stat strong {
  font-weight: 600;
  color: var(--color-primary);
  margin-right: 4px;
}

.profile-actions {
  display: flex;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--color-border-light);
}

.follow-btn {
  padding: 0 28px;
  height: 38px;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  background: var(--color-accent);
  color: #fff;
  border: none;
}

.follow-btn:hover {
  background: var(--color-accent-light);
}

.follow-btn.following {
  background: transparent;
  color: var(--color-text-secondary);
  border: 1.5px solid var(--color-border);
}

.follow-btn.following:hover {
  border-color: #f87171;
  color: #ef4444;
  background: #fef2f2;
}

.msg-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 24px;
  height: 38px;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  background: transparent;
  color: var(--color-text);
  border: 1.5px solid var(--color-border);
}

.msg-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

/* ===== 笔记列表 ===== */
.notes-section {
  margin-bottom: 40px;
}

.section-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 16px;
}

.empty-notes {
  text-align: center;
  padding: 60px 0;
  color: var(--color-text-muted);
}

.notes-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.note-card {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: all 0.2s;
}

.note-card:hover {
  border-color: var(--color-accent);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  transform: translateY(-2px);
}

.note-body {
  flex: 1;
  min-width: 0;
}

.note-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.note-cat {
  font-size: 0.75rem;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--color-accent-glow);
  color: var(--color-accent);
}

.note-time {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.note-title {
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--color-primary);
  margin: 0 0 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-summary {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin: 0 0 10px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.note-stats {
  display: flex;
  gap: 14px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.note-cover {
  width: 120px;
  height: 90px;
  border-radius: var(--radius-md);
  object-fit: cover;
  flex-shrink: 0;
}

@media (max-width: 640px) {
  .profile-header {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .profile-stats {
    justify-content: center;
  }

  .profile-actions {
    justify-content: center;
  }

  .note-cover {
    display: none;
  }
}
</style>
