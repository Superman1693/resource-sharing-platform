<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { FireOutlined, EyeOutlined, LikeOutlined, MessageOutlined } from '@ant-design/icons-vue'
import { getHotRank } from '../utils/api'
import { CATEGORY_TEXT_SHORT } from '../utils/constant'
import { formatDate } from '../utils/dateUtils'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const period = ref('day')
const hotList = ref([])

// 在组件初始化时确定路由前缀，避免跳转后 location.pathname 变化
const routePrefix = route.path.startsWith('/main') ? '/main' : '/user'

const categoryText = CATEGORY_TEXT_SHORT

const fetchHotRank = async () => {
  loading.value = true
  try {
    const res = await getHotRank(period.value)
    hotList.value = Array.isArray(res.data) ? res.data : []
  } catch (err) {
    antMessage.error(err?.description || '加载热榜失败')
  } finally {
    loading.value = false
  }
}

const handlePeriodChange = (val) => {
  period.value = val
  fetchHotRank()
}

const viewDetail = (note) => {
  router.push(`${routePrefix}/noteDetail/${note.id}`)
}

const hotScore = (note) =>
  (note.viewCount || 0) + (note.likeCount || 0) * 3 + (note.commentCount || 0) * 2

const rankColors = ['#ff4d4f', '#ff7a45', '#ffa940']

const formatTime = formatDate

onMounted(fetchHotRank)
</script>

<template>
  <div class="hotrank-page">
    <!-- Header -->
    <div class="hotrank-header">
      <div class="header-left">
        <p class="header-label">Hot · Trending · Now</p>
        <h1 class="header-title">
          <FireOutlined class="fire-icon" /> 热榜
        </h1>
        <p class="header-sub">发现最受欢迎的学习内容，跟上知识潮流</p>
      </div>
      <div class="period-switch">
        <button
          class="period-btn"
          :class="{ active: period === 'day' }"
          @click="handlePeriodChange('day')"
        >今日</button>
        <button
          class="period-btn"
          :class="{ active: period === 'week' }"
          @click="handlePeriodChange('week')"
        >本周</button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <!-- 空状态 -->
      <div v-if="!loading && hotList.length === 0" class="empty-state">
        <p class="empty-icon">🔥</p>
        <h3>暂无热榜数据</h3>
        <p>快去发布内容，成为热榜第一吧</p>
      </div>

      <!-- Top 3 大卡片 -->
      <div v-if="hotList.length > 0" class="top3-grid">
        <div
          v-for="(note, i) in hotList.slice(0, 3)"
          :key="note.id"
          class="top-card"
          :class="`rank-${i + 1}`"
          @click="viewDetail(note)"
        >
          <div class="rank-badge">{{ i + 1 }}</div>
          <div class="top-card-cover">
            <img v-if="note.coverImage" :src="note.coverImage" :alt="note.title" />
            <div v-else class="cover-placeholder">
              <FireOutlined :style="{ fontSize: '32px', color: rankColors[i] }" />
            </div>
          </div>
          <div class="top-card-body">
            <div class="top-card-tags">
              <span class="cat-tag">{{ categoryText[note.category] || note.category }}</span>
              <span v-if="note.isTop === 1" class="pin-tag">置顶</span>
            </div>
            <h3 class="top-card-title">{{ note.title }}</h3>
            <p class="top-card-summary">{{ note.summary }}</p>
            <div class="top-card-meta">
              <span>{{ note.author }}</span>
              <span>{{ formatTime(note.publishTime) }}</span>
            </div>
            <div class="top-card-stats">
              <span class="hot-score">
                <FireOutlined /> {{ hotScore(note) }}
              </span>
              <span><EyeOutlined /> {{ note.viewCount || 0 }}</span>
              <span><LikeOutlined /> {{ note.likeCount || 0 }}</span>
              <span><MessageOutlined /> {{ note.commentCount || 0 }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 4名以后列表 -->
      <div v-if="hotList.length > 3" class="rank-list">
        <div
          v-for="(note, i) in hotList.slice(3)"
          :key="note.id"
          class="rank-row"
          @click="viewDetail(note)"
        >
          <div class="rank-num">{{ i + 4 }}</div>
          <div class="rank-body">
            <div class="rank-top">
              <span class="cat-tag-sm">{{ categoryText[note.category] || note.category }}</span>
              <span v-if="note.isTop === 1" class="pin-tag-sm">置顶</span>
              <span class="rank-views"><EyeOutlined /> {{ note.viewCount || 0 }}</span>
            </div>
            <h4 class="rank-title">{{ note.title }}</h4>
            <p class="rank-summary">{{ note.summary }}</p>
            <div class="rank-meta">
              <span>{{ note.author }}</span>
              <span>{{ formatTime(note.publishTime) }}</span>
              <span><LikeOutlined /> {{ note.likeCount || 0 }}</span>
              <span><MessageOutlined /> {{ note.commentCount || 0 }}</span>
            </div>
          </div>
          <div class="rank-score">
            <FireOutlined style="color: #ff4d4f" />
            <span>{{ hotScore(note) }}</span>
          </div>
        </div>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.hotrank-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 40px 24px 80px;
  font-family: var(--font-body);
}

/* ── Header ── */
.hotrank-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 40px;
  flex-wrap: wrap;
  gap: 20px;
}
.header-label {
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  margin-bottom: 10px;
}
.header-title {
  font-size: 2.5rem;
  font-weight: 800;
  font-family: var(--font-display);
  color: var(--color-primary);
  letter-spacing: -0.02em;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.fire-icon {
  color: #ff4d4f;
  animation: flicker 1.5s ease-in-out infinite alternate;
}
@keyframes flicker {
  from { opacity: 0.8; transform: scale(1); }
  to   { opacity: 1;   transform: scale(1.08); }
}
.header-sub {
  font-size: 0.9375rem;
  color: var(--color-text-secondary);
}
.period-switch {
  display: flex;
  gap: 8px;
  background: var(--color-bg-warm);
  padding: 4px;
  border-radius: 999px;
}
.period-btn {
  padding: 6px 20px;
  border: none;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  background: transparent;
  color: var(--color-text-secondary);
  transition: all var(--duration-fast) var(--ease-in-out);
  font-family: var(--font-body);
}
.period-btn.active {
  background: var(--color-primary);
  color: #fff;
}

/* ── Top 3 ── */
.top3-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}
.top-card {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  transition: transform var(--duration-normal) var(--ease-out), box-shadow var(--duration-normal) var(--ease-out);
}
.top-card:hover {
  transform: translateY(-6px);
  box-shadow: var(--shadow-lg);
}
.rank-1 { border-top: 3px solid #ff4d4f; }
.rank-2 { border-top: 3px solid #ff7a45; }
.rank-3 { border-top: 3px solid #ffa940; }
.rank-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.8125rem;
  color: #fff;
  z-index: 1;
}
.rank-1 .rank-badge { background: #ff4d4f; }
.rank-2 .rank-badge { background: #ff7a45; }
.rank-3 .rank-badge { background: #ffa940; }
.top-card-cover {
  height: 160px;
  overflow: hidden;
  background: var(--color-bg-warm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.top-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
.top-card-body {
  padding: 16px;
}
.top-card-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.cat-tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 600;
  background: var(--color-accent-glow);
  color: var(--color-accent);
}
.pin-tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 600;
  background: #fff7e6;
  color: #d48806;
}
.top-card-title {
  font-size: 0.9375rem;
  font-weight: 700;
  font-family: var(--font-body);
  color: var(--color-text);
  margin-bottom: 6px;
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.top-card-summary {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-bottom: 10px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.top-card-meta {
  display: flex;
  gap: 10px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-bottom: 10px;
}
.top-card-stats {
  display: flex;
  gap: 10px;
  font-size: 0.8125rem;
  color: var(--color-text-muted);
  align-items: center;
}
.hot-score {
  color: #ff4d4f;
  font-weight: 600;
  font-size: 0.8125rem;
}

/* ── Rank List ── */
.rank-list {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--color-border-light);
}
.rank-row {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 20px 0;
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: transform var(--duration-fast) var(--ease-out);
}
.rank-row:hover {
  transform: translateX(6px);
}
.rank-row:hover .rank-title {
  color: var(--color-accent);
}
.rank-num {
  width: 32px;
  flex-shrink: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-border);
  padding-top: 2px;
  text-align: center;
}
.rank-body {
  flex: 1;
  min-width: 0;
}
.rank-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.cat-tag-sm {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 600;
  background: var(--color-accent-glow);
  color: var(--color-accent);
}
.pin-tag-sm {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 0.6875rem;
  background: #fff7e6;
  color: #d48806;
}
.rank-views {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-left: auto;
}
.rank-title {
  font-size: 1rem;
  font-weight: 600;
  font-family: var(--font-body);
  color: var(--color-text);
  margin-bottom: 4px;
  line-height: 1.4;
  transition: color var(--duration-fast) var(--ease-in-out);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-summary {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-bottom: 8px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}
.rank-meta {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}
.rank-score {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: #ff4d4f;
  padding-top: 4px;
  min-width: 48px;
}

/* ── Empty ── */
.empty-state {
  text-align: center;
  padding: 100px 24px;
}
.empty-icon { font-size: 52px; margin-bottom: 16px; }
.empty-state h3 { font-size: 1.25rem; font-weight: 700; font-family: var(--font-display); color: var(--color-primary); margin-bottom: 8px; }
.empty-state p { color: var(--color-text-secondary); }

/* ── Mobile ── */
@media (max-width: 768px) {
  .hotrank-page { padding: 24px 16px 60px; }
  .header-title { font-size: 2rem; }
  .top3-grid { grid-template-columns: 1fr; }
  .rank-row { gap: 12px; }
}
</style>
