<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  liked: { type: Boolean, default: false },
  count: { type: Number, default: 0 },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['toggle'])

const animating = ref(false)
const particles = Array.from({ length: 8 }, (_, i) => ({
  id: i,
  angle: (i * 45) * (Math.PI / 180),
}))

const handleClick = () => {
  if (props.loading) return
  if (!props.liked) {
    animating.value = true
    setTimeout(() => { animating.value = false }, 700)
  }
  emit('toggle')
}

const displayCount = computed(() => {
  if (props.count >= 10000) return (props.count / 10000).toFixed(1) + 'w'
  if (props.count >= 1000) return (props.count / 1000).toFixed(1) + 'k'
  return props.count
})
</script>

<template>
  <button
    class="like-btn"
    :class="{ 'like-btn--liked': liked, 'like-btn--animating': animating }"
    @click="handleClick"
    :disabled="loading"
  >
    <!-- 粒子爆炸 -->
    <div class="particles" v-if="animating">
      <span
        v-for="p in particles"
        :key="p.id"
        class="particle"
        :style="{
          '--angle': p.angle + 'rad',
          '--delay': (p.id * 0.03) + 's',
        }"
      />
    </div>

    <!-- 心形图标 -->
    <span class="heart-icon">
      <svg viewBox="0 0 24 24" :fill="liked ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="2">
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
      </svg>
    </span>

    <!-- 计数 -->
    <span class="like-count" v-if="count > 0">{{ displayCount }}</span>
  </button>
</template>

<style scoped>
.like-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s var(--ease-out);
  overflow: visible;
  font-family: var(--font-body);
}

.like-btn:hover {
  border-color: #f87171;
  color: #f87171;
  background: rgba(248, 113, 113, 0.06);
}

.like-btn--liked {
  border-color: #ef4444;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
}

.like-btn--liked:hover {
  border-color: #dc2626;
  color: #dc2626;
}

.heart-icon {
  width: 18px;
  height: 18px;
  display: flex;
  transition: transform 0.25s var(--ease-out);
}

.heart-icon svg {
  width: 100%;
  height: 100%;
}

.like-btn--animating .heart-icon {
  animation: heartPop 0.45s var(--ease-out);
}

.like-count {
  font-variant-numeric: tabular-nums;
  min-width: 12px;
  text-align: center;
}

/* 心形弹跳 */
@keyframes heartPop {
  0% { transform: scale(1); }
  15% { transform: scale(0.85); }
  40% { transform: scale(1.3); }
  65% { transform: scale(0.95); }
  85% { transform: scale(1.08); }
  100% { transform: scale(1); }
}

/* 粒子爆炸 */
.particles {
  position: absolute;
  top: 50%;
  left: 24px;
  width: 0;
  height: 0;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
  animation: particleBurst 0.6s var(--ease-out) var(--delay) both;
}

.particle:nth-child(odd) {
  background: #f87171;
  width: 5px;
  height: 5px;
}

.particle:nth-child(3n) {
  background: #fca5a5;
  width: 4px;
  height: 4px;
}

@keyframes particleBurst {
  0% {
    transform: translate(0, 0) scale(1);
    opacity: 1;
  }
  100% {
    transform: translate(
      calc(cos(var(--angle)) * 28px),
      calc(sin(var(--angle)) * 28px)
    ) scale(0);
    opacity: 0;
  }
}
</style>
