<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/userLogin'
import logoUrl from '../assets/images/logo.png'
import { notification } from 'ant-design-vue'
import { GithubOutlined, ArrowLeftOutlined, UserOutlined, LockOutlined, MailOutlined, PhoneOutlined } from '@ant-design/icons-vue'
import { userRegister } from '../utils/api'

const store = useUserStore()
const router = useRouter()
const loginFormRef = ref(null)

const goToLogin = () => {
  router.replace('/login')
}

const layout = {
  labelCol: { span: 24 },
  wrapperCol: { span: 24 },
}

const loginRules = reactive({
  userAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 20, message: '账号长度必须在 4-20 之间', trigger: 'blur' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  userPassword: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能小于 8 位', trigger: 'blur' }
  ],
  checkPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_, value) => {
        // 空值交给上面的 required 规则提示，这里不重复 reject，避免出现两段「请确认密码」
        if (!value) {
          return Promise.resolve()
        }
        if (value !== formState.user.userPassword) {
          return Promise.reject('两次密码输入不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
})

const formState = reactive({
  user: {
    username: '',
    userAccount: '',
    gender: '',
    userPassword: '',
    checkPassword: '',
    phone: '',
    email: '',
    avatarUrl: '',
  },
})

const handleSubmit = async () => {
  if (!loginFormRef.value) return

  try {
    const values = await loginFormRef.value.validate()
    await userRegister(values.user)
    notification.success({ message: '注册成功', description: '请登录' })
    goToLogin()
  } catch (error) {
    console.error('注册异常', error)
    if (!error?.errorFields) {
      const msg = error?.description || error?.message || '请检查输入信息'
      notification.error({ message: '注册失败', description: msg })
    }
  }
}
</script>

<template>
  <div class="register-page">
    <!-- 背景装饰 -->
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
      <div class="bg-line bg-line-1"></div>
      <div class="bg-line bg-line-2"></div>
    </div>

    <!-- 注册卡片 -->
    <div class="register-card">
      <!-- 顶部蓝色装饰线 -->
      <div class="card-accent"></div>

      <!-- 返回登录 -->
      <a-button type="link" @click="goToLogin" class="back-btn">
        <ArrowLeftOutlined /> 返回登录
      </a-button>

      <!-- Logo 和标题 -->
      <div class="register-header">
        <div class="logo-wrapper">
          <img :src="logoUrl" alt="CodeVerse" class="logo-img" />
        </div>
        <h1 class="register-title">CodeVerse</h1>
        <p class="register-subtitle">加入我们，开始你的编程之旅</p>
      </div>

      <!-- 注册表单 -->
      <a-form
        :model="formState"
        :label-col="layout.labelCol"
        :wrapper-col="layout.wrapperCol"
        ref="loginFormRef"
        class="register-form"
      >
        <a-form-item
          :name="['user', 'userAccount']"
          label="账号"
          :rules="loginRules.userAccount"
        >
          <a-input
            v-model:value="formState.user.userAccount"
            size="large"
            placeholder="用于登录的账号（4-20位）"
            class="form-input"
          >
            <template #prefix>
              <UserOutlined class="input-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          :name="['user', 'username']"
          label="用户名"
          :rules="loginRules.username"
        >
          <a-input
            v-model:value="formState.user.username"
            size="large"
            placeholder="你的昵称"
            class="form-input"
          >
            <template #prefix>
              <UserOutlined class="input-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item
          :name="['user', 'userPassword']"
          label="密码"
          :rules="loginRules.userPassword"
        >
          <a-input-password
            v-model:value="formState.user.userPassword"
            size="large"
            placeholder="至少8位密码"
            class="form-input"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item
          :name="['user', 'checkPassword']"
          label="确认密码"
          :rules="loginRules.checkPassword"
        >
          <a-input-password
            v-model:value="formState.user.checkPassword"
            size="large"
            placeholder="再次输入密码"
            class="form-input"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <!-- 可选信息 -->
        <div class="optional-section">
          <span class="optional-label">选填信息</span>
        </div>

        <a-form-item :name="['user', 'gender']" label="性别">
          <a-radio-group v-model:value="formState.user.gender" class="gender-group">
            <a-radio value="1">男</a-radio>
            <a-radio value="2">女</a-radio>
            <a-radio value="3">不愿透露</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item :name="['user', 'email']" label="邮箱" :rules="[{ type: 'email', message: '请输入正确的邮箱' }]">
          <a-input
            v-model:value="formState.user.email"
            size="large"
            placeholder="your@email.com"
            class="form-input"
          >
            <template #prefix>
              <MailOutlined class="input-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item :name="['user', 'phone']" label="电话">
          <a-input
            v-model:value="formState.user.phone"
            size="large"
            placeholder="手机号码"
            class="form-input"
          >
            <template #prefix>
              <PhoneOutlined class="input-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            size="large"
            class="register-btn"
            @click.prevent="handleSubmit"
          >
            注册
          </a-button>
        </a-form-item>

        <div class="login-section">
          <span class="login-text">已有账号？</span>
          <a class="login-link" @click.prevent="goToLogin">立即登录</a>
        </div>
      </a-form>
    </div>

    <!-- 底部 -->
    <footer class="register-footer">
      <div class="footer-links">
        <a href="https://github.com/Superman1693" target="_blank" class="footer-link">
          <GithubOutlined /> GitHub
        </a>
        <span class="footer-divider">·</span>
        <a href="https://www.yuque.com/superman-a2gis/skgz3p/nye9kvx8zwqsqyd9?singleDoc#" target="_blank" class="footer-link">
          文档
        </a>
      </div>
      <p class="footer-copyright">© 2025 CodeVerse · All Rights Reserved</p>
    </footer>
  </div>
</template>

<style scoped>
/* 页面容器 */
.register-page {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
  position: relative;
  overflow: hidden;
  padding: 40px 0;
}

/* 背景装饰 */
.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-decoration::before {
  content: '';
  position: absolute;
  inset: 0;
  /* 移除 feTurbulence SVG 噪点滤镜：高频 fractalNoise 全屏平铺每帧重算，是页面卡顿主因；
     噪点视觉贡献极小（原 opacity 0.5 * 0.03 ≈ 0.015），改用近乎透明的纯色避免 GPU 负载 */
  background: rgba(255, 255, 255, 0.008);
  opacity: 1;
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
  background: linear-gradient(to bottom, transparent, rgba(37, 99, 235, 0.1), transparent);
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

/* 注册卡片 */
.register-card {
  width: 90%;
  max-width: 480px;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl);
  padding: 48px 40px 40px;
  position: relative;
  z-index: 10;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.25);
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

/* 返回按钮 */
.back-btn {
  position: absolute;
  top: 20px;
  left: 20px;
  color: var(--color-text-muted);
  font-size: 0.875rem;
  padding: 0;
}

.back-btn:hover {
  color: var(--color-accent);
}

/* Logo 和标题 */
.register-header {
  text-align: center;
  margin-bottom: 32px;
}

.logo-wrapper {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
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

.register-title {
  font-family: var(--font-display);
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.register-subtitle {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  margin: 0;
  letter-spacing: 0.02em;
}

/* 表单 */
.register-form {
  width: 100%;
}

.register-form :deep(.ant-form-item) {
  margin-bottom: 16px;
}

.register-form :deep(.ant-form-item-label) {
  text-align: left;
  padding-bottom: 4px;
}

.register-form :deep(.ant-form-item-label > label) {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

/* 输入框 */
.form-input {
  height: 44px;
  border-radius: var(--radius-md) !important;
  font-size: 0.9375rem;
}

.form-input :deep(.ant-input) {
  font-size: 0.9375rem;
}

.input-icon {
  color: var(--color-text-muted);
  font-size: 14px;
}

/* 可选信息 */
.optional-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0 16px;
}

.optional-label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.optional-section::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border-light);
}

/* 性别选择 */
.gender-group {
  display: flex;
  gap: 16px;
}

/* 注册按钮 */
.register-btn {
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
  margin-top: 8px;
}

.register-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 32px rgba(37, 99, 235, 0.4) !important;
}

/* 登录区域 */
.login-section {
  text-align: center;
  padding-top: 8px;
}

.login-text {
  font-size: 0.875rem;
  color: var(--color-text-muted);
}

.login-link {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--color-accent);
  cursor: pointer;
  margin-left: 4px;
  transition: color var(--duration-fast);
}

.login-link:hover {
  color: var(--color-accent-light);
}

/* 底部 */
.register-footer {
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

/* 响应式 */
@media (max-width: 480px) {
  .register-card {
    padding: 36px 24px 32px;
    border-radius: var(--radius-lg);
  }

  .register-title {
    font-size: 1.5rem;
  }
}
</style>
