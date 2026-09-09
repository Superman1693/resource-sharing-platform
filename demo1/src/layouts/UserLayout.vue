<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import circleUrl from '../assets/images/1.jpg'
import logoUrl from '../assets/images/logo.png'
import { EditOutlined, FullscreenOutlined, UploadOutlined, MessageOutlined } from '@ant-design/icons-vue'
import { message as antMessage } from 'ant-design-vue'
import AIFloatWindow from '../components/AIFloatWindow.vue'
import AIFloatBall from '../components/AIFloatBall.vue'
import NotificationBell from '../components/NotificationBell.vue'
import { getUnreadMessageCount } from '../utils/api'
import GlobalSearch from '../components/GlobalSearch.vue'
import { useUserStore } from '../store/userLogin'
import { useNotificationStore } from '../store/notification'
import { useFullscreen } from '../composables/useFullscreen'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { toggleFullscreen } = useFullscreen()
const currentYear = new Date().getFullYear()

const aiWindowVisible = ref(false)
const isLoggedIn = computed(() => userStore.isLogin)
const unreadMessageCount = ref(0)
const scrolled = ref(false)
const scrollProgress = ref(0)

const fetchUnreadMessageCount = async () => {
  if (!isLoggedIn.value) return
  try {
    const res = await getUnreadMessageCount()
    unreadMessageCount.value = res.data || 0
  } catch (_) {}
}

const toggleAIWindow = () => {
  if (!isLoggedIn.value) {
    antMessage.warning('请先登录后再使用 AI 助手')
    return
  }
  aiWindowVisible.value = !aiWindowVisible.value
}

const userInfo = computed(() => ({
  username: userStore.username || '未设置',
  userAccount: userStore.userAccount || '',
  avatarUrl: userStore.avatarUrl || '',
}))

const isHome = computed(() => route.name === 'UserHome')

const navLinks = [
  { label: '首页', path: '/user/home' },
  { label: '笔记', path: '/user/notes' },
  { label: '热榜', path: '/user/hotRank' },
  { label: '资源', path: '/user/resources' },
  { label: '星球', path: '/user/starList' },
]

const isActive = (path: string) =>
  route.path === path || route.path.startsWith(path + '/')

const requireLogin = (targetPath: string) => {
  if (isLoggedIn.value) {
    router.push(targetPath)
  } else {
    router.push({ path: '/login', query: { redirect: targetPath } })
  }
}

const requireMessageLogin = () => {
  if (isLoggedIn.value) {
    router.push('/user/messages')
  } else {
    router.push({ path: '/login', query: { redirect: '/user/messages' } })
  }
}

// 滚动监听
const handleScroll = () => {
  scrolled.value = window.scrollY > 8
  const docHeight = document.documentElement.scrollHeight - window.innerHeight
  scrollProgress.value = docHeight > 0 ? Math.min((window.scrollY / docHeight) * 100, 100) : 0
}

let messagePollTimer = null
onMounted(() => {
  fetchUnreadMessageCount()
  messagePollTimer = setInterval(fetchUnreadMessageCount, 60000)
  window.addEventListener('scroll', handleScroll, { passive: true })
  handleScroll()
  // 刷新页面后若已登录，恢复 WebSocket 实时连接
  if (userStore.isLogin) {
    useNotificationStore().connect()
  }
})
onUnmounted(() => {
  if (messagePollTimer) clearInterval(messagePollTimer)
  window.removeEventListener('scroll', handleScroll)
})

const handleLogout = async () => {
  await userStore.logout()
  ;['token', 'userInfo', 'userLogin', 'login', 'username', 'isLogin', '__DC_STAT_UUID'].forEach((k) =>
    localStorage.removeItem(k)
  )
  try {
    await router.replace('/user/home')
  } catch (_) {
    window.location.assign('/user/home')
  }
}
</script>

<template>
  <div class="user-layout">
    <!-- 滚动进度条 -->
    <div class="scroll-progress" :style="{ width: scrollProgress + '%' }" />

    <!-- 顶栏 -->
    <header class="topbar" :class="{ 'topbar-scrolled': scrolled }">
      <div class="topbar-inner">
        <!-- Logo -->
        <div class="topbar-logo" @click="router.push('/user/home')">
          <div class="logo-icon">
            <img :src="logoUrl" alt="Logo" />
          </div>
          <span class="logo-title">CodeVerse</span>
        </div>

        <!-- 导航链接 -->
        <nav class="nav-links">
          <a
            v-for="link in navLinks"
            :key="link.path"
            class="nav-link"
            :class="{ 'nav-active': isActive(link.path) }"
            @click="router.push(link.path)"
          >
            {{ link.label }}
          </a>
        </nav>

        <!-- 右侧功能区 -->
        <div class="topbar-actions">
          <GlobalSearch />

          <button class="action-btn-primary" @click="requireLogin('/user/publish')">
            <EditOutlined /> 写笔记
          </button>

          <button class="action-btn-outline" @click="requireLogin('/user/resourceAdd')">
            <UploadOutlined /> 上传
          </button>

          <button class="icon-btn" title="全屏" @click="toggleFullscreen()">
            <FullscreenOutlined />
          </button>

          <!-- 已登录 -->
          <template v-if="isLoggedIn">
            <NotificationBell />
            <div class="msg-entry-wrap" title="私信" @click="requireMessageLogin">
              <a-badge :count="unreadMessageCount" :overflow-count="99">
                <MessageOutlined class="msg-entry-icon" />
              </a-badge>
            </div>
            <a-dropdown placement="bottomRight" trigger="click">
              <template #overlay>
                <a-menu class="user-dropdown-menu">
                  <a-menu-item @click="router.push('/user/profile')">个人中心</a-menu-item>
                  <a-menu-item @click="router.push('/user/growthTimeline')">成长轨迹</a-menu-item>
                  <a-menu-item @click="router.push('/user/dashboard')">数据看板</a-menu-item>
                  <a-menu-item @click="router.push('/user/myContent')">我的内容</a-menu-item>
                  <a-menu-item @click="router.push('/changeUserPassword')">修改密码</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item @click="handleLogout" class="logout-item">退出登录</a-menu-item>
                </a-menu>
              </template>
              <a-avatar
                :size="34"
                :src="userInfo.avatarUrl?.trim() || circleUrl"
                class="header-avatar"
              />
            </a-dropdown>
          </template>

          <!-- 未登录 -->
          <template v-else>
            <button class="action-btn-ghost" @click="router.push('/login')">登录</button>
            <button class="action-btn-primary-sm" @click="router.push('/register')">注册</button>
          </template>
        </div>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="main-area">
      <div :class="{ 'page-wrap': !isHome }">
        <router-view />
      </div>
    </main>

    <!-- 页脚 -->
    <footer class="layout-footer">
      <div class="footer-inner">
        <div class="footer-brand">
          <span class="footer-logo">CodeVerse</span>
          <span class="footer-divider-dot">·</span>
          <span class="footer-text">用心记录，持续成长</span>
        </div>
        <span class="footer-copyright">© {{ currentYear }} CodeVerse</span>
      </div>
    </footer>

    <!-- AI 助手 -->
    <AIFloatWindow v-model:visible="aiWindowVisible" />
    <AIFloatBall v-model:visible="aiWindowVisible" @toggle="toggleAIWindow" />
  </div>
</template>

<style scoped>
/* 根容器 */
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
  font-family: var(--font-body);
}

/* ===== 滚动进度条 ===== */
.scroll-progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--color-accent), var(--color-accent-light));
  z-index: 300;
  transition: width 0.1s linear;
  border-radius: 0 2px 2px 0;
}

/* ===== 顶栏 ===== */
.topbar {
  position: fixed;
  inset-x: 0;
  top: 0;
  z-index: 200;
  width: 100%;
  height: 64px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(24px) saturate(1.6);
  -webkit-backdrop-filter: blur(24px) saturate(1.6);
  border-bottom: 1px solid transparent;
  transition: all var(--duration-normal) var(--ease-out);
}

.topbar-scrolled {
  background: rgba(255, 255, 255, 0.95);
  border-bottom-color: var(--color-border-light);
  box-shadow: 0 1px 8px rgba(26, 31, 54, 0.04);
}

.topbar-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 28px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Logo */
.topbar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity var(--duration-fast);
}

.topbar-logo:hover {
  opacity: 0.8;
}

.logo-icon {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  transition: transform var(--duration-normal) var(--ease-spring);
}

.topbar-logo:hover .logo-icon {
  transform: rotate(-6deg) scale(1.05);
}

.logo-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.logo-title {
  font-family: var(--font-display);
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: -0.01em;
}

/* 导航链接 */
.nav-links {
  padding-left: 40px;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.nav-link {
  display: inline-flex;
  align-items: center;
  padding: 6px 16px;
  font-size: 0.875rem;
  line-height: 1;
  font-weight: 500;
  color: var(--color-text-secondary);
  border-radius: 999px;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  user-select: none;
  white-space: nowrap;
  position: relative;
}

.nav-link:hover {
  color: var(--color-text);
  background: var(--color-bg-warm);
}

.nav-active {
  color: var(--color-accent) !important;
  background: var(--color-accent-glow) !important;
  font-weight: 600;
}

/* 右侧功能区 */
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  margin-left: auto;
}

/* 按钮样式 */
.action-btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 18px;
  height: 34px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  font-family: var(--font-body);
}

.action-btn-primary:hover {
  background: var(--color-accent-light);
  transform: translateY(-1px);
  box-shadow: var(--shadow-accent);
}

.action-btn-primary:active {
  transform: translateY(0);
}

.action-btn-outline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 18px;
  height: 34px;
  background: transparent;
  color: var(--color-text);
  border: 1.5px solid var(--color-border);
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  font-family: var(--font-body);
}

.action-btn-outline:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

.action-btn-ghost {
  padding: 0 16px;
  height: 34px;
  background: transparent;
  color: var(--color-text);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  font-family: var(--font-body);
}

.action-btn-ghost:hover {
  border-color: var(--color-text);
  background: var(--color-bg-warm);
}

.action-btn-primary-sm {
  padding: 0 16px;
  height: 34px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 999px;
  font-size: 0.8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  font-family: var(--font-body);
}

.action-btn-primary-sm:hover {
  background: var(--color-accent-light);
  transform: translateY(-1px);
  box-shadow: var(--shadow-accent);
}

.icon-btn {
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--color-text-muted);
  font-size: 16px;
  transition: all var(--duration-fast) var(--ease-out);
  background: transparent;
  border: none;
}

.icon-btn:hover {
  background: var(--color-bg-warm);
  color: var(--color-text);
}

.header-avatar {
  cursor: pointer;
  border: 2px solid transparent;
  transition: all var(--duration-normal) var(--ease-out);
  display: block;
}

.header-avatar:hover {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-glow);
}

/* 私信入口 */
.msg-entry-wrap {
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast);
}

.msg-entry-wrap:hover {
  background: var(--color-bg-warm);
}

.msg-entry-icon {
  font-size: 18px;
  color: var(--color-text-muted);
  transition: color var(--duration-fast);
}

.msg-entry-wrap:hover .msg-entry-icon {
  color: var(--color-text);
}

/* 下拉菜单 */
.user-dropdown-menu {
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-lg) !important;
  border: 1px solid var(--color-border-light) !important;
  min-width: 140px;
  padding: 4px !important;
}

.user-dropdown-menu :deep(.ant-dropdown-menu-item) {
  border-radius: var(--radius-sm) !important;
  padding: 6px 12px !important;
  transition: all var(--duration-fast) !important;
}

.user-dropdown-menu :deep(.ant-dropdown-menu-item:hover) {
  background: var(--color-accent-soft) !important;
}

.logout-item {
  color: var(--color-error) !important;
}

/* ===== 主内容 ===== */
.main-area {
  flex: 1;
  padding-top: 64px;
}

.page-wrap {
  max-width: 1280px;
  margin: 0 auto;
  padding: 36px 28px 100px;
  min-height: calc(100vh - 64px - 72px);
  animation: slideUpFade 0.4s var(--ease-out);
}

/* ===== 页脚 ===== */
.layout-footer {
  border-top: 1px solid var(--color-border-light);
  padding: 32px 28px;
  background: var(--color-bg-card);
}

.footer-inner {
  max-width: 1280px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-logo {
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: -0.01em;
}

.footer-divider-dot {
  color: var(--color-border);
}

.footer-text {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
}

.footer-copyright {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  opacity: 0.7;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .nav-links {
    display: none;
  }

  .topbar-inner {
    gap: 12px;
    padding: 0 16px;
  }

  .page-wrap {
    padding: 24px 16px 80px;
  }

  .action-btn-outline,
  .action-btn-ghost {
    display: none;
  }

  .footer-inner {
    flex-direction: column;
    text-align: center;
    gap: 12px;
  }
}
</style>
