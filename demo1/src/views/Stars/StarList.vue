<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import { StarOutlined, UserOutlined, FireOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { getStarList, getHotStars, getMyStars, joinStar, createStar, uploadImage } from '../../utils/api'
import { useUserStore } from '../../store/userLogin'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)

const filterForm = reactive({
  keyword: '',
})

const activeTab = ref('all')

const starList = ref([])

// ===== 创建星球 =====
const showCreateModal = ref(false)
const creating = ref(false)
const uploadingCover = ref(false)
const createForm = reactive({
  name: '',
  description: '',
  coverImage: '',
  isPaid: false,
  price: 99,
})

const openCreateModal = () => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再创建星球')
    router.push({ path: '/login', query: { redirect: '/user/starList' } })
    return
  }
  createForm.name = ''
  createForm.description = ''
  createForm.coverImage = ''
  createForm.isPaid = false
  createForm.price = 99
  showCreateModal.value = true
}

// 封面上传（自定义请求，走通用图片上传接口）
const handleCoverUpload = async ({ file, onSuccess, onError }) => {
  uploadingCover.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadImage(formData)
    createForm.coverImage = res.data
    onSuccess(res)
  } catch (err) {
    onError(err)
    antMessage.error(err?.description || '封面上传失败')
  } finally {
    uploadingCover.value = false
  }
}

const beforeCoverUpload = (file) => {
  const isImage = /^image\/(jpeg|png|gif|bmp|webp)$/.test(file.type)
  if (!isImage) {
    antMessage.error('仅支持 jpg/png/gif/bmp/webp 图片')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    antMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}

const handleCreateStar = async () => {
  if (!createForm.name.trim()) {
    antMessage.warning('请填写星球名称')
    return
  }
  if (createForm.name.trim().length > 30) {
    antMessage.warning('星球名称不能超过30个字符')
    return
  }
  if (createForm.isPaid && (!createForm.price || createForm.price < 1)) {
    antMessage.warning('付费星球请设置 1 元以上的价格')
    return
  }
  creating.value = true
  try {
    const res = await createStar({
      name: createForm.name.trim(),
      description: createForm.description.trim(),
      coverImage: createForm.coverImage || null,
      price: createForm.isPaid ? Math.max(1, Math.floor(createForm.price || 0)) : 0,
    })
    antMessage.success(`星球《${res.data?.name || createForm.name}》创建成功`)
    showCreateModal.value = false
    await fetchStarList()
    // 创建成功直接进入星球详情
    if (res.data?.id) {
      router.push(`/user/starDetail/${res.data.id}`)
    }
  } catch (err) {
    antMessage.error(err?.description || '创建失败，请重试')
  } finally {
    creating.value = false
  }
}

const filteredList = computed(() => {
  if (!filterForm.keyword) return starList.value
  const kw = filterForm.keyword.toLowerCase()
  return starList.value.filter(
    item => item.name?.toLowerCase().includes(kw) || item.description?.toLowerCase().includes(kw)
  )
})

const fetchStarList = async () => {
  loading.value = true
  try {
    let res
    if (activeTab.value === 'hot') {
      res = await getHotStars({ limit: 20 })
    } else if (activeTab.value === 'mine') {
      if (!userStore.isLogin) {
        antMessage.info('请先登录后查看我的星球')
        starList.value = []
        loading.value = false
        return
      }
      res = await getMyStars()
      // 调试：打印后端返回数据
      console.log('我的星球响应:', res)
    } else {
      res = await getStarList()
    }
    
    // 处理响应数据，兼容不同的返回格式
    let data = res.data || []
    if (!Array.isArray(data)) {
      // 如果 data 是对象且包含 records 字段（分页格式）
      if (data.records && Array.isArray(data.records)) {
        data = data.records
      } else {
        data = []
      }
    }
    starList.value = data
    
    if (activeTab.value === 'mine' && starList.value.length === 0) {
      antMessage.info('您还没有加入任何星球')
    }
  } catch (err) {
    console.error('加载星球列表出错:', err)
    if (activeTab.value === 'mine' && err?.code === 40100) {
      antMessage.info('请先登录后查看我的星球')
      starList.value = []
      return
    }
    antMessage.error(err?.description || '加载星球列表失败')
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filterForm.keyword = ''
}

const handleTabChange = (key) => {
  activeTab.value = key
  fetchStarList()
}

const viewDetail = (star) => {
  router.push(`/user/starDetail/${star.id}`)
}

const handleJoin = (star) => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再加入星球')
    router.push('/login')
    return
  }
  if (star.isJoined) {
    antMessage.info('您已加入该星球，可直接进入查看')
    return
  }
  const doJoin = async () => {
    try {
      await joinStar(star.id, (star.price || 0) > 0 ? { pay: true } : undefined)
      star.isJoined = true
      star.memberCount = (star.memberCount || 0) + 1
      antMessage.success(`已加入星球：${star.name}`)
    } catch (err) {
      // 处理已加入的情况（PARAMS_ERROR 错误代码 40000 + 包含"已加入"的描述）
      if (err?.code === 40000 && err?.description?.includes('已加入')) {
        antMessage.success('您已加入该星球，可直接进入查看')
        star.isJoined = true
        return
      }
      antMessage.error(err?.description || '加入失败，请重试')
      console.error('加入星球错误:', err)
    }
  }
  // 付费星球：弹出模拟支付确认
  if ((star.price || 0) > 0) {
    Modal.confirm({
      title: `加入付费星球「${star.name}」`,
      content: `加入需要支付 ¥${star.price}（当前为模拟支付环境，不会产生真实扣款），确认支付并加入？`,
      okText: `确认支付 ¥${star.price}`,
      onOk: doJoin,
    })
    return
  }
  doJoin()
}

onMounted(() => {
  fetchStarList()
})
</script>

<template>
  <div class="star-list-page">
    <a-card bordered style="margin-bottom: 16px">
      <div class="top-bar">
        <a-segmented
          v-model:value="activeTab"
          :options="[
            { label: '全部星球', value: 'all' },
            { label: '热门星球', value: 'hot' },
            { label: '我的星球', value: 'mine' },
          ]"
          @change="handleTabChange"
        />
        <a-button type="primary" @click="openCreateModal">
          <PlusOutlined /> 创建星球
        </a-button>
      </div>
    </a-card>

    <a-card title="星球筛选" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="关键词">
          <a-input
            v-model:value="filterForm.keyword"
            allow-clear
            placeholder="搜索星球名称或描述"
            style="width: 300px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="fetchStarList" :loading="loading">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card title="知识星球" bordered style="margin-top: 16px">
      <a-spin :spinning="loading">
        <a-empty v-if="!loading && filteredList.length === 0" description="暂无星球数据" style="padding: 40px 0" />
        <a-list
          v-else
          :data-source="filteredList"
          :grid="{ gutter: 16, xs: 1, sm: 1, md: 2, lg: 2, xl: 3, xxl: 3 }"
        >
          <template #renderItem="{ item }">
            <a-list-item>
              <a-card hoverable class="star-card">
                <template #cover>
                  <div class="star-cover" @click="viewDetail(item)">
                    <img v-if="item.coverImage" :alt="item.name" :src="item.coverImage" />
                    <div v-else class="star-cover-placeholder">
                      <StarOutlined style="font-size: 48px; color: #8ba7c4" />
                    </div>
                  </div>
                </template>
                <a-card-meta :title="item.name">
                  <template #title>
                    <div class="star-name-line">
                      {{ item.name }}
                      <a-tag v-if="(item.price || 0) > 0" color="gold" class="star-price-tag">¥{{ item.price }}</a-tag>
                    </div>
                  </template>
                  <template #description>
                    <div class="star-meta">
                      <div class="star-description">{{ item.description || '暂无描述' }}</div>
                      <div class="star-stats">
                        <span><UserOutlined /> {{ item.memberCount || 0 }} 成员</span>
                        <span><StarOutlined /> {{ item.contentCount || 0 }} 内容</span>
                      </div>
                      <div class="star-footer">
                        <a-space>
                          <a-button type="link" size="small" @click="viewDetail(item)">查看详情</a-button>
                          <a-button
                            type="primary"
                            size="small"
                            :disabled="item.isJoined"
                            @click.stop="handleJoin(item)"
                          >
                            {{ item.isJoined ? '已加入' : '加入星球' }}
                          </a-button>
                        </a-space>
                      </div>
                    </div>
                  </template>
                </a-card-meta>
              </a-card>
            </a-list-item>
          </template>
        </a-list>
      </a-spin>
    </a-card>
  </div>

  <!-- 创建星球弹窗 -->
  <a-modal
    v-model:open="showCreateModal"
    title="创建星球"
    :confirm-loading="creating"
    ok-text="创建"
    cancel-text="取消"
    @ok="handleCreateStar"
  >
    <a-form layout="vertical" class="create-star-form">
      <a-form-item label="星球名称" required>
        <a-input
          v-model:value="createForm.name"
          placeholder="给星球起个名字（30 字以内）"
          :maxlength="30"
          show-count
        />
      </a-form-item>
      <a-form-item label="星球简介">
        <a-textarea
          v-model:value="createForm.description"
          placeholder="介绍一下这个星球的主题和定位（选填，200 字以内）"
          :rows="3"
          :maxlength="200"
          show-count
        />
      </a-form-item>
      <a-form-item label="星球类型">
        <a-radio-group v-model:value="createForm.isPaid">
          <a-radio :value="false">免费星球</a-radio>
          <a-radio :value="true">付费星球</a-radio>
        </a-radio-group>
      </a-form-item>
      <a-form-item v-if="createForm.isPaid" label="星球价格">
        <div class="price-input-row">
          <a-input-number
            v-model:value="createForm.price"
            :min="1"
            :max="9999"
            :precision="0"
            style="width: 160px"
            addon-before="¥"
          />
          <span class="price-hint">用户需支付该价格后才能加入，并阅读星球内全部内容</span>
        </div>
      </a-form-item>
      <a-form-item label="星球封面">
        <div class="cover-upload">
          <div v-if="createForm.coverImage" class="cover-preview">
            <img :src="createForm.coverImage" alt="封面预览" />
            <a-button size="small" danger @click="createForm.coverImage = ''">移除</a-button>
          </div>
          <a-upload
            v-else
            accept="image/*"
            :show-upload-list="false"
            :custom-request="handleCoverUpload"
            :before-upload="beforeCoverUpload"
          >
            <a-button :loading="uploadingCover">
              <PlusOutlined /> {{ uploadingCover ? '上传中...' : '上传封面图片' }}
            </a-button>
          </a-upload>
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
.star-list-page {
  padding: 16px;
}
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.star-name-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.star-price-tag {
  margin-left: 0;
}
.price-input-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.price-hint {
  font-size: 0.75rem;
  color: #999;
}
.cover-upload {
  display: flex;
  align-items: center;
}
.cover-preview {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cover-preview img {
  width: 160px;
  height: 90px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid #e0e0e0;
}
.filter-form {
  row-gap: 12px;
}
.star-card {
  height: 100%;
}
.star-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.star-cover {
  position: relative;
  height: 180px;
  overflow: hidden;
  cursor: pointer;
  background: #f4f6f9;
  display: flex;
  align-items: center;
  justify-content: center;
}
.star-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.star-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
.star-meta {
  padding-top: 8px;
}
.star-description {
  margin: 8px 0 12px;
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  min-height: 44px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.star-stats {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  color: #999;
  font-size: 12px;
}
.star-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
</style>
