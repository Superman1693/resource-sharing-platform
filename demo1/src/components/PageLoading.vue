<script setup>
import { ref, onMounted } from 'vue'

const progress = ref(0)
const visible = ref(false)
let timer = null

const start = () => {
  visible.value = true
  progress.value = 0
  timer = setInterval(() => {
    if (progress.value < 90) {
      progress.value += Math.random() * 15
    }
  }, 100)
}

const finish = () => {
  progress.value = 100
  clearInterval(timer)
  setTimeout(() => {
    visible.value = false
    progress.value = 0
  }, 300)
}

onMounted(() => {
  start()
  setTimeout(finish, 600)
})
</script>

<template>
  <div class="page-loading" v-if="visible">
    <div class="loading-bar" :style="{ width: progress + '%' }"></div>
  </div>
</template>

<style scoped>
.page-loading {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 9999;
  height: 3px;
  pointer-events: none;
}

.loading-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--color-accent), var(--color-accent-light));
  border-radius: 0 2px 2px 0;
  transition: width 0.2s ease;
  box-shadow: 0 0 10px var(--color-accent-glow);
}
</style>
