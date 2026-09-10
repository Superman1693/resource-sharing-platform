<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../../store/userLogin'
import { getUserGrowth, getPointsAccount, getPointsLog, getSignCalendar } from '../../utils/api'
import { formatDateTime } from '../../utils/dateUtils'
import { TrophyOutlined, BookOutlined, StarOutlined, LikeOutlined, FireOutlined, UserOutlined, EyeOutlined, GiftOutlined } from '@ant-design/icons-vue'
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

// ===== 积分明细 =====
const pointsAccount = ref({ balance: 0, totalEarned: 0 })
const pointsLog = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logPageSize = 10
const signCalendar = ref([])
const calendarMonth = ref('') // 格式 yyyy-MM

// 积分类型中文映射
const typeText = { sign: '每日签到', publish: '发布笔记', like: '获得点赞', comment: '发表评论' }

// 等级进度：基于积分余额（level = balance/100 + 1）
const levelProgress = computed(() => {
  const d = growthData.value
  const balance = pointsAccount.value.balance || 0
  const prevLevel = (d.level - 1) * 100
  const current = balance - prevLevel
  return Math.min(100, Math.max(0, Math.round((current / 100) * 100)))
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

// 把任意日期值转成 yyyy-MM-dd（本地时区，避免 UTC 偏移导致错位一天）
const toDateStr = (d) => {
  if (!d) return ''
  const date = typeof d === 'string' ? new Date(d) : d
  if (isNaN(date.getTime())) return String(d).slice(0, 10)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const todayStr = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 本月已签到天数
const signedCountThisMonth = computed(() => signCalendar.value.length)

// 生成当月日历网格（周日开头）
const calendarDays = computed(() => {
  if (!calendarMonth.value) return []
  const [y, m] = calendarMonth.value.split('-').map(Number)
  const firstWeekday = new Date(y, m - 1, 1).getDay()
  const lastDate = new Date(y, m, 0).getDate()
  const days = []
  for (let i = 0; i < firstWeekday; i++) days.push(null)
  for (let d = 1; d <= lastDate; d++) {
    const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const record = signCalendar.value.find(r => toDateStr(r.signDate) === dateStr)
    days.push({ day: d, dateStr, signed: !!record, continuous: record?.continuousDays })
  }
  return days
})

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

// 积分账户（balance / totalEarned）
const fetchPointsAccount = async () => {
  try {
    const res = await getPointsAccount()
    if (res.code === 0 && res.data) {
      pointsAccount.value = res.data
    }
  } catch (_) {
    // 未登录静默
  }
}

// 积分流水（分页）
const fetchPointsLog = async () => {
  try {
    const res = await getPointsLog({ page: logPage.value, pageSize: logPageSize })
    if (res.code === 0 && res.data) {
      pointsLog.value = res.data.records || []
      logTotal.value = res.data.total || 0
    }
  } catch (_) {
    // 未登录静默
  }
}

const onLogPageChange = (p) => {
  logPage.value = p
  fetchPointsLog()
}

// 签到日历（按月）
const fetchSignCalendar = async (month) => {
  try {
    const res = await getSignCalendar({ month })
    if (res.code === 0 && res.data) {
      signCalendar.value = res.data || []
    }
  } catch (_) {
    // 未登录静默
  }
}

// 切换月份
const switchMonth = (delta) => {
  const [y, m] = calendarMonth.value.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  calendarMonth.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  fetchSignCalendar(calendarMonth.value)
}

onMounted(() => {
  fetchGrowth()
  fetchPointsAccount()
  fetchPointsLog()
  // 当前月份，查签到日历
  const now = new Date()
  calendarMonth.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  fetchSignCalendar(calendarMonth.value)
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
                {{ pointsAccount.balance || 0 }} / {{ growthData.nextLevelContribution }} 积分
              </span>
            </div>
            <div class="contribution-value">
              <GiftOutlined /> 当前积分：{{ pointsAccount.balance || 0 }}　·　累计获得：{{ pointsAccount.totalEarned || 0 }}
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
              color="purple"
              style="font-size: 14px; padding: 4px 12px"
            >
              <TrophyOutlined style="margin-right: 4px" />
              {{ badge }}
            </a-tag>
          </a-space>
        </div>
      </a-card>

      <a-card title="积分明细" bordered style="margin-top: 16px">
        <a-row :gutter="24">
          <a-col :xs="24" :md="10">
            <div class="points-block">
              <div class="points-balance">
                <span class="pb-num">{{ pointsAccount.balance || 0 }}</span>
                <span class="pb-unit">积分</span>
              </div>
              <div class="points-meta">累计获得 {{ pointsAccount.totalEarned || 0 }} · 等级 Lv.{{ growthData.level }}</div>
            </div>

            <div class="calendar-head">
              <a-button size="small" @click="switchMonth(-1)">‹</a-button>
              <span class="calendar-month">{{ calendarMonth }}</span>
              <a-button size="small" @click="switchMonth(1)">›</a-button>
              <span class="calendar-count">本月已签 {{ signedCountThisMonth }} 天</span>
            </div>

            <div class="calendar-grid">
              <div class="cw">日</div>
              <div class="cw">一</div>
              <div class="cw">二</div>
              <div class="cw">三</div>
              <div class="cw">四</div>
              <div class="cw">五</div>
              <div class="cw">六</div>
              <template v-for="(d, idx) in calendarDays" :key="idx">
                <div v-if="d === null" class="cd empty"></div>
                <div
                  v-else
                  class="cd"
                  :class="{ signed: d.signed, today: d.dateStr === todayStr() }"
                >
                  <span>{{ d.day }}</span>
                  <i v-if="d.signed" class="cd-dot"></i>
                </div>
              </template>
            </div>
          </a-col>

          <a-col :xs="24" :md="14">
            <div class="log-head">积分流水</div>
            <a-list :data-source="pointsLog" size="small" :locale="{ emptyText: '暂无积分记录' }">
              <template #renderItem="{ item }">
                <a-list-item>
                  <div class="log-row">
                    <div class="log-left">
                      <span class="log-type">{{ typeText[item.type] || item.type }}</span>
                      <span class="log-remark">{{ item.remark }}</span>
                    </div>
                    <div class="log-right">
                      <span class="log-change" :class="{ plus: (item.change || 0) > 0 }">
                        {{ (item.change || 0) > 0 ? '+' : '' }}{{ item.change }}
                      </span>
                      <span class="log-time">{{ formatTime(item.createTime) }}</span>
                    </div>
                  </div>
                </a-list-item>
              </template>
            </a-list>
            <div class="log-pager" v-if="logTotal > logPageSize">
              <a-pagination
                size="small"
                :current="logPage"
                :page-size="logPageSize"
                :total="logTotal"
                :show-size-changer="false"
                @change="onLogPageChange"
              />
            </div>
          </a-col>
        </a-row>
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
  background: linear-gradient(135deg, var(--color-accent, #6366f1), var(--color-accent-light, #818cf8));
  color: #fff;
  padding: 4px 14px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
}
.exp-text {
  color: #666;
  font-size: 12px;
}
.contribution-value {
  color: var(--color-accent, #6366f1);
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

/* ===== 积分明细 ===== */
.points-block {
  text-align: center;
  padding: 8px 0 20px;
}
.points-balance {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 6px;
}
.pb-num {
  font-size: 38px;
  font-weight: 700;
  color: var(--color-accent);
  line-height: 1;
}
.pb-unit {
  font-size: 14px;
  color: #999;
}
.points-meta {
  color: #888;
  font-size: 13px;
  margin-top: 6px;
}
.calendar-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.calendar-month {
  font-weight: 600;
  flex: 1;
  text-align: center;
}
.calendar-count {
  color: #888;
  font-size: 12px;
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 5px;
}
.cw {
  text-align: center;
  color: #aaa;
  font-size: 12px;
  padding: 4px 0;
}
.cd {
  aspect-ratio: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: var(--color-bg, #f8fafc);
  color: var(--color-text-muted, #94a3b8);
  font-size: 13px;
  transition: all 0.2s ease;
}
.cd.empty {
  background: transparent;
}
.cd.signed {
  background: linear-gradient(135deg, var(--color-accent, #6366f1), var(--color-accent-light, #818cf8));
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}
.cd.today {
  box-shadow: 0 0 0 2px var(--color-accent) inset;
}
.cd-dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fff;
  margin-top: 2px;
}
.log-head {
  font-weight: 600;
  margin-bottom: 8px;
}
.log-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 8px;
}
.log-left {
  display: flex;
  flex-direction: column;
}
.log-type {
  font-size: 13px;
  font-weight: 500;
}
.log-remark {
  font-size: 12px;
  color: #999;
}
.log-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}
.log-change {
  font-weight: 600;
  color: #52c41a;
}
.log-time {
  font-size: 11px;
  color: #bbb;
}
.log-pager {
  margin-top: 12px;
  text-align: right;
}
</style>
