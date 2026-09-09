<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import StatsCard from '../../components/StatsCard.vue'
import ContributionHeatmap from '../../components/ContributionHeatmap.vue'
import { getPersonalStats, getContributionData, getLearningTrend } from '../../utils/api'

const router = useRouter()

// 跳转到"我的内容"对应 tab
const goTab = (tab) => router.push('/user/myContent?tab=' + tab)

const stats = ref({
  notes: 0,
  resources: 0,
  comments: 0,
  likes: 0,
})

const contributionData = ref([])
const trendData = ref([])
const categoryData = ref([])

// 加载个人统计数据
const loadStats = async () => {
  try {
    const res = await getPersonalStats()
    if (res.code === 0 && res.data) {
      stats.value = {
        notes: res.data.noteCount || 0,
        resources: res.data.resourceCount || 0,
        comments: res.data.commentCount || 0,
        likes: res.data.likeCount || 0,
      }
    }
  } catch {
    // API 未就绪时使用默认值
  }
}

// 加载热力图数据
const loadContribution = async () => {
  try {
    const res = await getContributionData()
    if (res.code === 0 && Array.isArray(res.data) && res.data.length > 0) {
      contributionData.value = res.data
      return
    }
  } catch { /* fallback */ }
  // 降级：生成模拟数据
  const today = new Date()
  const data = []
  for (let i = 364; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const dateStr = formatDate(d)
    const count = Math.random() > 0.4 ? Math.floor(Math.random() * 12) : 0
    data.push({ date: dateStr, count })
  }
  contributionData.value = data
}

// 加载学习趋势
const loadTrend = async () => {
  try {
    const res = await getLearningTrend()
    if (res.code === 0 && Array.isArray(res.data) && res.data.length > 0) {
      trendData.value = res.data.map(item => ({
        month: item.month,
        value: (item.notes || 0) + (item.comments || 0),
      }))
      return
    }
  } catch { /* fallback */ }
  trendData.value = [
    { month: '12月', value: 45 },
    { month: '1月', value: 62 },
    { month: '2月', value: 38 },
    { month: '3月', value: 85 },
    { month: '4月', value: 73 },
    { month: '5月', value: 96 },
  ]
}

onMounted(() => {
  loadStats()
  loadContribution()
  loadTrend()

  // 模拟分类分布
  categoryData.value = [
    { name: '前端开发', value: 35, color: '#2563eb' },
    { name: '后端开发', value: 25, color: '#7c3aed' },
    { name: '数据库', value: 15, color: '#0891b2' },
    { name: 'DevOps', value: 10, color: '#16a34a' },
    { name: '算法', value: 8, color: '#ea580c' },
    { name: '其他', value: 7, color: '#94a3b8' },
  ]
})

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// 趋势图最大值
const trendMax = ref(100)

// 饼图 conic-gradient
const pieGradient = ref('')
function buildPieGradient() {
  let acc = 0
  const stops = []
  categoryData.value.forEach((cat) => {
    const start = acc
    acc += cat.value
    stops.push(`${cat.color} ${start}% ${acc}%`)
  })
  pieGradient.value = `conic-gradient(${stops.join(', ')})`
}

onMounted(() => {
  buildPieGradient()
})
</script>

<template>
  <div class="dashboard">
    <!-- 页面标题 -->
    <div class="dashboard__header">
      <h2 class="dashboard__title">数据仪表盘</h2>
      <p class="dashboard__subtitle">查看你的学习数据和成长轨迹</p>
    </div>

    <!-- 统计卡片 -->
    <div class="dashboard__stats">
      <StatsCard
        title="笔记数"
        :value="stats.notes"
        color="#2563eb"
        :trend="12"
        @click="goTab('notes')"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" />
            <line x1="16" y1="17" x2="8" y2="17" />
          </svg>
        </template>
      </StatsCard>

      <StatsCard
        title="资源数"
        :value="stats.resources"
        color="#7c3aed"
        :trend="8"
        @click="goTab('resources')"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
          </svg>
        </template>
      </StatsCard>

      <StatsCard
        title="评论数"
        :value="stats.comments"
        color="#0891b2"
        :trend="23"
        @click="goTab('comments')"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </template>
      </StatsCard>

      <StatsCard
        title="获赞数"
        :value="stats.likes"
        color="#ea580c"
        :trend="-3"
        @click="goTab('likes')"
      >
        <template #icon>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
          </svg>
        </template>
      </StatsCard>
    </div>

    <!-- 热力图 -->
    <div class="dashboard__section card">
      <h3 class="dashboard__section-title">学习热力图</h3>
      <ContributionHeatmap :data="contributionData" />
    </div>

    <!-- 底部：趋势 + 分类 -->
    <div class="dashboard__bottom">
      <!-- 学习趋势（纯 CSS 柱状图） -->
      <div class="dashboard__section card dashboard__trend">
        <h3 class="dashboard__section-title">学习趋势</h3>
        <div class="bar-chart">
          <div
            v-for="(item, index) in trendData"
            :key="index"
            class="bar-chart__item"
          >
            <div class="bar-chart__bar-wrapper">
              <div
                class="bar-chart__bar"
                :style="{
                  height: (item.value / trendMax) * 100 + '%',
                }"
              >
                <span class="bar-chart__value">{{ item.value }}</span>
              </div>
            </div>
            <span class="bar-chart__label">{{ item.month }}</span>
          </div>
        </div>
      </div>

      <!-- 分类分布（纯 CSS 饼图） -->
      <div class="dashboard__section card dashboard__category">
        <h3 class="dashboard__section-title">分类分布</h3>
        <div class="pie-chart">
          <div class="pie-chart__circle" :style="{ background: pieGradient }"></div>
          <div class="pie-chart__center">
            <span class="pie-chart__total">100%</span>
            <span class="pie-chart__label">总计</span>
          </div>
        </div>
        <div class="pie-legend">
          <div
            v-for="cat in categoryData"
            :key="cat.name"
            class="pie-legend__item"
          >
            <span class="pie-legend__dot" :style="{ background: cat.color }"></span>
            <span class="pie-legend__name">{{ cat.name }}</span>
            <span class="pie-legend__pct">{{ cat.value }}%</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-2xl) var(--space-lg);
  animation: slideUpFade 0.5s var(--ease-out) both;
}

.dashboard__header {
  margin-bottom: var(--space-2xl);
}

.dashboard__title {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: var(--space-xs);
}

.dashboard__subtitle {
  font-size: 0.9375rem;
  color: var(--color-text-secondary);
}

/* 统计卡片网格 */
.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--space-lg);
  margin-bottom: var(--space-2xl);
}

@media (max-width: 1024px) {
  .dashboard__stats {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .dashboard__stats {
    grid-template-columns: 1fr;
  }
}

/* 区块通用 */
.dashboard__section {
  padding: var(--space-xl);
  margin-bottom: var(--space-2xl);
}

.dashboard__section-title {
  font-family: var(--font-body);
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: var(--space-lg);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

/* 底部双栏 */
.dashboard__bottom {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: var(--space-lg);
}

@media (max-width: 768px) {
  .dashboard__bottom {
    grid-template-columns: 1fr;
  }
}

/* ===== 柱状图 ===== */
.bar-chart {
  display: flex;
  align-items: flex-end;
  gap: var(--space-md);
  height: 220px;
  padding: var(--space-md) 0;
}

.bar-chart__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  height: 100%;
}

.bar-chart__bar-wrapper {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.bar-chart__bar {
  width: 100%;
  max-width: 48px;
  background: linear-gradient(180deg, var(--color-accent), var(--color-accent-light));
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  position: relative;
  transition: height 0.6s var(--ease-out);
  min-height: 4px;
}

.bar-chart__bar:hover {
  filter: brightness(1.1);
}

.bar-chart__value {
  position: absolute;
  top: -24px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-accent);
  white-space: nowrap;
}

.bar-chart__label {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  font-weight: 500;
}

/* ===== 饼图 ===== */
.pie-chart {
  position: relative;
  width: 200px;
  height: 200px;
  margin: 0 auto var(--space-lg);
}

.pie-chart__circle {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  transition: background 0.4s var(--ease-out);
}

.pie-chart__center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 90px;
  height: 90px;
  background: var(--color-bg-card);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
}

.pie-chart__total {
  font-family: var(--font-display);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-primary);
}

.pie-chart__label {
  font-size: 0.6875rem;
  color: var(--color-text-muted);
}

.pie-legend {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.pie-legend__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 0.8125rem;
}

.pie-legend__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.pie-legend__name {
  flex: 1;
  color: var(--color-text);
  font-weight: 500;
}

.pie-legend__pct {
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
</style>
