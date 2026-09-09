<script setup>
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  value: { type: [Number, String], required: true },
  icon: { type: [Object, Function], default: null },
  color: { type: String, default: 'var(--color-accent)' },
  trend: { type: Number, default: undefined },
})

defineEmits(['click'])

const trendDisplay = computed(() => {
  if (props.trend === undefined || props.trend === null) return null
  const sign = props.trend >= 0 ? '+' : ''
  return `${sign}${props.trend}%`
})

const trendClass = computed(() => {
  if (props.trend === undefined || props.trend === null) return ''
  return props.trend >= 0 ? 'trend--up' : 'trend--down'
})
</script>

<template>
  <div class="stats-card" @click="$emit('click', $event)">
    <div class="stats-card__header">
      <span class="stats-card__title">{{ title }}</span>
      <div class="stats-card__icon" :style="{ background: color + '15', color: color }">
        <slot name="icon">
          <component :is="icon" v-if="icon" />
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
          </svg>
        </slot>
      </div>
    </div>
    <div class="stats-card__body">
      <span class="stats-card__value" :style="{ color: color }">{{ value }}</span>
      <span v-if="trendDisplay" class="stats-card__trend" :class="trendClass">
        <svg v-if="trend >= 0" viewBox="0 0 12 12" fill="currentColor" class="trend-arrow">
          <path d="M6 2l4 5H2z" />
        </svg>
        <svg v-else viewBox="0 0 12 12" fill="currentColor" class="trend-arrow">
          <path d="M6 10l4-5H2z" />
        </svg>
        {{ trendDisplay }}
      </span>
    </div>
    <div class="stats-card__bar" :style="{ background: `linear-gradient(90deg, ${color}, ${color}40)` }"></div>
  </div>
</template>

<style scoped>
.stats-card {
  position: relative;
  background: var(--color-bg-card);
  cursor: pointer;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  padding: var(--space-lg);
  overflow: hidden;
  transition: all var(--duration-normal) var(--ease-out);
  box-shadow: var(--shadow-sm);
}

.stats-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stats-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.stats-card__title {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
}

.stats-card__icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stats-card__icon svg {
  width: 20px;
  height: 20px;
}

.stats-card__body {
  display: flex;
  align-items: baseline;
  gap: var(--space-sm);
}

.stats-card__value {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
  line-height: 1;
  letter-spacing: -0.02em;
}

.stats-card__trend {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 0.8125rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.trend--up {
  color: #16a34a;
  background: rgba(22, 163, 74, 0.1);
}

.trend--down {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.1);
}

.trend-arrow {
  width: 10px;
  height: 10px;
}

.stats-card__bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 3px;
}
</style>
