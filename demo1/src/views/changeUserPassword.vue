<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/userLogin'
import { message, notification } from 'ant-design-vue'
import { LockOutlined, ArrowLeftOutlined, SafetyOutlined } from '@ant-design/icons-vue'
import { changePassword } from '../utils/api'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

// 表单数据
const formState = reactive({
  oldPassword: '',
  newPassword: '',
  checkPassword: ''
})

// 表单验证规则
const rules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码长度不能小于8位', trigger: 'blur' },
    {
      validator: (_, value) => {
        if (value && value === formState.oldPassword) {
          return Promise.reject('新密码不能与原密码相同')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ],
  checkPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_, value) => {
        if (value && value !== formState.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    loading.value = true

    const res = await changePassword({
      oldPassword: formState.oldPassword,
      newPassword: formState.newPassword,
      checkPassword: formState.checkPassword
    })

    if (res.code === 0) {
      notification.success({
        message: '密码修改成功',
        description: '请使用新密码重新登录'
      })
      // 修改成功后退出登录，跳转到登录页
      userStore.logout()
      router.push('/login')
    } else {
      message.error(res.message || '密码修改失败')
    }
  } catch (error) {
    console.error('修改密码失败', error)
    if (error.errorFields) {
      // 表单验证失败
      message.warning('请完善表单信息')
    } else {
      message.error(error.message || '密码修改失败，请重试')
    }
  } finally {
    loading.value = false
  }
}

// 重置表单
const handleReset = () => {
  formRef.value?.resetFields()
}
</script>

<template>
  <div class="password-container">
    <div class="password-box">
      <!-- 返回按钮 -->
      <a-button type="link" @click="router.back()" class="back-button">
        <ArrowLeftOutlined /> 返回
      </a-button>

      <!-- 标题区域 -->
      <div class="title-section">
        <div class="title-icon">
          <SafetyOutlined />
        </div>
        <h2 class="page-title">修改密码</h2>
      </div>

      <p class="page-desc">为了您的账户安全，请定期更换密码</p>

      <!-- 密码修改表单 -->
      <a-form
        :model="formState"
        :rules="rules"
        ref="formRef"
        layout="vertical"
        class="password-form"
      >
        <a-form-item label="原密码" name="oldPassword">
          <a-input-password
            v-model:value="formState.oldPassword"
            placeholder="请输入原密码"
            size="large"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item label="新密码" name="newPassword">
          <a-input-password
            v-model:value="formState.newPassword"
            placeholder="请输入新密码（至少8位）"
            size="large"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item label="确认新密码" name="checkPassword">
          <a-input-password
            v-model:value="formState.checkPassword"
            placeholder="请再次输入新密码"
            size="large"
          >
            <template #prefix>
              <LockOutlined class="input-icon" />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item class="form-actions">
          <a-space size="middle">
            <a-button
              type="primary"
              size="large"
              :loading="loading"
              @click="handleSubmit"
              class="submit-btn"
            >
              确认修改
            </a-button>
            <a-button size="large" @click="handleReset" class="reset-btn">
              重置
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>

      <!-- 安全提示 -->
      <div class="security-tips">
        <h4>密码安全建议：</h4>
        <ul>
          <li>密码长度至少8位以上</li>
          <li>包含字母、数字和特殊字符的组合</li>
          <li>避免使用生日、手机号等容易被猜到的信息</li>
          <li>建议定期更换密码（3-6个月）</li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 页面容器：Flex布局，垂直居中 */
.password-container {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-image: url(https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/V-_oS6r-i7wAAAAAAAAAAAAAFl94AQBr);
  background-position: center center;
  background-size: cover;
  background-repeat: no-repeat;
  background-attachment: fixed;
  padding: 20px;
  box-sizing: border-box;
}

/* 内容盒子 */
.password-box {
  width: 100%;
  max-width: 480px;
  padding: 40px;
  background-color: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  position: relative;
}

/* 返回按钮 */
.back-button {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 0;
  color: var(--color-accent);
  font-size: 14px;
}

.back-button:hover {
  color: #40a9ff;
}

/* 标题区域 */
.title-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 8px;
}

.title-icon {
  font-size: 32px;
  color: var(--color-accent);
}

.page-title {
  margin: 0;
  color: #333;
  font-weight: 600;
  font-size: 28px;
}

.page-desc {
  text-align: center;
  margin-bottom: 32px;
  color: #666;
  font-size: 14px;
}

/* 表单样式 */
.password-form {
  width: 100%;
}

.password-form :deep(.ant-form-item-label) {
  font-weight: 500;
  color: #333;
}

.input-icon {
  color: #bfbfbf;
}

.form-actions {
  margin-top: 24px;
  margin-bottom: 0;
}

.form-actions :deep(.ant-form-item-control-input-content) {
  display: flex;
  justify-content: center;
}

.submit-btn {
  min-width: 120px;
}

.reset-btn {
  min-width: 100px;
}

/* 安全提示 */
.security-tips {
  margin-top: 32px;
  padding: 16px 20px;
  background-color: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
}

.security-tips h4 {
  margin: 0 0 12px 0;
  color: #52c41a;
  font-size: 14px;
  font-weight: 600;
}

.security-tips ul {
  margin: 0;
  padding-left: 20px;
  color: #666;
  font-size: 13px;
  line-height: 1.8;
}

.security-tips li {
  margin-bottom: 4px;
}

/* 响应式适配 */
@media (max-width: 576px) {
  .password-box {
    padding: 30px 20px;
  }

  .page-title {
    font-size: 24px;
  }

  .title-icon {
    font-size: 28px;
  }
}
</style>