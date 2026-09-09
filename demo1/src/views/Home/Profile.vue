<script setup>
import { getCurrentUser, updateUser, getFollowList, getFollowerList, uploadFile } from '../../utils/api'
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '../../store/userLogin'
import { PlusOutlined, CheckOutlined, CloseOutlined, TrophyOutlined, EditOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const growthTimelinePath = computed(() =>
  route.path.startsWith('/user') ? '/user/growthTimeline' : '/main/growthTimeline'
)

const userInfo = ref({
  id: userStore.id,
  userAccount: userStore.userAccount,
  username: userStore.username || '未设置',
  phone: userStore.phone || '未绑定',
  email: '未绑定',
  gender: '未知',
  bio: '',
  skills: '',
  services: '',
  avatarUrl: userStore.avatarUrl,
  address: '江软'
})

const originalValues = ref({})

const editingFields = ref({
  username: false,
  phone: false,
  email: false,
  gender: false,
  address: false,
  bio: false,
  skills: false,
  services: false
})

const loading = ref(true)
const errorMsg = ref('')
const saveLoading = ref({})

// 关注/粉丝统计
const followCount = ref(0)
const followerCount = ref(0)

onMounted(async () => {
  try {
    const res = await getCurrentUser()
    const data = res.data || res
    userInfo.value = {
      ...userInfo.value,
      id: data.id,
      userAccount: data.userAccount ?? '未设置',
      username: data.username ?? '未设置',
      phone: data.phone ?? '未绑定',
      email: data.email ?? '未绑定',
      gender: data.gender === 1 ? '男' : data.gender === 0 ? '女' : '未知',
      avatarUrl: data.avatarUrl ?? '',
      bio: data.bio ?? '',
      skills: data.skills ?? '',
      services: data.services ?? '',
      address: data.address ?? '未填写'
    }
    originalValues.value = { ...userInfo.value }
  } catch (err) {
    console.error('获取用户信息失败', err)
    if (err?.isBusinessError && err.code === 40100) {
      errorMsg.value = '未登录或登录已过期，请先登录'
      router.push('/login')
    } else {
      errorMsg.value = err?.description || err?.message || '加载用户信息失败，请刷新页面重试'
    }
  } finally {
    loading.value = false
  }

  // 加载关注/粉丝数量
  try {
    const [followRes, followerRes] = await Promise.all([
      getFollowList({ page: 1, pageSize: 1 }),
      getFollowerList({ page: 1, pageSize: 1 })
    ])
    followCount.value = followRes.data?.total || followRes.total || 0
    followerCount.value = followerRes.data?.total || followerRes.total || 0
  } catch (err) {
    console.warn('获取关注/粉丝数量失败', err)
  }
})

const startEditing = (field) => {
  editingFields.value[field] = true
  originalValues.value[field] = userInfo.value[field]
  nextTick(() => {
    const input = document.querySelector(`[data-field="${field}"]`)
    if (input) input.focus()
  })
}

const cancelEditing = (field) => {
  userInfo.value[field] = originalValues.value[field]
  editingFields.value[field] = false
}

const saveField = async (field) => {
  if (userInfo.value[field] === originalValues.value[field]) {
    editingFields.value[field] = false
    return
  }

  try {
    saveLoading.value[field] = true
    const updateData = {
      id: userInfo.value.id,
      [field]: field === 'gender'
        ? userInfo.value[field] === '男' ? 1 : userInfo.value[field] === '女' ? 0 : 2
        : userInfo.value[field]
    }

    await updateUser(updateData)
    message.success('修改成功')
    editingFields.value[field] = false
    originalValues.value[field] = userInfo.value[field]
  } catch (err) {
    console.error(`保存${field}失败`, err)
    message.error('修改失败，请重试')
    userInfo.value[field] = originalValues.value[field]
  } finally {
    saveLoading.value[field] = false
  }
}

// 头像上传（自定义请求：走 axios 携带登录 token，避免原生上传不带 Authorization 报未登录）
const handleAvatarUploadRequest = async ({ file, onSuccess, onError }) => {
  const formData = new FormData()
  formData.append('file', file)
  try {
    const resp = await uploadFile(formData)
    if (resp.code === 0) {
      // 成功回调后 a-upload 将 file.status 置为 done，触发下方 handleAvatarUpload
      onSuccess(resp)
    } else {
      message.error(resp.description || '头像上传失败')
      onError(new Error(resp.description || 'upload failed'))
    }
  } catch (e) {
    message.error('头像上传失败')
    onError(e)
  }
}

const handleAvatarUpload = (info) => {
  if (info.file.status === 'done') {
    const resp = info.file.response
    if (resp && resp.code === 0) {
      const url = typeof resp.data === 'string' ? resp.data : resp.data?.url
      if (url) userInfo.value.avatarUrl = url
      message.success('头像上传成功')
    } else {
      message.error(resp?.description || resp?.message || '头像上传失败')
    }
  } else if (info.file.status === 'error') {
    message.error('头像上传失败')
  }
}
</script>

<template>
  <div class="profile-page">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <span>加载中...</span>
    </div>

    <!-- 错误状态 -->
    <div v-else-if="errorMsg" class="error-state">
      <div class="error-icon">!</div>
      <p>{{ errorMsg }}</p>
    </div>

    <!-- 内容 -->
    <template v-else>
      <!-- 用户卡片头部 -->
      <div class="profile-header">
        <div class="header-bg"></div>
        <div class="header-content">
          <div class="avatar-section">
            <a-upload
              :custom-request="handleAvatarUploadRequest"
              accept="image/*"
              :show-upload-list="false"
              @change="handleAvatarUpload"
            >
              <div class="avatar-wrapper">
                <img
                  v-if="userInfo.avatarUrl"
                  :src="userInfo.avatarUrl"
                  alt="头像"
                  class="avatar-img"
                />
                <div v-else class="avatar-placeholder">
                  <span>{{ userInfo.username?.charAt(0) || '?' }}</span>
                </div>
                <div class="avatar-overlay">
                  <EditOutlined />
                </div>
              </div>
            </a-upload>

            <div class="user-meta">
              <h2 class="user-display-name">{{ userInfo.username }}</h2>
              <span class="user-account">@{{ userInfo.userAccount }}</span>
              <!-- 关注/粉丝统计 -->
              <div class="follow-stats">
                <div class="follow-stat-item" @click="router.push('/user/followList?tab=follow')">
                  <span class="follow-stat-number">{{ followCount }}</span>
                  <span class="follow-stat-label">关注</span>
                </div>
                <div class="follow-stat-item" @click="router.push('/user/followList?tab=follower')">
                  <span class="follow-stat-number">{{ followerCount }}</span>
                  <span class="follow-stat-label">粉丝</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 信息卡片 -->
      <div class="info-card">
        <div class="card-title">
          <span class="title-text">基本信息</span>
          <span class="title-line"></span>
        </div>

        <div class="info-grid">
          <!-- 用户名 -->
          <div class="info-item">
            <span class="info-label">用户名</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.username">
                <a-input
                  data-field="username"
                  v-model:value="userInfo.username"
                  size="small"
                  class="edit-input"
                  @blur="saveField('username')"
                  @keyup.enter="saveField('username')"
                  :max-length="20"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('username')" :disabled="saveLoading.username">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('username')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" @click="startEditing('username')">{{ userInfo.username }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('username')" />
              </template>
            </div>
          </div>

          <!-- 手机号 -->
          <div class="info-item">
            <span class="info-label">手机号</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.phone">
                <a-input
                  data-field="phone"
                  v-model:value="userInfo.phone"
                  size="small"
                  class="edit-input"
                  @blur="saveField('phone')"
                  @keyup.enter="saveField('phone')"
                  :max-length="11"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('phone')" :disabled="saveLoading.phone">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('phone')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" @click="startEditing('phone')">{{ userInfo.phone }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('phone')" />
              </template>
            </div>
          </div>

          <!-- 邮箱 -->
          <div class="info-item">
            <span class="info-label">邮箱</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.email">
                <a-input
                  data-field="email"
                  v-model:value="userInfo.email"
                  size="small"
                  class="edit-input"
                  @blur="saveField('email')"
                  @keyup.enter="saveField('email')"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('email')" :disabled="saveLoading.email">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('email')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" @click="startEditing('email')">{{ userInfo.email }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('email')" />
              </template>
            </div>
          </div>

          <!-- 性别 -->
          <div class="info-item">
            <span class="info-label">性别</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.gender">
                <a-select
                  data-field="gender"
                  v-model:value="userInfo.gender"
                  size="small"
                  style="width: 120px"
                  @change="saveField('gender')"
                >
                  <a-select-option value="男">男</a-select-option>
                  <a-select-option value="女">女</a-select-option>
                  <a-select-option value="未知">未知</a-select-option>
                </a-select>
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('gender')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" @click="startEditing('gender')">{{ userInfo.gender }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('gender')" />
              </template>
            </div>
          </div>

          <!-- 个人简介 -->
          <div class="info-item">
            <span class="info-label">个人简介</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.bio">
                <a-textarea
                  data-field="bio"
                  v-model:value="userInfo.bio"
                  size="small"
                  class="edit-input"
                  :rows="2"
                  :maxlength="200"
                  @blur="saveField('bio')"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('bio')" :disabled="saveLoading.bio">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('bio')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" :class="{ 'info-placeholder': !userInfo.bio }" @click="startEditing('bio')">{{ userInfo.bio || '写一句介绍自己' }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('bio')" />
              </template>
            </div>
          </div>

          <!-- 擅长技术栈 -->
          <div class="info-item">
            <span class="info-label">擅长技术</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.skills">
                <a-input
                  data-field="skills"
                  v-model:value="userInfo.skills"
                  size="small"
                  class="edit-input"
                  :maxlength="200"
                  placeholder="多个技术用逗号分隔，如：Vue, Java, MySQL"
                  @blur="saveField('skills')"
                  @keyup.enter="saveField('skills')"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('skills')" :disabled="saveLoading.skills">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('skills')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" :class="{ 'info-placeholder': !userInfo.skills }" @click="startEditing('skills')">{{ userInfo.skills || '添加你擅长的技术栈' }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('skills')" />
              </template>
            </div>
          </div>

          <!-- 可提供的服务 -->
          <div class="info-item">
            <span class="info-label">可提供服务</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.services">
                <a-textarea
                  data-field="services"
                  v-model:value="userInfo.services"
                  size="small"
                  class="edit-input"
                  :rows="2"
                  :maxlength="200"
                  placeholder="如：代码答疑、项目辅导、简历优化"
                  @blur="saveField('services')"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('services')" :disabled="saveLoading.services">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('services')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" :class="{ 'info-placeholder': !userInfo.services }" @click="startEditing('services')">{{ userInfo.services || '描述你可以提供的服务' }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('services')" />
              </template>
            </div>
          </div>

          <!-- 居住地址 -->
          <div class="info-item info-item-full">
            <span class="info-label">居住地址</span>
            <div class="info-value-wrapper">
              <template v-if="editingFields.address">
                <a-textarea
                  data-field="address"
                  v-model:value="userInfo.address"
                  size="small"
                  :rows="2"
                  class="edit-input"
                  @blur="saveField('address')"
                  @keyup.enter="saveField('address')"
                />
                <div class="edit-actions">
                  <button class="edit-btn edit-btn-save" @click="saveField('address')" :disabled="saveLoading.address">
                    <CheckOutlined />
                  </button>
                  <button class="edit-btn edit-btn-cancel" @click="cancelEditing('address')">
                    <CloseOutlined />
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="info-value" @click="startEditing('address')">{{ userInfo.address }}</span>
                <EditOutlined class="edit-trigger" @click="startEditing('address')" />
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作区 -->
      <div class="action-section">
        <a-button type="primary" size="large" class="growth-btn" @click="router.push(growthTimelinePath)">
          <TrophyOutlined />
          <span>查看成长轨迹</span>
        </a-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;
}

/* 加载和错误状态 */
.loading-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: var(--color-text-muted);
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
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
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  font-weight: 700;
  margin-bottom: 16px;
}

/* 用户卡片头部 */
.profile-header {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  margin-bottom: 24px;
  background: #ffffff;
  border: 1px solid var(--color-border-light);
}

.header-bg {
  height: 120px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
  position: relative;
}

.header-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, transparent 60%, rgba(37, 99, 235, 0.15));
}

.header-content {
  padding: 0 32px 28px;
}

.avatar-section {
  display: flex;
  align-items: flex-end;
  gap: 20px;
  margin-top: -40px;
}

.avatar-wrapper {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  border: 4px solid #ffffff;
  box-shadow: var(--shadow-md);
  overflow: hidden;
  position: relative;
  cursor: pointer;
  flex-shrink: 0;
  background: var(--color-bg-warm);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-light));
  color: #ffffff;
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(26, 31, 54, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 1.25rem;
  opacity: 0;
  transition: opacity var(--duration-fast);
}

.avatar-wrapper:hover .avatar-overlay {
  opacity: 1;
}

.user-meta {
  padding-bottom: 8px;
}

.user-display-name {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-primary);
  margin: 0 0 4px;
}

.user-account {
  font-size: 0.875rem;
  color: var(--color-text-muted);
}

/* 关注/粉丝统计 */
.follow-stats {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: 12px;
}

.follow-stat-item {
  display: flex;
  align-items: baseline;
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-sm, 6px);
  transition: all 0.25s var(--ease-out, ease);
}

.follow-stat-item:hover {
  background: rgba(99, 102, 241, 0.08);
}

.follow-stat-number {
  font-family: var(--font-display, sans-serif);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--color-primary, #1e293b);
  transition: color 0.2s;
}

.follow-stat-item:hover .follow-stat-number {
  color: var(--color-accent, #6366f1);
}

.follow-stat-label {
  font-size: 0.8125rem;
  color: var(--color-text-muted, #94a3b8);
}

/* 信息卡片 */
.info-card {
  background: #ffffff;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border-light);
  padding: 28px 32px;
  margin-bottom: 24px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.title-text {
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--color-primary);
}

.title-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--color-border), transparent);
}

/* 信息网格 */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px 32px;
}

.info-item-full {
  grid-column: 1 / -1;
}

.info-label {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 6px;
}

.info-value-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
}

.info-value {
  font-size: 0.9375rem;
  color: var(--color-text);
  cursor: pointer;
  padding: 4px 0;
  border-bottom: 1px dashed transparent;
  transition: border-color var(--duration-fast);
}
.info-value.info-placeholder {
  color: var(--color-text-muted, #b0b0b0);
}

.info-value:hover {
  border-bottom-color: var(--color-accent);
}

.edit-trigger {
  color: var(--color-text-muted);
  font-size: 0.8125rem;
  cursor: pointer;
  opacity: 0;
  transition: all var(--duration-fast);
}

.info-value-wrapper:hover .edit-trigger {
  opacity: 1;
}

.edit-trigger:hover {
  color: var(--color-accent);
}

/* 编辑输入框 */
.edit-input {
  flex: 1;
}

.edit-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.edit-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  transition: all var(--duration-fast);
}

.edit-btn-save {
  background: var(--color-accent-glow);
  color: var(--color-accent);
}

.edit-btn-save:hover {
  background: var(--color-accent);
  color: #ffffff;
}

.edit-btn-cancel {
  background: var(--color-bg-warm);
  color: var(--color-text-muted);
}

.edit-btn-cancel:hover {
  background: #fef2f2;
  color: #ef4444;
}

/* 操作区 */
.action-section {
  text-align: center;
  padding: 8px 0;
}

.growth-btn {
  height: 48px;
  padding: 0 32px;
  font-size: 0.9375rem;
  font-weight: 600;
  border-radius: var(--radius-md) !important;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

/* 响应式 */
@media (max-width: 640px) {
  .info-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .header-content {
    padding: 0 20px 20px;
  }

  .info-card {
    padding: 20px;
  }

  .avatar-section {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .user-meta {
    padding-bottom: 0;
  }
}
</style>
