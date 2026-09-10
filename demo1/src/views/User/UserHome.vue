<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  EyeOutlined, LikeOutlined, FileTextOutlined, DownloadOutlined,
  BookOutlined, TrophyOutlined, ArrowRightOutlined,
  FilePdfOutlined, VideoCameraOutlined, CodeOutlined, FolderOutlined,
  TeamOutlined, GiftOutlined
} from '@ant-design/icons-vue'
import TodayKnowledgeCard from '../../components/TodayKnowledgeCard.vue'
import ContributionHeatmap from '../../components/ContributionHeatmap.vue'
import FollowButton from '../../components/FollowButton.vue'
import UserAvatar from '../../components/UserAvatar.vue'
import { useUserStore } from '../../store/userLogin'
import {
  getNoteList, getResourceList, getPersonalStats,
  getContributionData, getUserGrowth, getFollowList, getStarFeed,
  signIn, checkSignedToday, getPointsAccount
} from '../../utils/api'
import { message } from 'ant-design-vue'
import { CATEGORY_TEXT_SHORT } from '../../utils/constant'
import { formatDate } from '../../utils/dateUtils'

const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = computed(() => !!userStore.isLogin)

// ===== 数据状态 =====
const loading = ref(false)
const allNotes = ref([])
const resources = ref([])
const personalStats = ref(null)
const contributionData = ref([])
const growthData = ref(null)
const recommendedUsers = ref([])
const starFeed = ref([])

// ===== 签到积分 =====
const signedToday = ref(false)
const signing = ref(false)
const pointsBalance = ref(0)

// 星球动态点击跳转（feed 里是 noteId 字段）
const viewFeedNote = (item) => router.push(`/user/noteDetail/${item.noteId}`)
const viewStar = (item) => router.push(`/user/starDetail/${item.starId}`)

// 签到
const handleSignIn = async () => {
  if (signedToday.value || signing.value) return
  signing.value = true
  try {
    const res = await signIn()
    if (res.code === 0 && res.data) {
      if (res.data.signed) {
        signedToday.value = true
        if (res.data.balance != null) pointsBalance.value = res.data.balance
        message.success('签到成功，积分已入账')
      } else {
        // 今日已签（兜底）
        signedToday.value = true
        message.info('今日已签到')
      }
    }
  } catch (_) {
    // 错误由 request 拦截器统一处理
  } finally {
    signing.value = false
  }
}

// 格式化 feed 时间
const formatFeedTime = (t) => {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t).slice(0, 10)
  const now = new Date()
  const diff = now - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const bentoNotes = computed(() => allNotes.value.slice(0, 4))
const listNotes = computed(() => allNotes.value.slice(4, 10))
const useBento = computed(() => bentoNotes.value.length === 4)

const categoryText = CATEGORY_TEXT_SHORT

const themes = [
  { wide: true,  bg: 'var(--color-primary)', light: true },
  { wide: false, bg: 'var(--color-bg-warm)', light: false },
  { wide: false, bg: '#e8edf2', light: false },
  { wide: true,  bg: '#6b8aab', light: true },
]

// ===== 资源类型图标映射 =====
const resourceTypeIcon = {
  document: FilePdfOutlined,
  video: VideoCameraOutlined,
  code: CodeOutlined,
  other: FolderOutlined,
}
const resourceTypeLabel = {
  document: '文档',
  video: '视频',
  code: '代码',
  other: '其他',
}
const resourceTypeColor = {
  document: '#2563eb',
  video: '#dc2626',
  code: '#16a34a',
  other: '#94a3b8',
}

const viewNote = (note) => router.push(`/user/noteDetail/${note.id}`)
const viewResource = (res) => router.push(`/user/resourceDetail/${res.id}`)
const goToUserProfile = (userId) => router.push(`/user/user/${userId}`)

// ===== 滚动动画 =====
let io = null
const setupReveal = () => {
  io?.disconnect()
  io = new IntersectionObserver(
    (entries) =>
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add('revealed')
          io.unobserve(e.target)
        }
      }),
    { threshold: 0.08, rootMargin: '-20px 0px' }
  )
  document.querySelectorAll('[data-reveal]').forEach((el) => io.observe(el))
}

// ===== 数据加载 =====
onMounted(async () => {
  loading.value = true
  try {
    const promises = [
      getNoteList({ sortType: 'latest' }),
      getResourceList({ status: 'enabled' }),
    ]

    if (isLoggedIn.value) {
      promises.push(getPersonalStats())
      promises.push(getContributionData())
      promises.push(getUserGrowth())
      promises.push(getFollowList({ page: 1, pageSize: 20 }))
      promises.push(getStarFeed(8).catch(() => null))
      promises.push(checkSignedToday())
      promises.push(getPointsAccount())
    }

    const results = await Promise.allSettled(promises)

    // 笔记
    const noteRes = results[0].status === 'fulfilled' ? results[0].value : null
    const noteData = noteRes?.data || {}
    allNotes.value = Array.isArray(noteData.records) ? noteData.records
      : Array.isArray(noteData) ? noteData : []

    // 资源
    const resRes = results[1].status === 'fulfilled' ? results[1].value : null
    const resData = resRes?.data || []
    resources.value = Array.isArray(resData) ? resData.slice(0, 6) : []

    // 已登录用户专属数据
    if (isLoggedIn.value) {
      const statsRes = results[2]?.status === 'fulfilled' ? results[2].value : null
      personalStats.value = statsRes?.data || null

      const contribRes = results[3]?.status === 'fulfilled' ? results[3].value : null
      contributionData.value = Array.isArray(contribRes?.data) ? contribRes.data : []

      const growthRes = results[4]?.status === 'fulfilled' ? results[4].value : null
      growthData.value = growthRes?.data || null

      const followRes = results[5]?.status === 'fulfilled' ? results[5].value : null
      const followRecords = followRes?.data?.records || []
      recommendedUsers.value = followRecords.slice(0, 5)

      const feedRes = results[6]?.status === 'fulfilled' ? results[6].value : null
      starFeed.value = Array.isArray(feedRes?.data) ? feedRes.data : []

      // 签到状态 + 积分账户
      const signedRes = results[7]?.status === 'fulfilled' ? results[7].value : null
      signedToday.value = !!signedRes?.data?.signed

      const accountRes = results[8]?.status === 'fulfilled' ? results[8].value : null
      pointsBalance.value = accountRes?.data?.balance || 0
    }
  } catch (_) {
    // 静默处理
  } finally {
    loading.value = false
    await nextTick()
    setupReveal()
  }
})

onUnmounted(() => io?.disconnect())
</script>

<template>
  <div class="home">
    <!-- ===== Hero 区域 ===== -->
    <section class="hero">
      <div class="hero-bg-pattern" />
      <div class="container">
        <p class="hero-label">CodeVerse · Your Coding Journey</p>
        <h1 class="hero-title">
          慢慢记录<br /><em class="hero-em">持续精进</em>
        </h1>
        <p class="hero-sub">
          这里整理日常学习笔记和实践心得<br />
          把零散经验写下来 方便回顾与复盘
        </p>
        <div class="hero-cta">
          <button class="btn-primary" @click="router.push('/user/notes')">
            查看笔记<span class="cta-arrow">→</span>
          </button>
          <button class="btn-outline" @click="router.push('/user/resources')">浏览资源</button>
        </div>
        <div class="hero-stats">
          <div class="stat-item">
            <span class="stat-num">{{ allNotes.length || 0 }}</span>
            <span class="stat-label">篇笔记</span>
          </div>
          <div class="stat-sep" />
          <div class="stat-item">
            <span class="stat-num">{{ resources.length || 0 }}</span>
            <span class="stat-label">份资源</span>
          </div>
          <div class="stat-sep" />
          <div class="stat-item">
            <span class="stat-num">∞</span>
            <span class="stat-label">持续学习</span>
          </div>
          <div class="stat-sep" />
          <div class="stat-item">
            <span class="stat-num">{{ new Date().getFullYear() }}</span>
            <span class="stat-label">成长中</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 每日知识 ===== -->
    <div class="knowledge-band">
      <div class="container">
        <TodayKnowledgeCard />
      </div>
    </div>

    <!-- ===== 个人学习看板（已登录用户） ===== -->
    <section v-if="isLoggedIn && personalStats" class="dashboard-section">
      <div class="container">
        <div class="section-head" data-reveal>
          <div>
            <p class="section-tag">My Dashboard</p>
            <h2 class="section-title">学习看板</h2>
          </div>
          <a class="section-more" @click="router.push('/user/dashboard')">详细数据 →</a>
        </div>

        <!-- 每日签到 -->
        <div class="sign-card" data-reveal>
          <div class="sign-left">
            <div class="sign-icon"><GiftOutlined /></div>
            <div class="sign-text">
              <span class="sign-title">每日签到</span>
              <span class="sign-sub">{{ signedToday ? '今日已签到，明天再来吧' : '签到领积分，连续签到奖励更多' }}</span>
            </div>
          </div>
          <div class="sign-right">
            <span class="sign-balance">当前 {{ pointsBalance }} 积分</span>
            <button
              class="sign-btn"
              :class="{ 'is-signed': signedToday }"
              :disabled="signedToday || signing"
              @click="handleSignIn"
            >
              {{ signedToday ? '已签到 ✓' : (signing ? '签到中…' : '立即签到') }}
            </button>
          </div>
        </div>

        <!-- 统计卡片 -->
        <div class="stats-grid" data-reveal>
          <div class="dash-stat-card clickable" style="--accent: #2563eb" title="查看我的笔记" @click="router.push('/user/notes')">
            <div class="dash-stat-icon">
              <BookOutlined />
            </div>
            <div class="dash-stat-info">
              <span class="dash-stat-value">{{ personalStats.noteCount || 0 }}</span>
              <span class="dash-stat-label">发布笔记</span>
            </div>
          </div>
          <div class="dash-stat-card clickable" style="--accent: #f59e0b" title="查看获赞数据" @click="router.push('/user/dashboard')">
            <div class="dash-stat-icon">
              <LikeOutlined />
            </div>
            <div class="dash-stat-info">
              <span class="dash-stat-value">{{ personalStats.likeCount || 0 }}</span>
              <span class="dash-stat-label">获得点赞</span>
            </div>
          </div>
          <div class="dash-stat-card clickable" style="--accent: #10b981" title="查看评论数据" @click="router.push('/user/dashboard')">
            <div class="dash-stat-icon">
              <FileTextOutlined />
            </div>
            <div class="dash-stat-info">
              <span class="dash-stat-value">{{ personalStats.commentCount || 0 }}</span>
              <span class="dash-stat-label">发布评论</span>
            </div>
          </div>
          <div class="dash-stat-card clickable" style="--accent: #8b5cf6" title="查看成长等级" @click="router.push('/user/growthTimeline')">
            <div class="dash-stat-icon">
              <TrophyOutlined />
            </div>
            <div class="dash-stat-info">
              <span class="dash-stat-value">Lv.{{ growthData?.level || 1 }}</span>
              <span class="dash-stat-label">当前等级</span>
            </div>
          </div>
        </div>

        <!-- 贡献热力图 -->
        <div class="heatmap-card clickable" data-reveal title="查看详细学习数据" @click="router.push('/user/dashboard')">
          <div class="heatmap-header">
            <span class="heatmap-title">学习轨迹</span>
            <span class="heatmap-count">今年共 {{ contributionData.reduce((s, d) => s + (d.count || 0), 0) }} 次贡献 · 点击查看详情 →</span>
          </div>
          <ContributionHeatmap :data="contributionData" />
        </div>
      </div>
    </section>

    <!-- ===== 星球动态（已登录且加入了星球） ===== -->
    <section v-if="isLoggedIn && starFeed.length > 0" class="feed-section">
      <div class="container">
        <div class="section-head" data-reveal>
          <div>
            <p class="section-tag">Star Feed</p>
            <h2 class="section-title">星球动态</h2>
          </div>
          <a class="section-more" @click="router.push('/user/starList')">我的星球 →</a>
        </div>

        <div class="feed-list" data-reveal>
          <div
            v-for="item in starFeed"
            :key="item.noteId"
            class="feed-item"
            @click="viewFeedNote(item)"
          >
            <div class="feed-main">
              <h4 class="feed-title">{{ item.title }}</h4>
              <p class="feed-summary">{{ item.summary || '作者没有写摘要' }}</p>
              <div class="feed-meta">
                <a-tag
                  color="blue"
                  class="feed-star-tag"
                  @click.stop="viewStar(item)"
                >
                  <TeamOutlined /> {{ item.starName }}
                </a-tag>
                <span class="feed-author">{{ item.authorName }}</span>
                <span class="feed-time">{{ formatFeedTime(item.publishTime) }}</span>
                <span class="feed-stats"><EyeOutlined /> {{ item.viewCount || 0 }}</span>
                <span class="feed-stats"><LikeOutlined /> {{ item.likeCount || 0 }}</span>
              </div>
            </div>
            <ArrowRightOutlined class="feed-arrow" />
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 精选资源推荐 ===== -->
    <section v-if="resources.length > 0" class="resources-section">
      <div class="container">
        <div class="section-head" data-reveal>
          <div>
            <p class="section-tag">Featured Resources</p>
            <h2 class="section-title">精选资源</h2>
          </div>
          <a class="section-more" @click="router.push('/user/resources')">查看全部 →</a>
        </div>

        <div class="resources-grid" data-reveal>
          <div
            v-for="(res, i) in resources"
            :key="res.id"
            class="resource-card"
            :style="{ '--delay': i * 0.08 + 's' }"
            @click="viewResource(res)"
          >
            <div class="resource-icon-wrap" :style="{ background: resourceTypeColor[res.resourceType] + '12', color: resourceTypeColor[res.resourceType] }">
              <component :is="resourceTypeIcon[res.resourceType] || FolderOutlined" />
            </div>
            <div class="resource-body">
              <h4 class="resource-title">{{ res.title }}</h4>
              <p class="resource-desc">{{ res.description }}</p>
              <div class="resource-meta">
                <a-tag :color="resourceTypeColor[res.resourceType]" size="small" :bordered="false">
                  {{ resourceTypeLabel[res.resourceType] || '其他' }}
                </a-tag>
                <span class="resource-size" v-if="res.fileSize">{{ res.fileSize }}</span>
                <span class="resource-downloads">
                  <DownloadOutlined /> {{ res.downloadCount || 0 }}
                </span>
              </div>
            </div>
            <ArrowRightOutlined class="resource-arrow" />
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 最新笔记 ===== -->
    <section class="section" v-if="!loading">
      <div class="container">
        <div class="section-head" data-reveal>
          <div>
            <p class="section-tag">Recent Notes</p>
            <h2 class="section-title">最新笔记</h2>
          </div>
          <a class="section-more" @click="router.push('/user/notes')">查看全部 →</a>
        </div>

        <!-- Bento 网格 -->
        <div class="bento" v-if="bentoNotes.length > 0">
          <div
            v-for="(note, i) in bentoNotes"
            :key="note.id"
            class="bento-card"
            :class="{
              'bento-wide': useBento && (i === 0 || i === 3),
              'bento-light': themes[i]?.light,
            }"
            :style="{
              backgroundColor: themes[i]?.bg || 'var(--color-bg-warm)',
              transitionDelay: i * 0.09 + 's',
            }"
            data-reveal
            @click="viewNote(note)"
          >
            <div class="bento-thumb">
              <img v-if="note.coverImage" :src="note.coverImage" :alt="note.title" />
              <div v-else class="bento-placeholder" />
            </div>
            <span class="bento-cat">{{ categoryText[note.category] || '其他' }}</span>
            <h3 class="bento-title">{{ note.title }}</h3>
            <p class="bento-desc">{{ note.summary }}</p>
            <div class="bento-stats">
              <span><EyeOutlined /> {{ note.viewCount || 0 }}</span>
              <span><LikeOutlined /> {{ note.likeCount || 0 }}</span>
              <time class="bento-time">{{ formatDate(note.publishTime) }}</time>
            </div>
            <span class="bento-go">↗</span>
          </div>
        </div>

        <!-- 博客列表 -->
        <template v-if="listNotes.length > 0">
          <div class="section-head" style="margin-top: 88px" data-reveal>
            <div>
              <p class="section-tag">Notes &amp; Blog</p>
              <h2 class="section-title">学习记录</h2>
            </div>
          </div>
          <div class="blog-list">
            <a
              v-for="(note, i) in listNotes"
              :key="note.id"
              class="blog-row"
              :style="{ transitionDelay: i * 0.07 + 's' }"
              data-reveal
              @click="viewNote(note)"
            >
              <time class="blog-date">{{ formatDate(note.publishTime) }}</time>
              <div class="blog-body">
                <div class="blog-top">
                  <span class="blog-cat">{{ categoryText[note.category] || '其他' }}</span>
                  <span class="blog-views"><EyeOutlined /> {{ note.viewCount || 0 }}</span>
                </div>
                <h3 class="blog-title">{{ note.title }}</h3>
                <p class="blog-summary">{{ note.summary }}</p>
              </div>
              <span class="blog-go">→</span>
            </a>
          </div>
        </template>

        <!-- 空状态 -->
        <div v-if="allNotes.length === 0 && !loading" class="empty-state">
          <p class="empty-icon">✍️</p>
          <h3 class="empty-title">还没有笔记</h3>
          <p class="empty-desc">发布你的第一篇学习笔记，开始记录成长轨迹</p>
          <button class="btn-primary" @click="router.push('/user/publish')">写第一篇笔记</button>
        </div>
      </div>
    </section>

    <!-- ===== 推荐关注用户（已登录） ===== -->
    <section v-if="isLoggedIn && recommendedUsers.length > 0" class="users-section">
      <div class="container">
        <div class="section-head" data-reveal>
          <div>
            <p class="section-tag">Following</p>
            <h2 class="section-title">我的关注</h2>
          </div>
          <a class="section-more" @click="router.push('/user/followList')">查看全部 →</a>
        </div>

        <div class="users-scroll" data-reveal>
          <div
            v-for="user in recommendedUsers"
            :key="user.id"
            class="user-card"
            @click="goToUserProfile(user.id)"
          >
            <div class="user-avatar-wrap">
              <img
                v-if="user.avatarUrl"
                :src="user.avatarUrl"
                :alt="user.username"
                class="user-avatar"
              />
              <div v-else class="user-avatar-placeholder">
                {{ (user.username || '?').charAt(0).toUpperCase() }}
              </div>
            </div>
            <h4 class="user-name">{{ user.username }}</h4>
            <p class="user-bio">{{ user.userProfile || '这个人很懒，什么都没写~' }}</p>
            <FollowButton
              :userId="user.id"
              :initialFollowing="true"
              @click.stop
            />
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 加载状态 ===== -->
    <div v-if="loading" class="container" style="padding: 80px 28px">
      <a-skeleton active :paragraph="{ rows: 6 }" />
    </div>
  </div>
</template>

<style scoped>
.home {
  background: #fff;
  font-family: var(--font-body);
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 28px;
}

/* ===== Scroll Reveal ===== */
[data-reveal] {
  opacity: 0;
  transform: translateY(22px);
  transition: opacity 0.65s cubic-bezier(0.22, 1, 0.36, 1),
              transform 0.65s cubic-bezier(0.22, 1, 0.36, 1);
}

[data-reveal].revealed {
  opacity: 1;
  transform: translateY(0);
}

/* ===== Hero ===== */
.hero {
  position: relative;
  padding: 120px 0 100px;
  background: var(--color-bg-card);
  overflow: hidden;
}

.hero-bg-pattern {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 60% 50% at 85% 20%, rgba(37, 99, 235, 0.04), transparent),
    radial-gradient(ellipse 40% 40% at 10% 80%, rgba(37, 99, 235, 0.03), transparent);
  pointer-events: none;
}

.hero-label {
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 22px;
  animation: fadeUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.hero-title {
  font-family: var(--font-display);
  font-size: clamp(48px, 7vw, 88px);
  font-weight: 800;
  line-height: 1.06;
  letter-spacing: -0.03em;
  color: var(--color-primary);
  margin-bottom: 24px;
  animation: fadeUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: 0.1s;
}

.hero-em {
  color: var(--color-accent);
  font-style: normal;
}

.hero-sub {
  font-size: 1.0625rem;
  line-height: 1.8;
  color: var(--color-text-secondary);
  max-width: 500px;
  margin-bottom: 40px;
  animation: fadeUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: 0.2s;
}

.hero-cta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 60px;
  animation: fadeUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: 0.3s;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 28px;
  height: 48px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
  font-family: var(--font-body);
}

.btn-primary:hover {
  background: var(--color-accent-light);
  transform: translateY(-1px);
}

.cta-arrow {
  display: inline-block;
  transition: transform 0.2s;
}

.btn-primary:hover .cta-arrow {
  transform: translateX(4px);
}

.btn-outline {
  display: inline-flex;
  align-items: center;
  padding: 0 28px;
  height: 48px;
  background: transparent;
  color: var(--color-text);
  border: 1.5px solid var(--color-border);
  border-radius: 999px;
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast);
  font-family: var(--font-body);
}

.btn-outline:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  transform: translateY(-1px);
}

.hero-stats {
  display: flex;
  align-items: center;
  gap: 32px;
  padding-top: 40px;
  border-top: 1px solid var(--color-border-light);
  animation: fadeUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: 0.4s;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -0.02em;
  line-height: 1;
}

.stat-label {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.stat-sep {
  width: 1px;
  height: 36px;
  background: var(--color-border);
}

/* ===== Knowledge Band ===== */
.knowledge-band {
  background: var(--color-bg);
  border-top: 1px solid var(--color-border-light);
  border-bottom: 1px solid var(--color-border-light);
  padding: 48px 0;
}

/* ===== Dashboard Section ===== */
.dashboard-section {
  padding: 88px 0 64px;
  background: var(--color-bg);
}

/* ===== 每日签到卡 ===== */
.sign-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  background: var(--color-bg-card, #fff);
  border: 1px solid var(--color-border-light, #f1f5f9);
  border-left: 4px solid var(--color-accent, #6366f1);
  border-radius: var(--radius-lg, 12px);
  margin-bottom: 24px;
  flex-wrap: wrap;
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(0, 0, 0, 0.04));
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}

.sign-card:hover {
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.08);
  transform: translateY(-1px);
}

.sign-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.sign-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md, 8px);
  background: var(--color-accent-glow, rgba(99, 102, 241, 0.1));
  color: var(--color-accent, #6366f1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.sign-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sign-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-primary, #1e293b);
}

.sign-sub {
  font-size: 0.8125rem;
  color: var(--color-text-muted, #94a3b8);
}

.sign-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.sign-balance {
  font-size: 0.875rem;
  color: var(--color-text-secondary, #64748b);
  font-weight: 500;
}

.sign-btn {
  padding: 0 24px;
  height: 40px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--color-accent, #6366f1), var(--color-accent-light, #818cf8));
  color: #fff;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--duration-fast, 0.15s);
  font-family: var(--font-body);
}

.sign-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(99, 102, 241, 0.3);
}

.sign-btn.is-signed,
.sign-btn:disabled {
  background: var(--color-border, #e2e8f0);
  color: var(--color-text-muted, #94a3b8);
  cursor: not-allowed;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.dash-stat-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--color-border-light);
  transition: all var(--duration-normal) var(--ease-out);
  position: relative;
  overflow: hidden;
}

.dash-stat-card::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--accent), transparent);
  opacity: 0.6;
}

.dash-stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
}

.dash-stat-card.clickable,
.heatmap-card.clickable {
  cursor: pointer;
}

.heatmap-card.clickable {
  transition: all var(--duration-normal) var(--ease-out);
}

.heatmap-card.clickable:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary);
}

.dash-stat-icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.125rem;
  background: var(--accent);
  color: #fff;
  flex-shrink: 0;
}

.dash-stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dash-stat-value {
  font-family: var(--font-display);
  font-size: 1.625rem;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.dash-stat-label {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.heatmap-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  padding: 24px;
}

.heatmap-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.heatmap-title {
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--color-primary);
}

.heatmap-count {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

/* ===== Resources Section ===== */
.resources-section {
  padding: 88px 0;
  background: var(--color-bg-card);
}

/* ===== 星球动态 ===== */
.feed-section {
  padding: 88px 0;
  background: var(--color-bg);
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feed-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 24px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
}

.feed-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary);
}

.feed-main {
  flex: 1;
  min-width: 0;
}

.feed-title {
  margin: 0 0 4px;
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-summary {
  margin: 0 0 10px;
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.feed-star-tag {
  cursor: pointer;
  margin: 0;
}

.feed-author {
  font-weight: 500;
}

.feed-stats {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.feed-arrow {
  color: var(--color-text-muted);
  flex-shrink: 0;
  transition: transform var(--duration-normal) var(--ease-out);
}

.feed-item:hover .feed-arrow {
  transform: translateX(4px);
  color: var(--color-primary);
}

.resources-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.resource-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  background: var(--color-bg);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
  border: 1px solid transparent;
  animation: fadeUp 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--delay, 0s);
}

.resource-card:hover {
  background: var(--color-bg-card);
  border-color: var(--color-border);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.resource-card:hover .resource-arrow {
  opacity: 1;
  transform: translateX(2px);
}

.resource-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.resource-body {
  flex: 1;
  min-width: 0;
}

.resource-title {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.resource-desc {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8px;
}

.resource-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.resource-size {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.resource-downloads {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  margin-left: auto;
}

.resource-arrow {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  opacity: 0;
  transition: all var(--duration-fast);
  flex-shrink: 0;
}

/* ===== Notes Section ===== */
.section {
  padding: 88px 0 100px;
  background: var(--color-bg-card);
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 36px;
}

.section-tag {
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-accent);
  margin-bottom: 10px;
}

.section-title {
  font-family: var(--font-display);
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -0.02em;
  line-height: 1.15;
  margin: 0;
}

.section-more {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  cursor: pointer;
  padding-bottom: 1px;
  border-bottom: 1px solid transparent;
  transition: all var(--duration-fast);
  white-space: nowrap;
}

.section-more:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

/* ===== Bento Grid ===== */
.bento {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.bento-card {
  position: relative;
  border-radius: var(--radius-xl);
  padding: 28px;
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1),
              box-shadow 0.3s ease;
  min-height: 280px;
  display: flex;
  flex-direction: column;
}

.bento-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.1);
}

.bento-card:active {
  transform: scale(0.97) translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
  transition-duration: 0.1s;
}

.bento-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 30%, rgba(255, 255, 255, 0.18) 50%, transparent 70%);
  transform: translateX(-100%);
  transition: transform 0.55s ease;
}

.bento-card:hover::before {
  transform: translateX(100%);
}

.bento-card:hover .bento-go {
  opacity: 1;
  transform: translate(2px, -2px);
}

.bento-wide {
  grid-column: span 2;
}

.bento-thumb {
  width: 100%;
  height: 150px;
  border-radius: var(--radius-md);
  overflow: hidden;
  margin-bottom: 18px;
  background: rgba(0, 0, 0, 0.06);
  flex-shrink: 0;
}

.bento-light .bento-thumb {
  background: rgba(255, 255, 255, 0.12);
}

.bento-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bento-placeholder {
  width: 100%;
  height: 100%;
}

.bento-cat {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  margin-bottom: 10px;
  background: rgba(0, 0, 0, 0.07);
  color: var(--color-text);
}

.bento-light .bento-cat {
  background: rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

.bento-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: -0.01em;
  line-height: 1.35;
  margin-bottom: 8px;
}

.bento-light .bento-title {
  color: #fff;
}

.bento-desc {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  flex: 1;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 14px;
}

.bento-light .bento-desc {
  color: rgba(255, 255, 255, 0.65);
}

.bento-stats {
  display: flex;
  gap: 12px;
  font-size: 0.75rem;
  color: var(--color-text-muted);
  align-items: center;
}

.bento-light .bento-stats {
  color: rgba(255, 255, 255, 0.5);
}

.bento-time {
  margin-left: auto;
  font-size: 0.6875rem;
  font-variant-numeric: tabular-nums;
  opacity: 0.8;
}

.bento-go {
  position: absolute;
  top: 22px;
  right: 22px;
  font-size: 1.125rem;
  opacity: 0;
  transition: all var(--duration-fast);
  color: var(--color-text);
}

.bento-light .bento-go {
  color: rgba(255, 255, 255, 0.8);
}

/* ===== Blog List ===== */
.blog-list {
  display: flex;
  flex-direction: column;
}

.blog-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  padding: 26px 0;
  border-bottom: 1px solid var(--color-border-light);
  cursor: pointer;
  text-decoration: none;
  transition: transform var(--duration-fast);
}

.blog-row:first-child {
  border-top: 1px solid var(--color-border-light);
}

.blog-row:hover {
  transform: translateX(6px);
}

.blog-row:hover .blog-title {
  color: var(--color-accent);
}

.blog-row:hover .blog-go {
  color: var(--color-accent);
  transform: translateX(4px);
}

.blog-date {
  width: 68px;
  flex-shrink: 0;
  font-size: 0.8125rem;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
  padding-top: 3px;
}

.blog-body {
  flex: 1;
  min-width: 0;
}

.blog-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.blog-cat {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 600;
  background: var(--color-accent-glow);
  color: var(--color-accent);
}

.blog-views {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.blog-title {
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: -0.01em;
  margin-bottom: 6px;
  line-height: 1.4;
  transition: color var(--duration-fast);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-summary {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
  line-height: 1.6;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.blog-go {
  flex-shrink: 0;
  font-size: 1rem;
  color: var(--color-border);
  padding-top: 2px;
  transition: all var(--duration-fast);
}

/* ===== Recommended Users Section ===== */
.users-section {
  padding: 88px 0 100px;
  background: var(--color-bg);
}

.users-scroll {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}

.user-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: 28px 20px;
  text-align: center;
  cursor: pointer;
  transition: all var(--duration-normal) var(--ease-out);
  border: 1px solid var(--color-border-light);
}

.user-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
  border-color: var(--color-border);
}

.user-avatar-wrap {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 50%;
  overflow: hidden;
  border: 2px solid var(--color-border-light);
}

.user-avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-glow);
  color: var(--color-accent);
  font-family: var(--font-display);
  font-size: 1.375rem;
  font-weight: 700;
}

.user-name {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-bio {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 16px;
}

/* ===== Empty State ===== */
.empty-state {
  text-align: center;
  padding: 100px 24px;
}

.empty-icon {
  font-size: 3.25rem;
  margin-bottom: 20px;
}

.empty-title {
  font-family: var(--font-display);
  font-size: 1.375rem;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: 10px;
}

.empty-desc {
  font-size: 0.9375rem;
  color: var(--color-text-secondary);
  margin-bottom: 28px;
}

/* ===== Keyframes ===== */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(28px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .resources-grid {
    grid-template-columns: 1fr;
  }
  .users-scroll {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 768px) {
  .hero {
    padding: 80px 0 60px;
  }

  .hero-title {
    font-size: 2.75rem;
  }

  .hero-sub {
    font-size: 0.9375rem;
  }

  .hero-stats {
    gap: 20px;
    flex-wrap: wrap;
  }

  .bento {
    grid-template-columns: 1fr;
  }

  .bento-wide {
    grid-column: span 1;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .blog-date {
    display: none;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .users-scroll {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .resource-card {
    padding: 16px;
  }
}
</style>
