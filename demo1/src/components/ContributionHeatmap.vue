<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => [],
    // 每项格式: { date: '2025-01-15', count: 3 }
  },
})

// 生成 365 天的数据映射
const dataMap = computed(() => {
  const map = {}
  props.data.forEach((item) => {
    map[item.date] = item.count
  })
  return map
})

// 计算起始日期（53 周前的最近一个周日）
const startDate = computed(() => {
  const today = new Date()
  const dayOfWeek = today.getDay()
  const totalDays = 53 * 7 - 1
  const start = new Date(today)
  start.setDate(today.getDate() - totalDays + (6 - dayOfWeek))
  return start
})

// 生成所有方格
const cells = computed(() => {
  const result = []
  const start = startDate.value
  const today = new Date()
  const d = new Date(start)

  while (d <= today) {
    const dateStr = formatDate(d)
    const count = dataMap.value[dateStr] || 0
    result.push({
      date: dateStr,
      count,
      level: getLevel(count),
    })
    d.setDate(d.getDate() + 1)
  }
  return result
})

// 按周分组
const weeks = computed(() => {
  const result = []
  for (let i = 0; i < cells.value.length; i += 7) {
    result.push(cells.value.slice(i, i + 7))
  }
  return result
})

// 月份标签
const monthLabels = computed(() => {
  const labels = []
  let lastMonth = -1
  weeks.value.forEach((week, index) => {
    const firstDay = week[0]
    if (firstDay) {
      const month = new Date(firstDay.date).getMonth()
      if (month !== lastMonth) {
        labels.push({ weekIndex: index, month })
        lastMonth = month
      }
    }
  })
  return labels
})

const MONTH_NAMES = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const DAY_LABELS = ['', '一', '', '三', '', '五', '']

function getLevel(count) {
  if (count <= 0) return 0
  if (count <= 3) return 1
  if (count <= 6) return 2
  return 3
}

function formatDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// tooltip
const tooltip = ref({ visible: false, x: 0, y: 0, date: '', count: 0 })

function showTooltip(event, cell) {
  const rect = event.target.getBoundingClientRect()
  const container = event.currentTarget.closest('.heatmap').getBoundingClientRect()
  tooltip.value = {
    visible: true,
    x: rect.left - container.left + rect.width / 2,
    y: rect.top - container.top - 8,
    date: cell.date,
    count: cell.count,
  }
}

function hideTooltip() {
  tooltip.value.visible = false
}
</script>

<template>
  <div class="heatmap">
    <div class="heatmap__months">
      <span
        v-for="label in monthLabels"
        :key="label.weekIndex"
        class="heatmap__month-label"
        :style="{ gridColumn: label.weekIndex + 2 }"
      >
        {{ MONTH_NAMES[label.month] }}
      </span>
    </div>
    <div class="heatmap__body">
      <div class="heatmap__days">
        <span
          v-for="(day, i) in DAY_LABELS"
          :key="i"
          class="heatmap__day-label"
        >
          {{ day }}
        </span>
      </div>
      <div class="heatmap__grid">
        <div
          v-for="(week, wi) in weeks"
          :key="wi"
          class="heatmap__week"
        >
          <div
            v-for="(cell, ci) in week"
            :key="ci"
            class="heatmap__cell"
            :class="[`heatmap__cell--level-${cell.level}`]"
            @mouseenter="showTooltip($event, cell)"
            @mouseleave="hideTooltip"
          ></div>
        </div>
      </div>
    </div>
    <div class="heatmap__legend">
      <span class="heatmap__legend-text">少</span>
      <div class="heatmap__cell heatmap__cell--level-0"></div>
      <div class="heatmap__cell heatmap__cell--level-1"></div>
      <div class="heatmap__cell heatmap__cell--level-2"></div>
      <div class="heatmap__cell heatmap__cell--level-3"></div>
      <span class="heatmap__legend-text">多</span>
    </div>

    <!-- 浮动提示 -->
    <Teleport to="body">
      <div
        v-if="tooltip.visible"
        class="heatmap-tooltip"
        :style="{
          left: tooltip.x + 'px',
          top: tooltip.y + 'px',
          position: 'fixed',
        }"
      >
        <strong>{{ tooltip.date }}</strong>
        <span>{{ tooltip.count }} 次学习</span>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.heatmap {
  position: relative;
  padding: var(--space-md) 0;
  overflow-x: auto;
}

.heatmap__months {
  display: grid;
  grid-template-columns: 36px repeat(53, 1fr);
  margin-bottom: var(--space-xs);
  min-width: 720px;
}

.heatmap__month-label {
  font-size: 0.6875rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.heatmap__body {
  display: flex;
  gap: var(--space-xs);
  min-width: 720px;
}

.heatmap__days {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 0;
  width: 28px;
  flex-shrink: 0;
}

.heatmap__day-label {
  height: 13px;
  display: flex;
  align-items: center;
  font-size: 0.625rem;
  color: var(--color-text-muted);
  font-weight: 500;
}

.heatmap__grid {
  display: flex;
  gap: 3px;
}

.heatmap__week {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.heatmap__cell {
  width: 13px;
  height: 13px;
  border-radius: 3px;
  background: var(--color-border-light);
  transition: all var(--duration-fast) var(--ease-in-out);
  cursor: pointer;
}

.heatmap__cell:hover {
  outline: 2px solid var(--color-text-muted);
  outline-offset: 1px;
}

.heatmap__cell--level-0 {
  background: var(--color-border-light);
}

.heatmap__cell--level-1 {
  background: rgba(37, 99, 235, 0.25);
}

.heatmap__cell--level-2 {
  background: rgba(37, 99, 235, 0.5);
}

.heatmap__cell--level-3 {
  background: var(--color-accent);
}

.heatmap__legend {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  margin-top: var(--space-sm);
  padding-right: var(--space-xs);
}

.heatmap__legend-text {
  font-size: 0.625rem;
  color: var(--color-text-muted);
  margin: 0 2px;
}

.heatmap-tooltip {
  transform: translate(-50%, -100%);
  background: var(--color-primary);
  color: #fff;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  pointer-events: none;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  box-shadow: var(--shadow-lg);
  white-space: nowrap;
}

.heatmap-tooltip strong {
  font-weight: 600;
}

.heatmap-tooltip span {
  color: rgba(255, 255, 255, 0.8);
  font-size: 0.6875rem;
}
</style>
