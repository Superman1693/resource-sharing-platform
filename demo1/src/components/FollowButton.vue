<script setup>
import { ref, watch } from 'vue'
import { followUser, unfollowUser } from '../utils/api'
import { message } from 'ant-design-vue'

const props = defineProps({
  userId: { type: Number, required: true },
  initialFollowing: { type: Boolean, default: false }
})

const emit = defineEmits(['follow', 'unfollow'])

const isFollowing = ref(props.initialFollowing)
const loading = ref(false)
const hovering = ref(false)

// 监听外部状态变化
watch(() => props.initialFollowing, (val) => {
  isFollowing.value = val
})

const handleClick = async () => {
  if (loading.value) return
  loading.value = true

  try {
    if (isFollowing.value) {
      await unfollowUser(props.userId)
      isFollowing.value = false
      message.success('已取消关注')
      emit('unfollow', props.userId)
    } else {
      await followUser(props.userId)
      isFollowing.value = true
      message.success('关注成功')
      emit('follow', props.userId)
    }
  } catch (err) {
    console.error('关注操作失败', err)
    message.error(err?.description || err?.message || '操作失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <button
    class="follow-btn"
    :class="{
      'follow-btn--following': isFollowing,
      'follow-btn--loading': loading
    }"
    @click="handleClick"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
    :disabled="loading"
  >
    <span class="follow-btn__icon" v-if="loading">
      <span class="spinner"></span>
    </span>
    <span class="follow-btn__text">
      {{ isFollowing ? (hovering ? '取消关注' : '已关注') : '关注' }}
    </span>
  </button>
</template>

<style scoped>
.follow-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 6px 20px;
  border: 1.5px solid var(--color-accent, #6366f1);
  border-radius: 999px;
  background: var(--color-accent, #6366f1);
  color: #ffffff;
  font-size: 0.8125rem;
  font-weight: 600;
  font-family: var(--font-body, -apple-system, BlinkMacSystemFont, sans-serif);
  cursor: pointer;
  transition: all 0.25s var(--ease-out, ease);
  white-space: nowrap;
  min-width: 80px;
  height: 32px;
}

.follow-btn:hover:not(:disabled) {
  background: var(--color-accent-light, #818cf8);
  border-color: var(--color-accent-light, #818cf8);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  transform: translateY(-1px);
}

.follow-btn:active:not(:disabled) {
  transform: translateY(0);
}

/* 已关注状态 */
.follow-btn--following {
  background: transparent;
  color: var(--color-text-secondary, #64748b);
  border-color: var(--color-border, #e2e8f0);
}

.follow-btn--following:hover:not(:disabled) {
  background: #fef2f2;
  border-color: #f87171;
  color: #ef4444;
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.15);
}

/* 加载状态 */
.follow-btn--loading {
  opacity: 0.7;
  cursor: not-allowed;
}

.follow-btn__icon {
  display: inline-flex;
  align-items: center;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.follow-btn--following .spinner {
  border-color: rgba(100, 116, 139, 0.3);
  border-top-color: var(--color-text-secondary, #64748b);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.follow-btn__text {
  line-height: 1;
}
</style>
