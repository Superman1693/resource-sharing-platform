<script setup>
/**
 * 统一用户头像组件
 * avatarUrl 为空时回退到项目默认头像，避免显示灰色占位头像
 */
import { computed } from 'vue'
import defaultAvatar from '../assets/images/1.jpg'

const props = defineProps({
  src: { type: String, default: '' },
})

const resolvedSrc = computed(() => {
  const url = typeof props.src === 'string' ? props.src.trim() : ''
  return url || defaultAvatar
})
</script>

<template>
  <!-- size / class / shape 等属性透传给 a-avatar -->
  <a-avatar :src="resolvedSrc"><slot /></a-avatar>
</template>
