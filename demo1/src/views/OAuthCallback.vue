<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/userLogin'
import { message } from 'ant-design-vue'
import { githubLogin, qqLogin } from '../utils/api'
import { validateOAuthState } from '../utils/oauth'

const router = useRouter()
const store = useUserStore()
const loading = ref(true)
const error = ref('')
const providerName = ref('第三方')

onMounted(async () => {
  try {
    const urlParams = new URLSearchParams(window.location.search)
    const code = urlParams.get('code')
    const state = urlParams.get('state')
    const provider = urlParams.get('provider') || (state === 'qq_login' ? 'qq' : 'github')

    if (!code) {
      error.value = '未收到授权码，请重试'
      loading.value = false
      return
    }

    // 验证 state 参数防止 CSRF
    if (state && !validateOAuthState(state, provider)) {
      error.value = '安全验证失败，请重试'
      loading.value = false
      return
    }

    // 根据 provider 调用对应的登录接口
    let res
    if (provider === 'qq') {
      providerName.value = 'QQ'
      res = await qqLogin(code)
    } else {
      providerName.value = 'GitHub'
      res = await githubLogin(code)
    }

    if (res.code === 0 && res.data) {
      const { token, userInfo } = res.data
      // 通过 store action 保存登录状态（确保 persist 插件同步）
      store.setUserInfo({ ...userInfo, token })

      message.success(`${providerName.value} 登录成功！`)

      // 跳转到首页或之前的页面
      const redirect = localStorage.getItem('oauth_redirect') || '/user/home'
      localStorage.removeItem('oauth_redirect')
      router.replace(redirect)
    } else {
      error.value = res.description || '登录失败，请重试'
    }
  } catch (err) {
    console.error('OAuth 回调处理失败:', err)
    error.value = err?.description || err?.message || '登录失败，请重试'
  } finally {
    loading.value = false
  }
})

const goToLogin = () => {
  router.replace('/login')
}
</script>

<template>
  <div class="oauth-callback-page">
    <div class="callback-card">
      <div class="card-accent"></div>

      <!-- 加载中 -->
      <div v-if="loading" class="callback-content">
        <div class="loading-spinner"></div>
        <h2 class="callback-title">正在登录...</h2>
        <p class="callback-desc">正在处理 {{ providerName }} 授权，请稍候</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="error" class="callback-content">
        <div class="error-icon">!</div>
        <h2 class="callback-title">登录失败</h2>
        <p class="callback-desc">{{ error }}</p>
        <button class="retry-btn" @click="goToLogin">返回登录</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.oauth-callback-page {
  width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-primary);
}

.callback-card {
  width: 90%;
  max-width: 420px;
  background: var(--color-bg-card);
  border-radius: var(--radius-xl);
  padding: 48px 40px;
  position: relative;
  box-shadow: 0 32px 80px rgba(0, 0, 0, 0.25);
  text-align: center;
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

.callback-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-icon {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #fef2f2;
  color: #ef4444;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.callback-title {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0;
}

.callback-desc {
  font-size: 0.875rem;
  color: var(--color-text-muted);
  margin: 0;
}

.retry-btn {
  margin-top: 8px;
  padding: 10px 32px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--font-body);
}

.retry-btn:hover {
  background: var(--color-accent-light);
  transform: translateY(-1px);
}
</style>
