<template>
  <div class="reconcile-page">
    <!-- 页头 -->
    <a-card :bordered="false" class="header-card">
      <div class="header-row">
        <div class="header-text">
          <h2 class="page-title">积分对账</h2>
          <p class="page-desc">
            平台发放 / 用户消耗 / 平台抽成。抽成刻意不落任何账户，统一由成交表反推（成交额 − 收款方实得）。
          </p>
        </div>
        <a-radio-group v-model:value="days" button-style="solid" @change="load">
          <a-radio-button :value="7">近 7 天</a-radio-button>
          <a-radio-button :value="30">近 30 天</a-radio-button>
          <a-radio-button :value="90">近 90 天</a-radio-button>
        </a-radio-group>
      </div>
    </a-card>

    <a-spin :spinning="loading">
      <!-- 概览 -->
      <div class="stat-grid">
        <div v-for="s in stats" :key="s.label" class="stat-card">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value" :style="{ color: s.color || 'var(--color-text, #1a1f36)' }">
            {{ s.value }}<span v-if="s.unit" class="stat-unit">{{ s.unit }}</span>
          </div>
          <div class="stat-hint">{{ s.hint }}</div>
        </div>
      </div>

      <!-- 抽成分渠道 -->
      <a-card :bordered="false" class="block-card" title="平台抽成（分渠道）">
        <template #extra>
          <span class="card-extra">抽成比例 10%，不落账户、直接销毁</span>
        </template>
        <a-empty v-if="!hasFee" description="暂无成交记录" />
        <div v-else class="channel-list">
          <div v-for="c in feeByChannel" :key="c.channel" class="channel-row">
            <span class="channel-name">{{ channelText[c.channel] || c.channel }}</span>
            <div class="channel-bar">
              <div class="channel-bar-inner" :style="{ width: barWidth(c.fee, maxFee) }"></div>
            </div>
            <span class="channel-fee">{{ c.fee }} 积分</span>
            <span class="channel-orders">{{ c.orders }} 单</span>
          </div>
        </div>
      </a-card>

      <!-- 趋势 -->
      <a-card :bordered="false" class="block-card" :title="`近 ${days} 天趋势`">
        <a-empty v-if="daily.length === 0" description="该区间内没有积分流水" />
        <a-table
          v-else
          :columns="dailyColumns"
          :data-source="daily"
          :pagination="false"
          size="small"
          row-key="date"
          :scroll="{ x: 760 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'earned'">
              <div class="bar-cell">
                <span class="bar bar-earned" :style="{ width: barWidth(record.earned, maxDaily) }"></span>
                <span class="bar-num">{{ record.earned }}</span>
              </div>
            </template>
            <template v-else-if="column.dataIndex === 'spent'">
              <div class="bar-cell">
                <span class="bar bar-spent" :style="{ width: barWidth(record.spent, maxDaily) }"></span>
                <span class="bar-num">{{ record.spent }}</span>
              </div>
            </template>
            <template v-else-if="column.dataIndex === 'fee'">
              <div class="bar-cell">
                <span class="bar bar-fee" :style="{ width: barWidth(record.fee, maxDaily) }"></span>
                <span class="bar-num">{{ record.fee }}</span>
              </div>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 榜单 -->
      <div class="rank-grid">
        <a-card :bordered="false" class="block-card" title="收入榜（靠内容赚到积分）">
          <a-empty v-if="topEarners.length === 0" description="暂无数据" />
          <div v-else class="rank-list">
            <div v-for="(u, i) in topEarners" :key="u.userId" class="rank-item">
              <span class="rank-no" :class="`rank-no-${i + 1}`">{{ i + 1 }}</span>
              <span class="rank-name" :title="u.username">{{ u.username }}</span>
              <span class="rank-amount earn">+{{ u.amount }}</span>
            </div>
          </div>
        </a-card>

        <a-card :bordered="false" class="block-card" title="消费榜（花掉积分最多）">
          <a-empty v-if="topSpenders.length === 0" description="暂无数据" />
          <div v-else class="rank-list">
            <div v-for="(u, i) in topSpenders" :key="u.userId" class="rank-item">
              <span class="rank-no" :class="`rank-no-${i + 1}`">{{ i + 1 }}</span>
              <span class="rank-name" :title="u.username">{{ u.username }}</span>
              <span class="rank-amount spend">-{{ u.amount }}</span>
            </div>
          </div>
        </a-card>
      </div>
    </a-spin>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { getPointsReconcile } from '../../utils/api'

const loading = ref(false)
const days = ref(30)
const overview = ref({})
const feeByChannel = ref([])
const daily = ref([])
const topEarners = ref([])
const topSpenders = ref([])

const channelText = {
  note_unlock: '笔记解锁',
  note_tip: '笔记打赏',
  resource_download: '资源下载',
}

const dailyColumns = [
  { title: '日期', dataIndex: 'date', width: 120 },
  { title: '平台发放', dataIndex: 'earned', width: 200 },
  { title: '用户消耗', dataIndex: 'spent', width: 200 },
  { title: '平台抽成', dataIndex: 'fee', width: 200 },
  { title: '成交单数', dataIndex: 'orders', width: 100 },
]

const stats = computed(() => {
  const o = overview.value || {}
  return [
    { label: '积分账户', value: o.accountCount ?? 0, hint: '已开户用户数' },
    { label: '未消费存量', value: o.totalBalance ?? 0, hint: '全平台余额之和' },
    {
      label: '累计发放',
      value: o.totalIssued ?? 0,
      color: 'var(--color-success, #16a34a)',
      hint: '仅平台发放（签到/发布/被赞/评论）',
    },
    {
      label: '累计消耗',
      value: o.totalSpent ?? 0,
      color: 'var(--color-error, #dc2626)',
      hint: '解锁 / 打赏 / 置顶 / 下载 / 开星球',
    },
    {
      label: '平台抽成',
      value: o.platformFee ?? 0,
      color: 'var(--color-accent, #2563eb)',
      hint: '成交额的 10%，直接销毁不落账户',
    },
    { label: '成交单数', value: o.orderCount ?? 0, hint: '解锁 + 打赏 + 资源下载' },
    {
      label: '今日已发放',
      value: o.todayIssued ?? 0,
      hint: `单用户每日上限 ${o.dailyEarnLimit ?? 200} 分`,
    },
    { label: '累计获得', value: o.totalEarned ?? 0, hint: '账户 total_earned 之和（定级依据）' },
  ]
})

const hasFee = computed(() => feeByChannel.value.some((c) => Number(c.fee) > 0))
const maxFee = computed(() => Math.max(1, ...feeByChannel.value.map((c) => Number(c.fee) || 0)))
const maxDaily = computed(() => {
  let m = 1
  for (const d of daily.value) {
    m = Math.max(m, Number(d.earned) || 0, Number(d.spent) || 0, Number(d.fee) || 0)
  }
  return m
})

// 条形宽度：最低保留 2%，避免「有值但看不见」
const barWidth = (value, max) => {
  const v = Number(value) || 0
  if (v <= 0) return '0%'
  return Math.max(2, Math.round((v / (max || 1)) * 100)) + '%'
}

const load = async () => {
  loading.value = true
  try {
    const res = await getPointsReconcile({ days: days.value })
    const data = res?.data || {}
    overview.value = data.overview || {}
    feeByChannel.value = data.feeByChannel || []
    daily.value = data.daily || []
    topEarners.value = data.topEarners || []
    topSpenders.value = data.topSpenders || []
  } catch (err) {
    antMessage.error(err?.description || err?.message || '加载对账数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.reconcile-page {
  padding: 4px;
}

.header-card,
.block-card {
  margin-bottom: 14px;
  border-radius: 14px;
  box-shadow: 0 1px 3px rgba(26, 31, 54, 0.05);
}

.header-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text, #1a1f36);
}

.page-desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-secondary, #64748b);
  max-width: 640px;
  line-height: 1.6;
}

.card-extra {
  font-size: 12px;
  color: var(--color-text-muted, #94a3b8);
}

/* 概览卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stat-card {
  background: #fff;
  border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 14px;
  padding: 14px 16px;
  box-shadow: 0 1px 3px rgba(26, 31, 54, 0.04);
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary, #64748b);
}

.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.stat-unit {
  font-size: 12px;
  font-weight: 400;
  margin-left: 3px;
  color: var(--color-text-muted, #94a3b8);
}

.stat-hint {
  margin-top: 5px;
  font-size: 11px;
  color: var(--color-text-muted, #94a3b8);
  line-height: 1.5;
}

/* 渠道条形 */
.channel-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.channel-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.channel-name {
  width: 84px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text, #1a1f36);
}

.channel-bar {
  flex: 1;
  height: 8px;
  background: #f1f5f9;
  border-radius: 999px;
  overflow: hidden;
}

.channel-bar-inner {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-accent) 0%, var(--color-accent-light) 100%);
  transition: width 0.3s ease;
}

.channel-fee {
  width: 90px;
  flex-shrink: 0;
  text-align: right;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--color-text, #1a1f36);
}

.channel-orders {
  width: 60px;
  flex-shrink: 0;
  text-align: right;
  font-size: 12px;
  color: var(--color-text-muted, #94a3b8);
}

/* 趋势条 */
.bar-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bar {
  height: 8px;
  border-radius: 999px;
  min-width: 0;
  transition: width 0.3s ease;
}

.bar-earned {
  background: #86efac;
}

.bar-spent {
  background: #fca5a5;
}

.bar-fee {
  background: #93c5fd;
}

.bar-num {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary, #64748b);
  flex-shrink: 0;
}

/* 榜单 */
.rank-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
}

.rank-list {
  display: flex;
  flex-direction: column;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--color-border-light, #f1f5f9);
}

.rank-item:last-child {
  border-bottom: none;
}

.rank-no {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  background: #f1f5f9;
  color: var(--color-text-secondary, #64748b);
}

.rank-no-1 {
  background: #fef3c7;
  color: #b45309;
}

.rank-no-2 {
  background: #e2e8f0;
  color: #475569;
}

.rank-no-3 {
  background: #fde8d7;
  color: #c2410c;
}

.rank-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--color-text, #1a1f36);
}

.rank-amount {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.rank-amount.earn {
  color: var(--color-success, #16a34a);
}

.rank-amount.spend {
  color: var(--color-error, #dc2626);
}
</style>
