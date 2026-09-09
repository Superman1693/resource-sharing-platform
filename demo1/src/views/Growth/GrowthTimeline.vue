<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/userLogin'
import { getUserGrowth } from '../../utils/api'
import { formatDateTime } from '../../utils/dateUtils'
import { TrophyOutlined, BookOutlined, StarOutlined, LikeOutlined, FireOutlined, UserOutlined, EyeOutlined } from '@ant-design/icons-vue'
import UserAvatar from '../../components/UserAvatar.vue'

const userStore = useUserStore()
const router = useRouter()
const loading = ref(false)

const growthData = ref({
  publishCount: 0,
  totalLikes: 0,
  totalViews: 0,
  totalComments: 0,
  joinedStars: 0,
  contribution: 0,
  level: 1,
  nextLevelContribution: 100,
  recentNotes: [],
})

const levelProgress = computed(() => {
  const d = growthData.value
  const prevLevel = (d.level - 1) * 100
  const range = d.nextLevelContribution - prevLevel
  const current = d.contribution - prevLevel
  return Math.min(100, Math.round((current / range) * 100))
})

const badges = computed(() => {
  const list = []
  const d = growthData.value
  if (d.publishCount >= 1) list.push('新手上路')
  if (d.publishCount >= 5) list.push('内容创作者')
  if (d.publishCount >= 20) list.push('知识达人')
  if (d.totalLikes >= 50) list.push('受欢迎作者')
  if (d.totalLikes >= 200) list.push('明星创作者')
  if (d.joinedStars >= 1) list.push('星球探索者')
  if (d.level >= 5) list.push('活跃用户')
  if (d.level >= 10) list.push('资深用户')
  return list.length > 0 ? list : ['新手上路']
})

const formatTime = formatDateTime

const fetchGrowth = async () => {
  loading.value = true
  try {
    const res = await getUserGrowth()
    if (res.code === 0 && res.data) {
      growthData.value = res.data
    }
  } catch (err) {
    // 未登录时静默失败
    console.error('加载成长数据失败', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchGrowth()
})
</script>

<template>
  <div class="growth-timeline-page">
    <a-spin :spinning="loading">
      <a-card title="我的成长轨迹" bordered>
        <div class="user-header">
          <UserAvatar :src="userStore.avatarUrl" :size="80" />
          <div class="user-info">
            <h2>{{ userStore.username || '用户' }}</h2>
            <div class="level-info">
              <span class="level-badge">Lv.{{ growthData.level }}</span>
              <a-progress
                :percent="levelProgress"
                :show-info="false"
                style="width: 200px; margin: 0 12px"
              />
              <span class="exp-text">
                {{ growthData.contribution }} / {{ growthData.nextLevelContribution }} 贡献值
              </span>
            </div>
            <div class="contribution-value">
              <FireOutlined /> 知识贡献值：{{ growthData.contribution }}
            </div>
          </div>
        </div>

        <a-divider />

        <div class="stats-grid">
          <a-card class="stat-card">
            <div class="stat-value">{{ growthData.publishCount }}</div>
            <div class="stat-label">发布内容</div>
          </a-card>
          <a-card class="stat-card">
            <div class="stat-value">{{ growthData.totalLikes }}</div>
            <div class="stat-label">获得点赞</div>
          </a-card>
          <a-card class="stat-card">
            <div class="stat-value">{{ growthData.totalViews }}</div>
            <div class="stat-label">内容浏览量</div>
          </a-card>
          <a-card class="stat-card">
            <div class="stat-value">{{ growthData.totalComments }}</div>
            <div class="stat-label">收到评论</div>
          </a-card>
          <a-card class="stat-card">
            <div class="stat-value">{{ growthData.joinedStars }}</div>
            <div class="stat-label">加入星球</div>
          </a-card>
        </div>

        <a-divider />

        <div class="badges-section">
          <h3>我的徽章</h3>
          <a-space wrap>
            <a-tag
              v-for="badge in badges"
              :key="badge"
              color="gold"
              style="font-size: 14px; padding: 4px 12px"
            >
              <TrophyOutlined style="margin-right: 4px" />
              {{ badge }}
            </a-tag>
          </a-space>
        </div>
      </a-card>

      <a-card title="最近发布" bordered style="margin-top: 16px">
        <a-empty
          v-if="!loading && growthData.recentNotes.length === 0"
          description="还没有发布过内容，快去写第一篇笔记吧"
          style="padding: 40px 0"
        >
          <a-button type="primary" @click="router.push('/user/publish')">写笔记</a-button>
        </a-empty>
        <a-timeline v-else>
          <a-timeline-item
            v-for="note in growthData.recentNotes"
            :key="note.id"
          >
            <template #dot>
              <BookOutlined style="color: var(--color-accent); font-size: 16px" />
            </template>
            <div class="timeline-item" @click="router.push(`/user/noteDetail/${note.id}`)">
              <div class="timeline-header">
                <h4>{{ note.title }}</h4>
                <span class="timeline-time">{{ formatTime(note.publishTime) }}</span>
              </div>
              <div class="timeline-description">{{ note.summary }}</div>
              <div class="timeline-stats">
                <span><EyeOutlined /> {{ note.viewCount || 0 }}</span>
                <span><LikeOutlined /> {{ note.likeCount || 0 }}</span>
              </div>
            </div>
          </a-timeline-item>
        </a-timeline>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.growth-timeline-page {
  padding: 16px;
}
.user-header {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 16px 0;
  flex-wrap: wrap;
}
.user-info {
  flex: 1;
}
.user-info h2 {
  margin: 0 0 16px 0;
  font-size: 24px;
}
.level-info {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}
.level-badge {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 14px;
}
.exp-text {
  color: #666;
  font-size: 12px;
}
.contribution-value {
  color: #faad14;
  font-size: 14px;
  font-weight: 500;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
  margin: 16px 0;
}
.stat-card {
  text-align: center;
  border: 1px solid #f0f0f0;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-accent);
  margin-bottom: 8px;
}
.stat-label {
  color: #666;
  font-size: 14px;
}
.badges-section {
  margin: 16px 0;
}
.badges-section h3 {
  margin-bottom: 12px;
}
.timeline-item {
  padding-left: 8px;
  cursor: pointer;
}
.timeline-item:hover h4 {
  color: var(--color-accent);
}
.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  flex-wrap: wrap;
  gap: 8px;
}
.timeline-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  transition: color 0.2s;
}
.timeline-time {
  color: #999;
  font-size: 12px;
  white-space: nowrap;
}
.timeline-description {
  color: #666;
  font-size: 13px;
  margin-bottom: 6px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}
.timeline-stats {
  display: flex;
  gap: 12px;
  color: #999;
  font-size: 12px;
}
</style>
