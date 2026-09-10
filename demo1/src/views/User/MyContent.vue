<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../../store/userLogin'
import { getMyNotes, getMyResources, getMyComments, getMyLikes, getMyCollections } from '../../utils/api'
import UserAvatar from '../../components/UserAvatar.vue'
import { message } from 'ant-design-vue'
import {
  FileTextOutlined,
  FolderOpenOutlined,
  CommentOutlined,
  LikeOutlined,
  RightOutlined,
  StarOutlined,
} from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const TAB_KEYS = ['notes', 'resources', 'comments', 'likes', 'collections']
const pageSize = 10

// 当前激活的 tab（从路由 query 初始化，白名单校验）
const activeTab = ref(TAB_KEYS.includes(route.query.tab) ? route.query.tab : 'notes')

// ===== 各 tab 数据状态 =====
const notes = ref([])
const notesTotal = ref(0)
const notesPage = ref(1)
const notesLoading = ref(false)
// 笔记状态筛选：''-全部 draft-草稿 scheduled-定时中 published-已发布
const notesStatus = ref('')

const resources = ref([])
const resourcesTotal = ref(0)
const resourcesPage = ref(1)
const resourcesLoading = ref(false)

const comments = ref([])
const commentsTotal = ref(0)
const commentsPage = ref(1)
const commentsLoading = ref(false)

const likes = ref([])
const likesTotal = ref(0)
const likesPage = ref(1)
const likesLoading = ref(false)

const collections = ref([])
const collectionsTotal = ref(0)
const collectionsPage = ref(1)
const collectionsLoading = ref(false)

// 懒加载标记：切到某 tab 才首次加载
const loadedTabs = new Set()

// ===== 加载函数 =====
// 我的笔记（按 status 筛选：草稿/定时中/已发布，走 /note/my 接口）
const loadNotes = async () => {
  try {
    notesLoading.value = true
    const res = await getMyNotes({
      status: notesStatus.value || undefined,
      page: notesPage.value,
      pageSize,
    })
    const data = res.data || {}
    notes.value = Array.isArray(data.records) ? data.records : []
    notesTotal.value = data.total || notes.value.length
  } catch (err) {
    console.error('获取我的笔记失败', err)
    message.error('获取我的笔记失败')
  } finally {
    notesLoading.value = false
  }
}

// 切换笔记状态筛选
const onNotesStatusChange = () => {
  notesPage.value = 1
  loadNotes()
}

// 我的资源
const loadResources = async () => {
  try {
    resourcesLoading.value = true
    const res = await getMyResources({ page: resourcesPage.value, pageSize })
    const data = res.data || {}
    resources.value = data.records || []
    resourcesTotal.value = data.total || resources.value.length
  } catch (err) {
    console.error('获取我的资源失败', err)
    message.error('获取我的资源失败')
  } finally {
    resourcesLoading.value = false
  }
}

// 我的评论（含回复）
const loadComments = async () => {
  try {
    commentsLoading.value = true
    const res = await getMyComments({ page: commentsPage.value, pageSize })
    const data = res.data || {}
    comments.value = data.records || []
    commentsTotal.value = data.total || comments.value.length
  } catch (err) {
    console.error('获取我的评论失败', err)
    message.error('获取我的评论失败')
  } finally {
    commentsLoading.value = false
  }
}

// 我的获赞
const loadLikes = async () => {
  try {
    likesLoading.value = true
    const res = await getMyLikes({ page: likesPage.value, pageSize })
    const data = res.data || {}
    likes.value = data.records || []
    likesTotal.value = data.total || likes.value.length
  } catch (err) {
    console.error('获取我的获赞失败', err)
    message.error('获取我的获赞失败')
  } finally {
    likesLoading.value = false
  }
}

// 我的收藏
const loadCollections = async () => {
  try {
    collectionsLoading.value = true
    const res = await getMyCollections({ page: collectionsPage.value, pageSize })
    const data = res.data || {}
    collections.value = data.records || []
    collectionsTotal.value = data.total || collections.value.length
  } catch (err) {
    console.error('获取我的收藏失败', err)
    message.error('获取我的收藏失败')
  } finally {
    collectionsLoading.value = false
  }
}

// 首次进入：加载当前 tab
onMounted(() => {
  loadedTabs.add(activeTab.value)
  loadTab(activeTab.value)
})

// tab 切换：同步路由 + 懒加载
watch(activeTab, (val) => {
  router.replace({ query: { tab: val } })
  if (!loadedTabs.has(val)) {
    loadedTabs.add(val)
    loadTab(val)
  }
})

// 从数据看板再次点击卡片时（组件未重建），跟随路由 query 切换
watch(() => route.query.tab, (val) => {
  if (TAB_KEYS.includes(val) && val !== activeTab.value) {
    activeTab.value = val
  }
})

const loadTab = (tab) => {
  if (tab === 'notes') loadNotes()
  else if (tab === 'resources') loadResources()
  else if (tab === 'comments') loadComments()
  else if (tab === 'likes') loadLikes()
  else if (tab === 'collections') loadCollections()
}

// ===== 分页 =====
const onNotesPageChange = (page) => { notesPage.value = page; loadNotes() }
const onResourcesPageChange = (page) => { resourcesPage.value = page; loadResources() }
const onCommentsPageChange = (page) => { commentsPage.value = page; loadComments() }
const onLikesPageChange = (page) => { likesPage.value = page; loadLikes() }
const onCollectionsPageChange = (page) => { collectionsPage.value = page; loadCollections() }

// ===== 跳转 =====
const goNote = (noteId) => {
  if (noteId) router.push(`/user/noteDetail/${noteId}`)
}
const goResource = (id) => {
  if (id) router.push(`/user/resourceDetail/${id}`)
}
</script>

<template>
  <div class="my-content-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">我的内容</h2>
      <p class="page-subtitle">管理你发布的笔记、资源、评论和收到的点赞</p>
    </div>

    <a-tabs v-model:activeKey="activeTab" class="content-tabs">
      <!-- 我的笔记 -->
      <a-tab-pane key="notes" tab="我的笔记">
        <div class="list-container">
          <div style="margin-bottom: 12px">
            <a-radio-group v-model:value="notesStatus" size="small" @change="onNotesStatusChange">
              <a-radio-button value="">全部</a-radio-button>
              <a-radio-button value="draft">草稿</a-radio-button>
              <a-radio-button value="scheduled">定时中</a-radio-button>
              <a-radio-button value="published">已发布</a-radio-button>
            </a-radio-group>
          </div>
          <a-spin :spinning="notesLoading">
            <div v-if="notes.length === 0 && !notesLoading" class="empty-state">
              <FileTextOutlined class="empty-icon" />
              <p>还没有发布过笔记</p>
              <a-button type="primary" @click="router.push('/user/publish')">去发布</a-button>
            </div>
            <div v-else class="item-list">
              <div
                v-for="note in notes"
                :key="note.id"
                class="content-item"
                @click="goNote(note.id)"
              >
                <div class="item-main">
                  <span class="item-title">{{ note.title }}</span>
                  <span class="item-meta">
                    <span v-if="note.category" class="item-tag">{{ note.category }}</span>
                    <span>浏览 {{ note.viewCount || 0 }}</span>
                    <span>点赞 {{ note.likeCount || 0 }}</span>
                    <span>评论 {{ note.commentCount || 0 }}</span>
                  </span>
                </div>
                <RightOutlined class="item-arrow" />
              </div>
            </div>
          </a-spin>
          <div class="pagination-wrapper" v-if="notesTotal > pageSize">
            <a-pagination
              v-model:current="notesPage"
              :total="notesTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onNotesPageChange"
            />
          </div>
        </div>
      </a-tab-pane>

      <!-- 我的资源 -->
      <a-tab-pane key="resources" tab="我的资源">
        <div class="list-container">
          <a-spin :spinning="resourcesLoading">
            <div v-if="resources.length === 0 && !resourcesLoading" class="empty-state">
              <FolderOpenOutlined class="empty-icon" />
              <p>还没有上传过资源</p>
              <a-button type="primary" @click="router.push('/user/resourceAdd')">去上传</a-button>
            </div>
            <div v-else class="item-list">
              <div
                v-for="res in resources"
                :key="res.id"
                class="content-item"
                @click="goResource(res.id)"
              >
                <div class="item-main">
                  <span class="item-title">{{ res.title }}</span>
                  <span class="item-meta">
                    <span v-if="res.resourceType" class="item-tag">{{ res.resourceType }}</span>
                    <span v-if="res.category" class="item-tag">{{ res.category }}</span>
                    <span>下载 {{ res.downloadCount || 0 }}</span>
                    <span>{{ res.fileSize || '' }}</span>
                  </span>
                </div>
                <RightOutlined class="item-arrow" />
              </div>
            </div>
          </a-spin>
          <div class="pagination-wrapper" v-if="resourcesTotal > pageSize">
            <a-pagination
              v-model:current="resourcesPage"
              :total="resourcesTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onResourcesPageChange"
            />
          </div>
        </div>
      </a-tab-pane>

      <!-- 我的评论 -->
      <a-tab-pane key="comments" tab="我的评论">
        <div class="list-container">
          <a-spin :spinning="commentsLoading">
            <div v-if="comments.length === 0 && !commentsLoading" class="empty-state">
              <CommentOutlined class="empty-icon" />
              <p>还没有发表过评论</p>
            </div>
            <div v-else class="item-list">
              <div
                v-for="c in comments"
                :key="c.id"
                class="content-item"
                @click="goNote(c.noteId)"
              >
                <div class="item-main">
                  <span class="item-title item-title--comment">{{ c.content }}</span>
                  <span class="item-meta">
                    <span v-if="c.parentId" class="item-tag">回复</span>
                    <span v-if="c.noteTitle" class="item-note">《{{ c.noteTitle }}》</span>
                    <span>点赞 {{ c.likeCount || 0 }}</span>
                    <span v-if="c.createTimeStr">{{ c.createTimeStr }}</span>
                  </span>
                </div>
                <RightOutlined class="item-arrow" />
              </div>
            </div>
          </a-spin>
          <div class="pagination-wrapper" v-if="commentsTotal > pageSize">
            <a-pagination
              v-model:current="commentsPage"
              :total="commentsTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onCommentsPageChange"
            />
          </div>
        </div>
      </a-tab-pane>

      <!-- 我的获赞 -->
      <a-tab-pane key="likes" tab="我的获赞">
        <div class="list-container">
          <a-spin :spinning="likesLoading">
            <div v-if="likes.length === 0 && !likesLoading" class="empty-state">
              <LikeOutlined class="empty-icon" />
              <p>还没有收到点赞</p>
            </div>
            <div v-else class="item-list">
              <div
                v-for="like in likes"
                :key="like.id"
                class="content-item"
                @click="goNote(like.noteId)"
              >
                <UserAvatar :size="36" :src="like.likerAvatar" class="liker-avatar">
                  {{ (like.likerName || '?').charAt(0) }}
                </UserAvatar>
                <div class="item-main">
                  <span class="item-title">
                    <b class="liker-name">{{ like.likerName || '用户' }}</b> 赞了你的笔记
                  </span>
                  <span class="item-meta">
                    <span v-if="like.noteTitle" class="item-note">《{{ like.noteTitle }}》</span>
                    <span v-if="like.createTime">{{ like.createTime }}</span>
                  </span>
                </div>
                <RightOutlined class="item-arrow" />
              </div>
            </div>
          </a-spin>
          <div class="pagination-wrapper" v-if="likesTotal > pageSize">
            <a-pagination
              v-model:current="likesPage"
              :total="likesTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onLikesPageChange"
            />
          </div>
        </div>
      </a-tab-pane>

      <!-- 我的收藏 -->
      <a-tab-pane key="collections" tab="我的收藏">
        <div class="list-container">
          <a-spin :spinning="collectionsLoading">
            <div v-if="collections.length === 0 && !collectionsLoading" class="empty-state">
              <StarOutlined class="empty-icon" />
              <p>还没有收藏过笔记</p>
            </div>
            <div v-else class="item-list">
              <div
                v-for="note in collections"
                :key="note.id"
                class="content-item"
                @click="goNote(note.id)"
              >
                <div class="item-main">
                  <span class="item-title">{{ note.title }}</span>
                  <span class="item-meta">
                    <span v-if="note.author" class="item-note">{{ note.author }}</span>
                    <span v-if="note.category" class="item-tag">{{ note.category }}</span>
                    <span>浏览 {{ note.viewCount || 0 }}</span>
                    <span>点赞 {{ note.likeCount || 0 }}</span>
                    <span>评论 {{ note.commentCount || 0 }}</span>
                  </span>
                </div>
                <RightOutlined class="item-arrow" />
              </div>
            </div>
          </a-spin>
          <div class="pagination-wrapper" v-if="collectionsTotal > pageSize">
            <a-pagination
              v-model:current="collectionsPage"
              :total="collectionsTotal"
              :pageSize="pageSize"
              :showSizeChanger="false"
              @change="onCollectionsPageChange"
            />
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<style scoped>
.my-content-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 0 16px 40px;
}

/* 页面标题 */
.page-header {
  padding: 28px 0 20px;
  text-align: center;
}

.page-title {
  font-family: var(--font-display, sans-serif);
  font-size: 1.625rem;
  font-weight: 700;
  color: var(--color-primary, #1e293b);
  margin: 0 0 6px;
}

.page-subtitle {
  font-size: 0.875rem;
  color: var(--color-text-muted, #94a3b8);
  margin: 0;
}

/* Tabs */
.content-tabs {
  background: #ffffff;
  border-radius: var(--radius-lg, 12px);
  border: 1px solid var(--color-border-light, #f1f5f9);
  padding: 0 24px;
  min-height: 400px;
}

.content-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}

.content-tabs :deep(.ant-tabs-tab) {
  font-size: 0.9375rem;
  font-weight: 500;
  padding: 16px 0;
}

.content-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: var(--color-accent, #6366f1);
  font-weight: 600;
}

.content-tabs :deep(.ant-tabs-ink-bar) {
  background: var(--color-accent, #6366f1);
}

.list-container {
  padding: 16px 0;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px 0;
  color: var(--color-text-muted, #94a3b8);
}

.empty-icon {
  font-size: 3rem;
  opacity: 0.4;
}

.empty-state p {
  font-size: 0.9375rem;
  margin: 0;
}

/* 列表 */
.item-list {
  display: flex;
  flex-direction: column;
}

.content-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--color-border-light, #f1f5f9);
  cursor: pointer;
  transition: background 0.2s ease;
  border-radius: var(--radius-md, 8px);
}

.content-item:last-child {
  border-bottom: none;
}

.content-item:hover {
  background: var(--color-bg-warm, #f8fafc);
}

.content-item:hover .item-arrow {
  color: var(--color-accent, #6366f1);
}

.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.item-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-primary, #1e293b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-title--comment {
  font-weight: 500;
}

.item-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 0.8125rem;
  color: var(--color-text-muted, #94a3b8);
}

.item-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-accent-glow, rgba(99, 102, 241, 0.08));
  color: var(--color-accent, #6366f1);
  font-size: 0.75rem;
  font-weight: 500;
}

.item-note {
  color: var(--color-text-secondary, #64748b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-border, #e2e8f0);
  transition: color 0.2s ease;
}

/* 获赞列表点赞者 */
.liker-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--color-accent, #6366f1), var(--color-accent-light, #818cf8));
  color: #ffffff;
  font-weight: 600;
}

.liker-name {
  font-weight: 600;
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
  .content-tabs {
    padding: 0 16px;
  }

  .content-item {
    padding: 12px 0;
  }
}
</style>
