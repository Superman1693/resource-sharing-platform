<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../store/userLogin'
import logoUrl from '../assets/images/logo.png'
import { Layout, message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, MailOutlined, GithubOutlined } from '@ant-design/icons-vue'
import Fireworks from '../components/Fireworks.vue'
import { redirectToGithub, redirectToQQ } from '../utils/oauth'

const store = useUserStore()
const router = useRouter()
const route = useRoute()
const loginFormRef = ref(null)
const showFireworks = ref(false)

/**
 * QQ 登录入口开关。
 *
 * 需求：登录页暂时不显示 QQ 登录按钮，但**相关代码全部保留**——
 * `utils/oauth.js` 的 redirectToQQ、`utils/api.js` 的 qqLogin、
 * `OAuthCallback.vue` 的 QQ 分支、以及后端 `/oauth/qq/**` 接口均未改动。
 * 需要恢复时，把这里改成 true 即可（按钮与样式会立刻回来）。
 */
const SHOW_QQ_LOGIN = false

const bannedMsg = route.query.msg === 'banned' ? '您的账号已被封禁，请联系管理员' : ''

const activeKey = ref('1')
const formState = reactive({
  userAccount: '',
  userPassword: '',
  remember: true,
})

// 邮箱登录表单：与账号登录走同一个登录接口，后端按「是否含 @」识别为邮箱
const emailFormRef = ref(null)
const emailFormState = reactive({
  email: '',
  emailPassword: '',
  remember: true,
})

watch(
  () => route.path,
  (newPath) => {
    if (newPath === '/login') {
      formState.userAccount = ''
      formState.userPassword = ''
      emailFormState.email = ''
      emailFormState.emailPassword = ''
      if (loginFormRef.value) {
        loginFormRef.value.resetFields()
      }
      if (emailFormRef.value) {
        emailFormRef.value.resetFields()
      }
    }
  },
  { immediate: true }
)

/** 登录成功后的统一处理：放烟花 + 按角色跳转 */
const handleLoginSuccess = () => {
  showFireworks.value = true
  const userRole = Number(store.userRole)
  const defaultPath = userRole === 1 ? '/main/contentManage' : '/user/home'
  const redirectPath = route.query.redirect
  const targetPath = redirectPath && redirectPath !== '/login' ? String(redirectPath) : defaultPath

  setTimeout(() => {
    router.replace(targetPath).catch(err => {
      console.error('导航失败:', err)
      router.push(targetPath).catch(e => console.error('重试导航失败:', e))
    })
  }, 100)
}

/** 账号密码登录 */
const sendMsg = async () => {
  if (!loginFormRef.value) {
    message.error('表单加载失败，请刷新页面')
    return
  }

  try {
    await loginFormRef.value.validate()
    const loginSuccess = await store.login(formState.userAccount, formState.userPassword, formState.remember)

    if (loginSuccess) {
      handleLoginSuccess()
    }
  } catch (err) {
    if (err.errorFields) {
      message.warning('请完善登录信息')
    }
  }
}

/** 邮箱登录（后端支持用邮箱 + 密码登录） */
const sendEmailMsg = async () => {
  if (!emailFormRef.value) {
    message.error('表单加载失败，请刷新页面')
    return
  }

  try {
    await emailFormRef.value.validate()
    const loginSuccess = await store.login(
      emailFormState.email.trim(),
      emailFormState.emailPassword,
      emailFormState.remember
    )

    if (loginSuccess) {
      handleLoginSuccess()
    }
  } catch (err) {
    if (err.errorFields) {
      message.warning('请完善登录信息')
    }
  }
}

const goToRegister = () => {
  router.replace('/register')
}

const { Footer } = Layout

const disabled = computed(() => {
  return !(formState.userAccount && formState.userPassword)
})

const emailDisabled = computed(() => {
  return !(emailFormState.email && emailFormState.emailPassword)
})
</script>

<template>
  <div class="login-page">
    <Fireworks v-if="showFireworks" />

    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-line bg-line-1"></div>
      <div class="bg-line bg-line-2"></div>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- 顶部金色装饰线 -->
      <div class="card-accent"></div>

      <!-- Logo 和标题 -->
      <div class="login-header">
        <div class="logo-wrapper">
          <img :src="logoUrl" alt="CodeVerse" class="logo-img" />
        </div>
        <h1 class="login-title">CodeVerse</h1>
        <p class="login-subtitle">探索编程的无限可能</p>
      </div>

      <!-- 封禁提示 -->
      <a-alert
        v-if="bannedMsg"
        :message="bannedMsg"
        type="error"
        show-icon
        class="banned-alert"
      />

      <!-- 登录表单 -->
      <a-tabs v-model:activeKey="activeKey" centered class="login-tabs">
        <a-tab-pane key="1" tab="账号密码登录">
          <a-form
            :model="formState"
            name="login_form"
            class="login-form"
            ref="loginFormRef"
          >
            <a-form-item
              name="userAccount"
              :rules="[{ required: true, message: '请输入账号' }]"
            >
              <a-input
                v-model:value="formState.userAccount"
                size="large"
                placeholder="请输入账号"
                class="login-input"
              >
                <template #prefix>
                  <UserOutlined class="input-icon" />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item
              name="userPassword"
              :rules="[{ required: true, message: '请输入密码' }]"
            >
              <a-input-password
                v-model:value="formState.userPassword"
                size="large"
                placeholder="请输入密码"
                class="login-input"
              >
                <template #prefix>
                  <LockOutlined class="input-icon" />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item class="remember-row">
              <a-checkbox v-model:checked="formState.remember" class="remember-checkbox">
                记住我
              </a-checkbox>
              <a class="forgot-link" @click.prevent="router.push('/resetPassword')">忘记密码</a>
            </a-form-item>

            <a-form-item>
              <a-button
                :disabled="disabled"
                type="primary"
                html-type="submit"
                class="login-btn"
                size="large"
                @click.prevent="sendMsg"
              >
                登录
              </a-button>
            </a-form-item>

            <div class="register-section">
              <span class="register-text">还没有账号？</span>
              <a class="register-link" @click.prevent="goToRegister">立即注册</a>
            </div>

            <div class="social-login">
              <div class="social-divider">
                <span class="divider-line"></span>
                <span class="divider-text">其他登录方式</span>
                <span class="divider-line"></span>
              </div>
              <div class="social-buttons">
                <button class="social-btn github-btn" @click="redirectToGithub" title="GitHub 登录">
                  <GithubOutlined />
                </button>
                <!-- QQ 登录按钮：暂时隐藏（SHOW_QQ_LOGIN = false），代码与样式均保留，改开关即可恢复 -->
                <button
                  v-if="SHOW_QQ_LOGIN"
                  class="social-btn qq-btn"
                  @click="redirectToQQ"
                  title="QQ 登录"
                >
                  <svg class="qq-icon" viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                    <path d="M12.003 2c-2.265 0-6.29 1.364-6.29 7.325v1.195S3.55 14.96 3.55 17.474c0 .665.17 1.025.281 1.025.114 0 .902-.484 1.748-2.072 0 0-.18 2.197 1.904 3.967 0 0-1.77.495-1.77 1.182 0 .686 4.078.43 6.29.43 2.239 0 6.29.256 6.29-.43 0-.687-1.77-1.182-1.77-1.182 2.085-1.77 1.905-3.967 1.905-3.967.845 1.588 1.634 2.072 1.746 2.072.111 0 .283-.36.283-1.025 0-2.514-2.166-6.954-2.166-6.954V9.325C18.29 3.364 14.268 2 12.003 2z"/>
                  </svg>
                </button>
              </div>
            </div>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="2" tab="邮箱登录">
          <a-form
            :model="emailFormState"
            name="email_login_form"
            class="login-form"
            ref="emailFormRef"
          >
            <a-form-item
              name="email"
              :rules="[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '请输入正确的邮箱格式' }
              ]"
            >
              <a-input
                v-model:value="emailFormState.email"
                size="large"
                placeholder="请输入邮箱"
                class="login-input"
                autocomplete="email"
              >
                <template #prefix>
                  <MailOutlined class="input-icon" />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item
              name="emailPassword"
              :rules="[{ required: true, message: '请输入密码' }]"
            >
              <a-input-password
                v-model:value="emailFormState.emailPassword"
                size="large"
                placeholder="请输入密码"
                class="login-input"
                autocomplete="current-password"
              >
                <template #prefix>
                  <LockOutlined class="input-icon" />
                </template>
              </a-input-password>
            </a-form-item>

            <a-form-item class="remember-row">
              <a-checkbox v-model:checked="emailFormState.remember" class="remember-checkbox">
                记住我
              </a-checkbox>
              <a class="forgot-link" @click.prevent="router.push('/resetPassword')">忘记密码</a>
            </a-form-item>

            <a-form-item>
              <a-button
                :disabled="emailDisabled"
                type="primary"
                html-type="submit"
                class="login-btn"
                size="large"
                @click.prevent="sendEmailMsg"
              >
                登录
              </a-button>
            </a-form-item>

            <div class="register-section">
              <span class="register-text">还没有账号？</span>
              <a class="register-link" @click.prevent="goToRegister">立即注册</a>
            </div>
          </a-form>
        </a-tab-pane>
      </a-tabs>
    </div>

    <!-- 底部 -->
    <Footer class="login-footer">
      <div class="footer-links">
        <a href="https://github.com/Superman1693" target="_blank" class="footer-link">
          <GithubOutlined />
          <span>GitHub</span>
        </a>
        <span class="footer-divider">·</span>
        <a href="https://www.yuque.com/superman-a2gis/skgz3p/nye9kvx8zwqsqyd9?singleDoc#" target="_blank" class="footer-link">
          文档
        </a>
      </div>
      <p class="footer-copyright">© 2025 CodeVerse · All Rights Reserved</p>
    </Footer>
  </div>
</template>

<style scoped>
/* 页面容器 */
.login-page {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  position: relative;
  overflow: hidden;
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

/* 微妙的噪点纹理 */
.bg-decoration::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)' opacity='0.03'/%3E%3C/svg%3E");
  background-repeat: repeat;
  opacity: 0.5;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(37, 99, 235, 0.08);
  background: radial-gradient(circle, rgba(37, 99, 235, 0.03), transparent 70%);
}

.bg-circle-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -100px;
  animation: floatSlow 20s ease-in-out infinite;
}

.bg-circle-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: -50px;
  animation: floatSlow 25s ease-in-out infinite reverse;
}

.bg-line {
  position: absolute;
  width: 1px;
  background: linear-gradient(to bottom, transparent, rgba(37, 99, 235, 0.12), transparent);
}

.bg-line-1 {
  height: 100%;
  left: 15%;
  animation: lineShimmer 8s ease-in-out infinite;
}

.bg-line-2 {
  height: 100%;
  right: 20%;
  animation: lineShimmer 10s ease-in-out infinite 2s;
}

@keyframes floatSlow {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -20px); }
}

@keyframes lineShimmer {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 0.8; }
}

/* 登录卡片 */
.login-card {
  width: 90%;
  max-width: 440px;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl);
  padding: 48px 40px 40px;
  position: relative;
  z-index: 10;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.25), 0 0 0 1px rgba(255, 255, 255, 0.08);
  animation: cardEnter 0.8s var(--ease-out) forwards;
  opacity: 0;
  transform: translateY(30px);
}

@keyframes cardEnter {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 顶部蓝色装饰线 */
.card-accent {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 3px;
  background: linear-gradient(90deg, transparent, var(--color-accent), transparent);
  border-radius: 0 0 4px 4px;
}

/* Logo 和标题 */
.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.logo-wrapper {
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-md);
  transition: transform var(--duration-normal) var(--ease-out);
}

.logo-wrapper:hover {
  transform: scale(1.05) rotate(-3deg);
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.login-title {
  font-family: var(--font-display);
  font-size: 1.875rem;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.login-subtitle {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
}

/* 封禁提示 */
.banned-alert {
  margin-bottom: 20px;
  border-radius: var(--radius-md);
}

/* 标签页 */
.login-tabs {
  margin-top: 8px;
}

.login-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 28px;
}

.login-tabs :deep(.ant-tabs-tab) {
  font-size: 0.9375rem;
  font-weight: 500;
  padding: 8px 0;
}

.login-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: var(--color-accent) !important;
  font-weight: 600;
}

.login-tabs :deep(.ant-tabs-ink-bar) {
  background: var(--color-accent) !important;
  height: 2px !important;
}

/* 表单 */
.login-form {
  width: 100%;
}

.login-form :deep(.ant-form-item) {
  margin-bottom: 20px;
}

/* 输入框 */
.login-input {
  height: 48px;
  border-radius: var(--radius-md) !important;
  font-size: 0.9375rem;
}

.login-input :deep(.ant-input) {
  font-size: 0.9375rem;
}

.input-icon {
  color: var(--color-text-muted);
  font-size: 16px;
}

/* 记住我行 */
.remember-row :deep(.ant-form-item-control-input-content) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.remember-checkbox {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

.forgot-link {
  font-size: 0.875rem;
  color: var(--color-accent);
  transition: color var(--duration-fast);
}

.forgot-link:hover {
  color: var(--color-accent-light);
}

/* 登录按钮 */
.login-btn {
  width: 100%;
  height: 48px;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: var(--radius-md) !important;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-light)) !important;
  border: none !important;
  box-shadow: var(--shadow-accent) !important;
  transition: all var(--duration-normal) var(--ease-out) !important;
}

.login-btn:not([disabled]):hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(37, 99, 235, 0.4) !important;
}

.login-btn:not([disabled]):active {
  transform: translateY(0);
}

/* 注册区域 */
.register-section {
  text-align: center;
  padding-top: 8px;
}

.register-text {
  font-size: 0.875rem;
  color: var(--color-text-muted);
}

.register-link {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-accent);
  cursor: pointer;
  margin-left: 4px;
  transition: color var(--duration-fast);
}

.register-link:hover {
  color: var(--color-accent-light);
}

/* 底部 */
.login-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 20px;
  text-align: center;
  background: transparent;
  z-index: 10;
}

.footer-links {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.footer-link {
  color: rgba(255, 255, 255, 0.4);
  font-size: 0.8125rem;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: color var(--duration-fast);
}

.footer-link:hover {
  color: var(--color-accent);
}

.footer-divider {
  color: rgba(255, 255, 255, 0.15);
}

.footer-copyright {
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.25);
  margin: 0;
}

/* 社交登录 */
.social-login {
  margin-top: 16px;
}

.social-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.divider-line {
  flex: 1;
  height: 1px;
  background: var(--color-border);
}

.divider-text {
  font-size: 12px;
  color: var(--color-text-muted);
  white-space: nowrap;
}

.social-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.social-btn {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1.5px solid var(--color-border);
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.social-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px var(--color-accent-glow);
}

.github-btn:hover {
  border-color: #333;
  color: #333;
}

/* QQ 按钮样式：按钮当前被 SHOW_QQ_LOGIN 隐藏，样式一并保留 */
.qq-btn:hover {
  border-color: #12b7f5;
  color: #12b7f5;
}

.qq-icon {
  width: 20px;
  height: 20px;
}

[data-theme="dark"] .github-btn:hover {
  border-color: #fff;
  color: #fff;
}

[data-theme="dark"] .qq-btn:hover {
  border-color: #12b7f5;
  color: #12b7f5;
}

/* 响应式 */
@media (max-width: 480px) {
  .login-card {
    padding: 36px 24px 32px;
    border-radius: var(--radius-lg);
  }

  .login-title {
    font-size: 1.5rem;
  }
}
</style>
