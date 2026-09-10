<script setup lang="ts">
import { ref, computed } from 'vue'
import circleUrl from '../assets/images/1.jpg'
import logoUrl from '../assets/images/logo.png'
import {
  UserOutlined,
  FileTextOutlined,
  FolderOutlined,
  MessageOutlined,
  ApartmentOutlined,
  TeamOutlined,
  FullscreenOutlined,
  AliwangwangOutlined,
  StarOutlined,
  DashboardOutlined,
  AlertOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import AIFloatWindow from '../components/AIFloatWindow.vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/userLogin'
import { useFullscreen } from '../composables/useFullscreen'

const router = useRouter()
const userStore = useUserStore()
const { toggleFullscreen } = useFullscreen()
const currentYear = new Date().getFullYear()
const selectedKeys1 = ref<string[]>(['1'])
const openKeys = ref<string[]>(['content'])

const aiWindowVisible = ref(false)

const toggleAIWindow = () => {
  aiWindowVisible.value = !aiWindowVisible.value
}

const userInfo = computed(() => ({
  id: userStore.id || '',
  userAccount: userStore.userAccount || '',
  username: userStore.username || '未设置',
  avatarUrl: userStore.avatarUrl || '',
}))

const changeUserPassword = () => {
  router.push('/changeUserPassword')
}

const handleLogout = async () => {
  await userStore.logout()
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('userLogin')
  localStorage.removeItem('login')
  localStorage.removeItem('username')
  localStorage.removeItem('isLogin')
  localStorage.removeItem('__DC_STAT_UUID')
  try {
    await router.replace('/user/home')
  } catch (_) {
    window.location.assign('/user/home')
  }
}
</script>

<template>
  <div class="layout-root">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="topbar-inner">
        <!-- Logo -->
        <div class="topbar-logo" @click="router.push('/')">
          <div class="logo-icon">
            <img :src="logoUrl" alt="Logo" />
          </div>
          <div class="logo-text">
            <span class="logo-title">CodeVerse</span>
            <span class="logo-badge">Admin</span>
          </div>
        </div>

        <!-- 中间标题 -->
        <div class="topbar-center">
          <span class="topbar-title">管理控制台</span>
        </div>

        <!-- 右侧功能区 -->
        <div class="topbar-actions">
          <button class="action-btn" @click="toggleAIWindow()" title="AI 助手">
            <AliwangwangOutlined />
          </button>
          <button class="action-btn" @click="toggleFullscreen()" title="全屏">
            <FullscreenOutlined />
          </button>

          <a-dropdown placement="bottomRight" trigger="click">
            <template #overlay>
              <a-menu class="user-dropdown-menu">
                <a-menu-item key="profile" @click="router.push('/main/Profile')">
                  <UserOutlined /> 个人信息
                </a-menu-item>
                <a-menu-item key="password" @click="changeUserPassword">
                  修改密码
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="logout" @click="handleLogout" class="logout-item">
                  退出登录
                </a-menu-item>
              </a-menu>
            </template>
            <div class="user-avatar-trigger">
              <a-avatar
                :size="36"
                :src="userInfo?.avatarUrl?.trim() || circleUrl"
                class="user-avatar"
              />
              <span class="user-name">{{ userInfo.username }}</span>
            </div>
          </a-dropdown>
        </div>
      </div>
    </header>

    <!-- 主体区域 -->
    <div class="layout-body">
      <!-- 侧边栏 -->
      <aside class="sidebar">
        <nav class="sidebar-nav">
          <a-menu
            v-model:openKeys="openKeys"
            mode="inline"
            :selected-keys="[$route.path]"
            @click="(e) => $router.push(e.key)"
            class="nav-menu"
          >
            <a-menu-item key="/main/dashboard">
              <DashboardOutlined class="menu-icon" />
              <span>总览</span>
            </a-menu-item>

            <a-sub-menu key="operation">
              <template #title>
                <AlertOutlined class="menu-icon" />
                <span>运营管理</span>
              </template>
              <a-menu-item key="/main/reportManage">举报管理</a-menu-item>
              <a-menu-item key="/main/sensitiveWord">敏感词</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="content">
              <template #title>
                <FileTextOutlined class="menu-icon" />
                <span>内容管理</span>
              </template>
              <a-menu-item key="/main/contentManage">内容列表</a-menu-item>
              <a-menu-item key="/main/contentPublish">发布内容</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="resources">
              <template #title>
                <FolderOutlined class="menu-icon" />
                <span>资源管理</span>
              </template>
              <a-menu-item key="/main/resourceManage">资源列表</a-menu-item>
              <a-menu-item key="/main/resourceAdd">上传资源</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="comments">
              <template #title>
                <MessageOutlined class="menu-icon" />
                <span>评论管理</span>
              </template>
              <a-menu-item key="/main/commentManage">评论审核</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="knowledge">
              <template #title>
                <ApartmentOutlined class="menu-icon" />
                <span>知识图谱</span>
              </template>
              <a-menu-item key="/main/knowledgeMapEdit">编辑地图</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="stars">
              <template #title>
                <StarOutlined class="menu-icon" />
                <span>星球管理</span>
              </template>
              <a-menu-item key="/main/starManage">星球列表</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="users">
              <template #title>
                <TeamOutlined class="menu-icon" />
                <span>用户管理</span>
              </template>
              <a-menu-item key="/main/Account">用户列表</a-menu-item>
              <a-menu-item key="/main/adminUser">封禁管理</a-menu-item>
            </a-sub-menu>

            <a-sub-menu key="profile">
              <template #title>
                <UserOutlined class="menu-icon" />
                <span>个人中心</span>
              </template>
              <a-menu-item key="/main/Profile">个人信息</a-menu-item>
            </a-sub-menu>
          </a-menu>
        </nav>

        <!-- 侧边栏底部装饰 -->
        <div class="sidebar-footer">
          <div class="sidebar-accent-line"></div>
        </div>
      </aside>

      <!-- 内容区 -->
      <main class="main-area">
        <!-- 面包屑 -->
        <div class="breadcrumb-bar">
          <a-breadcrumb class="breadcrumb">
            <a-breadcrumb-item>首页</a-breadcrumb-item>
            <a-breadcrumb-item>{{ $route.meta?.title || '管理' }}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>

        <!-- 页面内容 -->
        <div class="content-wrapper">
          <router-view></router-view>
        </div>
      </main>
    </div>

    <!-- 页脚 -->
    <footer class="layout-footer">
      <span class="footer-text">CodeVerse</span>
      <span class="footer-dot">·</span>
      <span class="footer-text">© {{ currentYear }}</span>
    </footer>

    <!-- AI 助手浮动窗口 -->
    <AIFloatWindow v-model:visible="aiWindowVisible" />
  </div>
</template>

<style scoped>
/* 根容器 */
.layout-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--color-bg);
}

/* ===== 顶栏 ===== */
.topbar {
  height: 64px;
  background: var(--color-primary);
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 1px 0 rgba(37, 99, 235, 0.15);
}

.topbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 24px;
  max-width: 100%;
}

/* Logo */
.topbar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: opacity var(--duration-fast);
}

.topbar-logo:hover {
  opacity: 0.9;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
}

.logo-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.logo-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-title {
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 600;
  color: #ffffff;
  letter-spacing: -0.01em;
}

.logo-badge {
  font-size: 0.625rem;
  font-weight: 600;
  color: var(--color-primary);
  background: var(--color-accent);
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* 中间 */
.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.topbar-title {
  font-size: 0.875rem;
  color: rgba(255, 255, 255, 0.5);
  font-weight: 500;
  letter-spacing: 0.04em;
}

/* 右侧功能区 */
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: transparent;
  color: rgba(255, 255, 255, 0.6);
  font-size: 16px;
  transition: all var(--duration-fast);
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

/* 用户头像触发器 */
.user-avatar-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--duration-fast);
  margin-left: 8px;
}

.user-avatar-trigger:hover {
  background: rgba(255, 255, 255, 0.08);
}

.user-avatar {
  border: 2px solid rgba(37, 99, 235, 0.3);
  flex-shrink: 0;
}

.user-name {
  font-size: 0.875rem;
  color: rgba(255, 255, 255, 0.85);
  font-weight: 500;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 下拉菜单 */
.user-dropdown-menu {
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-lg) !important;
  border: 1px solid var(--color-border-light) !important;
  min-width: 160px;
}

.user-dropdown-menu :deep(.ant-dropdown-menu-item) {
  font-size: 0.875rem;
  padding: 8px 16px;
}

.logout-item {
  color: #ef4444 !important;
}

/* ===== 主体区域 ===== */
.layout-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 220px;
  background: #ffffff;
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  position: sticky;
  top: 64px;
  height: calc(100vh - 64px);
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-nav {
  flex: 1;
  padding: 16px 12px;
}

.nav-menu {
  border-right: none !important;
  background: transparent !important;
}

.nav-menu :deep(.ant-menu-item) {
  margin: 2px 0;
  padding-left: 16px !important;
  height: 40px;
  line-height: 40px;
  font-size: 0.875rem;
  border-radius: var(--radius-sm);
  color: var(--color-text-secondary);
}

.nav-menu :deep(.ant-menu-item:hover) {
  color: var(--color-accent);
  background: var(--color-accent-glow);
}

.nav-menu :deep(.ant-menu-item-selected) {
  color: var(--color-accent) !important;
  background: var(--color-accent-glow) !important;
  font-weight: 600;
}

.nav-menu :deep(.ant-menu-submenu-title) {
  margin: 4px 0;
  padding-left: 16px !important;
  height: 40px;
  line-height: 40px;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.nav-menu :deep(.ant-menu-submenu-title:hover) {
  color: var(--color-accent);
}

.menu-icon {
  margin-right: 10px;
  font-size: 15px;
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 16px;
}

.sidebar-accent-line {
  height: 2px;
  background: linear-gradient(90deg, var(--color-accent), transparent);
  border-radius: 1px;
  opacity: 0.3;
}

/* ===== 内容区 ===== */
.main-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 32px;
}

/* 面包屑 */
.breadcrumb-bar {
  margin-bottom: 24px;
}

.breadcrumb {
  font-size: 0.8125rem;
}

.breadcrumb :deep(.ant-breadcrumb-link) {
  color: var(--color-text-muted);
}

.breadcrumb :deep(.ant-breadcrumb-separator) {
  color: var(--color-border);
}

/* 内容包装器 */
.content-wrapper {
  flex: 1;
  background: #ffffff;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  box-shadow: var(--shadow-sm);
  padding: 28px;
  animation: contentEnter 0.4s var(--ease-out) forwards;
}

@keyframes contentEnter {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 页脚 ===== */
.layout-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  background: #ffffff;
  border-top: 1px solid var(--color-border-light);
}

.footer-text {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.footer-dot {
  color: var(--color-border);
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .sidebar {
    display: none;
  }

  .main-area {
    padding: 16px;
  }

  .topbar-center {
    display: none;
  }

  .user-name {
    display: none;
  }
}
</style>
