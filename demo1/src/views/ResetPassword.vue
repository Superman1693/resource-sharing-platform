<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import logoUrl from '../assets/images/logo.png'
import { notification } from 'ant-design-vue'
import { UserOutlined, LockOutlined, ArrowLeftOutlined, SafetyOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { resetPassword, getCaptcha } from '../utils/api'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

// 验证码相关
const captchaKey = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)
const userCaptchaInput = ref('')

const formState = reactive({
  userAccount: '',
  newPassword: '',
  checkPassword: '',
})

const rules = {
  userAccount: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 4, max: 20, message: '账号长度必须在 4-20 之间', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能小于 8 位', trigger: 'blur' }
  ],
  checkPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_, value) => {
        if (!value) return Promise.reject('请确认新密码')
        if (value !== formState.newPassword) return Promise.reject('两次密码输入不一致')
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ],
  captchaCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 4, message: '验证码为4位数字', trigger: 'blur' }
  ]
}

// 获取图形验证码
const fetchCaptcha = async () => {
  captchaLoading.value = true
  try {
    const res = await getCaptcha()
    if (res.code === 0) {
      captchaKey.value = res.data.captchaKey
      captchaImage.value = res.data.captchaImage
      userCaptchaInput.value = ''
    }
  } catch (error) {
    notification.error({ message: '获取验证码失败', description: '请稍后重试' })
  } finally {
    captchaLoading.value = false
  }
}

// 页面加载时获取验证码
onMounted(() => {
  fetchCaptcha()
})

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await resetPassword({
      userAccount: formState.userAccount,
      newPassword: formState.newPassword,
      checkPassword: formState.checkPassword,
      captchaCode: userCaptchaInput.value,
      captchaKey: captchaKey.value,
    })
    notification.success({ message: '密码重置成功', description: '请使用新密码登录' })
    router.replace('/login')
  } catch (error) {
    if (!error?.errorFields) {
      const msg = error?.description || error?.message || '重置失败，请检查输入信息'
      notification.error({ message: '密码重置失败', description: msg })
    }
    // 验证码错误时刷新验证码
    fetchCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="reset-page">
    <div class="bg-decoration">
      <div class="bg-circle bg-circle-1"></div>
      <div class="bg-circle bg-circle-2"></div>
    </div>

    <div class="reset-card">
      <div class="card-accent"></div>

      <div class="reset-header">
        <div class="logo-wrapper">
          <img :src="logoUrl" alt="CodeVerse" class="logo-img" />
        </div>
        <h1 class="reset-title">重置密码</h1>
        <p class="reset-subtitle">输入账号和新密码来重置您的密码</p>
      </div>

      <a-form
        :model="formState"
        :rules="rules"
        ref="formRef"
        layout="vertical"
        class="reset-form"
      >
        <a-form-item name="userAccount">
          <a-input
            v-model:value="formState.userAccount"
            size="large"
            placeholder="请输入账号"
            class="reset-input"
          >
            <template #prefix>
              <UserOutlined class="input-icon" />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item name="newPassword">
          <a-input-password
            v-model:value="formState.newPassword"
            size="large"
            placeholder="请输入新密码（至少8位）"
            class="reset-input"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item name="checkPassword">
          <a-input-password
            v-model:value="formState.checkPassword"
            size="large"
            placeholder="请确认新密码"
            class="reset-input"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <!-- 图形验证码输入 -->
        <a-form-item name="captchaCode">
          <div class="captcha-row">
            <a-input
              v-model:value="userCaptchaInput"
              size="large"
              placeholder="请输入验证码"
              class="captcha-input"
              :maxlength="4"
            >
              <template #prefix>
                <SafetyOutlined class="input-icon" />
              </template>
            </a-input>
            <div class="captcha-image-wrapper" @click="fetchCaptcha" :title="'点击刷新验证码'">
              <img
                v-if="captchaImage"
                :src="captchaImage"
                alt="验证码"
                class="captcha-image"
              />
              <div v-else class="captcha-placeholder">
                <ReloadOutlined :spin="captchaLoading" />
              </div>
            </div>
          </div>
          <div class="captcha-tip">点击图片可刷新验证码</div>
        </a-form-item>

        <a-form-item>
          <a-button
            type="primary"
            html-type="submit"
            class="reset-btn"
            size="large"
            :loading="loading"
            @click.prevent="handleSubmit"
          >
            重置密码
          </a-button>
        </a-form-item>

        <div class="back-section">
          <a class="back-link" @click.prevent="router.replace('/login')">
            <ArrowLeftOutlined /> 返回登录
          </a>
        </div>
      </a-form>
    </div>
  </div>
</template>

<style scoped>
.reset-page {
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

.bg-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(37, 99, 235, 0.06);
}

.bg-circle-1 {
  width: 600px;
  height: 600px;
  top: -200px;
  right: -100px;
}

.bg-circle-2 {
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: -50px;
}

.reset-card {
  width: 90%;
  max-width: 440px;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl);
  padding: 48px 40px 40px;
  position: relative;
  z-index: 10;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.25);
}

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

.reset-header {
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
}

.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.reset-title {
  font-family: var(--font-display);
  font-size: 1.875rem;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 8px;
}

.reset-subtitle {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  margin: 0;
}

.reset-form {
  width: 100%;
}

.reset-form :deep(.ant-form-item) {
  margin-bottom: 20px;
}

.reset-input {
  height: 48px;
  border-radius: var(--radius-md) !important;
  font-size: 0.9375rem;
}

.input-icon {
  color: var(--color-text-muted);
  font-size: 16px;
}

.reset-btn {
  width: 100%;
  height: 48px;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: var(--radius-md) !important;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-light)) !important;
  border: none !important;
  box-shadow: var(--shadow-accent) !important;
}

.back-section {
  text-align: center;
  padding-top: 8px;
}

.back-link {
  font-size: 0.875rem;
  color: var(--color-accent);
  cursor: pointer;
  transition: color 0.2s;
}

.back-link:hover {
  color: var(--color-accent-light);
}

.captcha-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-input {
  flex: 1;
  height: 50px;
  border-radius: var(--radius-md) !important;
  font-size: 0.9375rem;
}

.captcha-image-wrapper {
  width: 160px;
  height: 50px;
  cursor: pointer;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--color-border);
  background: var(--color-bg-warm);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}

.captcha-image-wrapper:hover {
  border-color: var(--color-accent);
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha-placeholder {
  font-size: 20px;
  color: var(--color-text-muted);
}

.captcha-tip {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 4px;
  text-align: right;
}
</style>
