<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageLoading from './components/PageLoading.vue'

const router = useRouter()
const isLoading = ref(false)
const transitionName = ref('page-fade')

router.beforeEach((to, from) => {
  isLoading.value = true
  const toDepth = to.path.split('/').length
  const fromDepth = from.path.split('/').length
  transitionName.value = toDepth >= fromDepth ? 'page-slide-left' : 'page-slide-right'
})

router.afterEach(() => {
  setTimeout(() => {
    isLoading.value = false
  }, 300)
})
</script>

<template>
  <div id="app">
    <PageLoading v-if="isLoading" />
    <router-view v-slot="{ Component, route }">
      <transition :name="transitionName" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<style scoped>
/* 基础淡入淡出 */
.page-fade-enter-active {
  transition: opacity 0.4s var(--ease-out), transform 0.4s var(--ease-out);
}

.page-fade-leave-active {
  transition: opacity 0.25s var(--ease-in-out), transform 0.25s var(--ease-in-out);
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(12px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 前进 - 左滑 */
.page-slide-left-enter-active {
  transition: opacity 0.4s var(--ease-out), transform 0.4s var(--ease-out);
}

.page-slide-left-leave-active {
  transition: opacity 0.3s var(--ease-in-out), transform 0.3s var(--ease-in-out);
}

.page-slide-left-enter-from {
  opacity: 0;
  transform: translateX(30px);
}

.page-slide-left-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}

/* 后退 - 右滑 */
.page-slide-right-enter-active {
  transition: opacity 0.4s var(--ease-out), transform 0.4s var(--ease-out);
}

.page-slide-right-leave-active {
  transition: opacity 0.3s var(--ease-in-out), transform 0.3s var(--ease-in-out);
}

.page-slide-right-enter-from {
  opacity: 0;
  transform: translateX(-30px);
}

.page-slide-right-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

#app {
  width: 100%;
  min-height: 100vh;
}
</style>
