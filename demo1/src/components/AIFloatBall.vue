<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { RobotOutlined } from '@ant-design/icons-vue'
import { debounce } from 'lodash-es'

const props = defineProps({
  visible: { type: Boolean, default: false },
  unreadCount: { type: Number, default: 0 }
})

const emit = defineEmits(['update:visible', 'toggle'])

const ballState = ref({
  x: window.innerWidth - 80,
  y: window.innerHeight / 2 - 30,
  isDragging: false,
  dragStart: { x: 0, y: 0 }
})

let rafId = null
let lastX = 0
let lastY = 0
// 修复点：区分拖拽和点击，拖拽超过阈值才视为拖拽
let dragMoved = false

const getEventCoords = (e) => {
  // 修复点：touch 事件需从 e.touches[0] 或 e.changedTouches[0] 取坐标
  if (e.touches && e.touches.length > 0) return { x: e.touches[0].clientX, y: e.touches[0].clientY }
  if (e.changedTouches && e.changedTouches.length > 0) return { x: e.changedTouches[0].clientX, y: e.changedTouches[0].clientY }
  return { x: e.clientX, y: e.clientY }
}

const startDrag = (e) => {
  e.preventDefault()
  e.stopPropagation()
  const { x, y } = getEventCoords(e)
  dragMoved = false
  ballState.value.isDragging = true
  ballState.value.dragStart = {
    x: x - ballState.value.x,
    y: y - ballState.value.y
  }
}

const onDrag = (e) => {
  if (!ballState.value.isDragging) return
  const { x, y } = getEventCoords(e)
  lastX = x
  lastY = y
  dragMoved = true
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    ballState.value.x = Math.max(0, Math.min(window.innerWidth - 60, lastX - ballState.value.dragStart.x))
    ballState.value.y = Math.max(0, Math.min(window.innerHeight - 60, lastY - ballState.value.dragStart.y))
    rafId = null
  })
}

const endDrag = (e) => {
  if (!ballState.value.isDragging) return
  ballState.value.isDragging = false
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
  // 修复点：只有未发生拖拽移动时才触发 toggle（区分点击与拖拽）
  if (!dragMoved) emit('toggle')
}

const resizeDebounce = debounce(() => {
  ballState.value.x = Math.max(0, Math.min(window.innerWidth - 60, ballState.value.x))
  ballState.value.y = Math.max(0, Math.min(window.innerHeight - 60, ballState.value.y))
}, 200)

const displayUnreadCount = computed(() => {
  if (props.unreadCount <= 0) return null
  return props.unreadCount > 99 ? '99+' : String(props.unreadCount)
})

onMounted(() => {
  document.addEventListener('mousemove', onDrag)
  document.addEventListener('mouseup', endDrag)
  document.addEventListener('touchmove', onDrag, { passive: false })
  document.addEventListener('touchend', endDrag)
  window.addEventListener('resize', resizeDebounce)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', onDrag)
  document.removeEventListener('mouseup', endDrag)
  document.removeEventListener('touchmove', onDrag)
  document.removeEventListener('touchend', endDrag)
  window.removeEventListener('resize', resizeDebounce)
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<template>
  <Teleport to="body">
    <div
      class="ai-float-ball"
      :class="{ 'is-dragging': ballState.isDragging }"
      :style="{ left: ballState.x + 'px', top: ballState.y + 'px' }"
      @mousedown="startDrag"
      @touchstart.prevent="startDrag"
      tabindex="0"
      role="button"
      aria-label="AI 助手"
    >
      <div class="ball-inner">
        <!-- 修复点：图标改为白色，与深色背景形成对比 -->
        <RobotOutlined class="ball-icon" />
        <transition name="badge">
          <div v-if="displayUnreadCount" class="message-badge">
            <span>{{ displayUnreadCount }}</span>
          </div>
        </transition>
        <div class="online-dot"></div>
      </div>
      <div class="ball-glow"></div>
    </div>
  </Teleport>
</template>

<style scoped>
.ai-float-ball {
  position: fixed;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  z-index: 1000;
  user-select: none;
  will-change: left, top;
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.ai-float-ball:focus { outline: none; }
.ai-float-ball.is-dragging { cursor: grabbing; transition: none; }

.ball-inner {
  position: relative;
  width: 52px;
  height: 52px;
  background: linear-gradient(135deg, var(--color-accent) 0%, var(--color-accent-light) 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 20px rgba(27, 53, 87, 0.45), inset 0 1px 3px rgba(255,255,255,0.15);
  border: 2px solid rgba(255, 255, 255, 0.12);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  z-index: 2;
}

.ai-float-ball:not(.is-dragging):hover .ball-inner {
  transform: scale(1.08);
  box-shadow: 0 6px 28px rgba(27, 53, 87, 0.55), inset 0 1px 3px rgba(255,255,255,0.2);
}

/* 修复点：图标颜色改为白色 */
.ball-icon {
  font-size: 22px;
  color: #ffffff;
  filter: drop-shadow(0 1px 3px rgba(0,0,0,0.25));
}

.message-badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 18px;
  height: 18px;
  background: #ff4d4f;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  border: 2px solid #fff;
  z-index: 3;
}

.message-badge span {
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
}

.badge-enter-active, .badge-leave-active { transition: all 0.25s ease; }
.badge-enter-from, .badge-leave-to { opacity: 0; transform: scale(0.5); }

.online-dot {
  position: absolute;
  bottom: 2px;
  right: 2px;
  width: 9px;
  height: 9px;
  background: #52c41a;
  border-radius: 50%;
  border: 2px solid #fff;
  animation: onlinePulse 2.5s infinite;
}

@keyframes onlinePulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.75; transform: scale(1.15); }
}

.ball-glow {
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(27, 53, 87, 0.18) 0%, transparent 70%);
  z-index: 1;
  pointer-events: none;
  animation: glowPulse 3s infinite;
}

@keyframes glowPulse {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.25); opacity: 0.25; }
}

@media (max-width: 768px) {
  .ai-float-ball { width: 50px; height: 50px; }
  .ball-inner { width: 46px; height: 46px; }
  .ball-icon { font-size: 20px; }
}
</style>
