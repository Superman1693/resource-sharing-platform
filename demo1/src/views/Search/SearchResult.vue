<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  SearchOutlined, FilePdfOutlined, VideoCameraOutlined,
  CodeOutlined, FolderOutlined,
} from '@ant-design/icons-vue'
import { searchNotes, searchResources, searchPublicUsers, getHotKeywords } from '../../utils/api'
import UserAvatar from '../../components/UserAvatar.vue'
import { CATEGORY_TEXT_SHORT } from '../../utils/constant'

const route = useRoute()
const router = useRouter()

const keyword = ref(route.query.q || '')
const activeTab = ref(route.query.type || 'notes')
const loading = ref(false)
const searched = ref(false)

const notes = ref([])
const users = ref([])
const resources = ref([])
const hotKeywords = ref([])

const resourceTypeIcon = { document: FilePdfOutlined, video: VideoCameraOutlined, code: CodeOutlined, other: FolderOutlined }
const resourceTypeLabel = { document: '文档', video: '视频', code: '代码', other: '其他' }

// 并发搜索三类
const doSearch = async (q) => {
  const kw = (q ?? keyword.value).trim()
  if (!kw) { searched.value = false; return }
  keyword.value = kw
  router.replace({ query: { ...route.query, q: kw } })
  searched.value = true
  loading.value = true
  try {
    const [n, u, r] = await Promise.allSettled([
      searchNotes({ keyword: kw, pageSize: 20 }),
      searchPublicUsers({ keyword: kw }),
      searchResources({ keyword: kw, pageSize: 20 }),
    ])
    notes.value = n.status === 'fulfilled' ? (n.value.data?.records || []) : []
    users.value = u.status === 'fulfilled' ? (u.value.data || []) : []
    resources.value = r.status === 'fulfilled' ? (r.value.data?.records || []) : []
  } catch (err) {
    message.error(err?.description || err?.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

const loadHot = async () => {
  try { const res = await getHotKeywords(); hotKeywords.value = res.data || [] } catch (_) {}
}

const searchFromHot = (q) => doSearch(q)

const goNote = (id) => router.push(`/user/noteDetail/${id}`)
const goUser = (id) => router.push(`/user/user/${id}`)
const goResource = (id) => router.push(`/user/resourceDetail/${id}`)

// 保留 <em> 高亮，转义其余标签
const sanitizeHighlight = (text) => {
  if (text == null) return ''
  return String(text).replace(/<(?!\/?em\b)[^>]*>/gi, '')
}

onMounted(() => {
  loadHot()
  if (keyword.value) doSearch(keyword.value)
})

watch(() => route.query.q, (q) => {
  const v = q || ''
  if (v !== keyword.value) { keyword.value = v; if (v) doSearch(v); else searched.value = false }
})
</script>

<template>
  <div class="search-page">
    <!-- 搜索头 -->
    <div class="search-header">
      <h2 class="page-title">搜索</h2>
      <div class="search-box">
        <SearchOutlined class="search-icon" />
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索笔记、用户、资源..."
          @keyup.enter="doSearch()"
        />
        <button class="search-btn" @click="doSearch()">搜索</button>
      </div>
    </div>

    <!-- 未搜索：热搜词云 -->
    <div v-if="!searched" class="hot-section">
      <p class="hot-title">大家都在搜</p>
      <div v-if="hotKeywords.length" class="hot-tags">
        <span v-for="w in hotKeywords" :key="w" class="hot-tag" @click="searchFromHot(w)">{{ w }}</span>
      </div>
      <p v-else class="hot-empty">暂无热搜，输入关键词开始搜索吧</p>
    </div>

    <!-- 搜索结果 -->
    <div v-else>
      <a-tabs v-model:activeKey="activeTab" class="result-tabs">
        <!-- 笔记 -->
        <a-tab-pane key="notes" :tab="`笔记 (${notes.length})`">
          <a-spin :spinning="loading">
            <div v-if="notes.length === 0 && !loading" class="empty">未找到相关笔记</div>
            <div v-else class="note-list">
              <div v-for="n in notes" :key="n.id" class="note-item" @click="goNote(n.id)">
                <span class="cat">{{ CATEGORY_TEXT_SHORT[n.category] || '其他' }}</span>
                <span class="title" v-html="sanitizeHighlight(n.title)"></span>
                <span class="summary" v-if="n.summary">{{ n.summary }}</span>
              </div>
            </div>
          </a-spin>
        </a-tab-pane>

        <!-- 用户 -->
        <a-tab-pane key="users" :tab="`用户 (${users.length})`">
          <a-spin :spinning="loading">
            <div v-if="users.length === 0 && !loading" class="empty">未找到相关用户</div>
            <div v-else class="user-list">
              <div v-for="u in users" :key="u.id" class="user-item" @click="goUser(u.id)">
                <UserAvatar :src="u.avatarUrl" :size="44" />
                <div class="user-info">
                  <span class="user-name">{{ u.username }}</span>
                  <span class="user-bio">{{ u.userProfile || '这个人很懒，什么都没写~' }}</span>
                </div>
              </div>
            </div>
          </a-spin>
        </a-tab-pane>

        <!-- 资源 -->
        <a-tab-pane key="resources" :tab="`资源 (${resources.length})`">
          <a-spin :spinning="loading">
            <div v-if="resources.length === 0 && !loading" class="empty">未找到相关资源</div>
            <div v-else class="res-list">
              <div v-for="r in resources" :key="r.id" class="res-item" @click="goResource(r.id)">
                <component :is="resourceTypeIcon[r.resourceType] || FolderOutlined" class="res-icon" />
                <div class="res-body">
                  <span class="res-title">{{ r.title }}</span>
                  <span class="res-desc" v-if="r.description">{{ r.description }}</span>
                  <span class="res-meta">{{ resourceTypeLabel[r.resourceType] || '其他' }} · 下载 {{ r.downloadCount || 0 }}</span>
                </div>
              </div>
            </div>
          </a-spin>
        </a-tab-pane>
      </a-tabs>
    </div>
  </div>
</template>

<style scoped>
.search-page { max-width: 860px; margin: 0 auto; padding: 8px 16px 40px; }
.search-header { padding: 16px 0 20px; }
.page-title { font-size: 1.5rem; font-weight: 700; color: var(--color-primary, #1e293b); margin: 0 0 16px; }
.search-box {
  display: flex; align-items: center; gap: 8px;
  background: #fff; border: 1px solid var(--color-border, #e2e8f0);
  border-radius: 999px; padding: 4px 4px 4px 16px;
}
.search-icon { color: var(--color-text-muted, #94a3b8); }
.search-input {
  flex: 1; border: none; outline: none; background: transparent;
  font-size: 0.9375rem; height: 36px; color: var(--color-text, #1e293b);
}
.search-btn {
  height: 36px; padding: 0 24px; background: var(--color-accent, #6366f1);
  color: #fff; border: none; border-radius: 999px;
  font-size: 0.875rem; font-weight: 500; cursor: pointer; transition: background 0.2s;
}
.search-btn:hover { background: var(--color-accent-light, #818cf8); }

.hot-section {
  background: #fff; border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 12px; padding: 24px;
}
.hot-title { font-size: 0.9375rem; font-weight: 600; color: var(--color-primary, #1e293b); margin: 0 0 16px; }
.hot-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.hot-tag {
  padding: 6px 14px; background: var(--color-accent-glow, rgba(99,102,241,0.08));
  color: var(--color-accent, #6366f1); border-radius: 999px;
  font-size: 0.8125rem; font-weight: 500; cursor: pointer; transition: all 0.2s;
}
.hot-tag:hover { background: var(--color-accent, #6366f1); color: #fff; }
.hot-empty { font-size: 0.875rem; color: var(--color-text-muted, #94a3b8); margin: 0; }

.result-tabs {
  background: #fff; border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 12px; padding: 0 24px; min-height: 300px;
}
.result-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) { color: var(--color-accent, #6366f1); }
.result-tabs :deep(.ant-tabs-ink-bar) { background: var(--color-accent, #6366f1); }

.empty { padding: 60px 0; text-align: center; color: var(--color-text-muted, #94a3b8); font-size: 0.9375rem; }

.note-list, .user-list, .res-list { display: flex; flex-direction: column; }
.note-item, .user-item, .res-item {
  display: flex; align-items: center; gap: 12px; padding: 14px 0;
  border-bottom: 1px solid var(--color-border-light, #f1f5f9);
  cursor: pointer; transition: background 0.2s; border-radius: 8px;
}
.note-item:hover, .user-item:hover, .res-item:hover { background: var(--color-bg-warm, #f8fafc); }
.note-item .cat {
  padding: 2px 8px; border-radius: 999px; font-size: 0.6875rem; font-weight: 600;
  background: var(--color-accent-glow, rgba(99,102,241,0.08)); color: var(--color-accent, #6366f1); flex-shrink: 0;
}
.note-item .title {
  font-size: 0.9375rem; font-weight: 600; color: var(--color-primary, #1e293b);
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.note-item .summary {
  font-size: 0.8125rem; color: var(--color-text-muted, #94a3b8);
  flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
:deep(.note-item .title em) { color: var(--color-accent, #6366f1); font-style: normal; font-weight: 600; }
.user-info { display: flex; flex-direction: column; gap: 4px; }
.user-name { font-size: 0.9375rem; font-weight: 600; color: var(--color-primary, #1e293b); }
.user-bio { font-size: 0.8125rem; color: var(--color-text-muted, #94a3b8); }
.res-icon { font-size: 1.5rem; color: var(--color-text-muted, #94a3b8); flex-shrink: 0; }
.res-body { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.res-title { font-size: 0.9375rem; font-weight: 600; color: var(--color-primary, #1e293b); }
.res-desc { font-size: 0.8125rem; color: var(--color-text-secondary, #64748b); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.res-meta { font-size: 0.75rem; color: var(--color-text-muted, #94a3b8); }
</style>
