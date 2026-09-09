<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import { UserOutlined, StarOutlined, ArrowLeftOutlined, EyeOutlined, MessageOutlined, LikeOutlined, DeleteOutlined, EditOutlined, PlusOutlined, LockOutlined } from '@ant-design/icons-vue'
import { getStarDetail, getStarMembers, joinStar, exitStar, removeStarMember, updateStarAnnouncement, followUser, unfollowUser, getFollowList } from '../../utils/api'
import UserAvatar from '../../components/UserAvatar.vue'
import { useUserStore } from '../../store/userLogin'
import { formatShortDate } from '../../utils/dateUtils'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const starId = route.params.id
const loading = ref(false)
const memberLoading = ref(false)
const memberList = ref([])
const contentPage = ref(1)
const contentPageSize = ref(10)
const contentTotal = ref(0)
const editingAnnouncement = ref(false)
const announcementInput = ref('')

const starInfo = ref({
  id: starId,
  name: '',
  description: '',
  announcement: '',
  coverImage: '',
  memberCount: 0,
  contentCount: 0,
  isJoined: false,
  contentList: [],
  ownerId: null,
})

const canManageMembers = computed(() => {
  if (!userStore.isLogin) return false
  return Number(userStore.userRole || 0) === 1 || Number(starInfo.value.ownerId || 0) === Number(userStore.id || 0)
})

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getStarDetail(starId, contentPage.value, contentPageSize.value)
    if (res.code === 0 && res.data) {
      starInfo.value = res.data
      contentTotal.value = res.data.contentTotal || 0
    }
  } catch (err) {
    antMessage.error(err?.description || '加载星球详情失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page, pageSize) => {
  contentPage.value = page
  contentPageSize.value = pageSize
  fetchDetail()
}

const canEditAnnouncement = computed(() => {
  if (!userStore.isLogin) return false
  return Number(userStore.userRole || 0) === 1 || Number(starInfo.value.ownerId || 0) === Number(userStore.id || 0)
})

const startEditAnnouncement = () => {
  announcementInput.value = starInfo.value.announcement || starInfo.value.description || ''
  editingAnnouncement.value = true
}

const saveAnnouncement = async () => {
  try {
    await updateStarAnnouncement(starId, announcementInput.value)
    starInfo.value.announcement = announcementInput.value
    editingAnnouncement.value = false
    antMessage.success('公告已更新')
  } catch (err) {
    antMessage.error(err?.description || '更新公告失败')
  }
}

const fetchMembers = async () => {
  memberLoading.value = true
  try {
    const res = await getStarMembers(starId)
    memberList.value = Array.isArray(res.data) ? res.data : []
  } catch (err) {
    memberList.value = []
  } finally {
    memberLoading.value = false
  }
}

const isPaidStar = computed(() => (starInfo.value.price || 0) > 0)

const doJoin = async () => {
  try {
    await joinStar(starId, isPaidStar.value ? { pay: true } : undefined)
    starInfo.value.isJoined = true
    starInfo.value.locked = false
    starInfo.value.memberCount = (starInfo.value.memberCount || 0) + 1
    antMessage.success(isPaidStar.value ? '支付成功，已加入星球！' : '已成功加入星球！')
    await fetchMembers()
    await fetchDetail()
  } catch (err) {
    // 处理已加入的情况（PARAMS_ERROR 错误代码 40000 + 包含"已加入"的描述）
    if (err?.code === 40000 && err?.description?.includes('已加入')) {
      antMessage.success('您已加入该星球，页面将自动刷新')
      starInfo.value.isJoined = true
      await fetchMembers()
      return
    }
    antMessage.error(err?.description || '加入失败，请重试')
    console.error('加入星球错误:', err)
  }
}

const handleJoin = () => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再加入星球')
    router.push('/login')
    return
  }
  // 付费星球：弹出模拟支付确认
  if (isPaidStar.value) {
    Modal.confirm({
      title: `加入付费星球「${starInfo.value.name}」`,
      content: `加入需要支付 ¥${starInfo.value.price}（当前为模拟支付环境，不会产生真实扣款），确认支付并加入？`,
      okText: `确认支付 ¥${starInfo.value.price}`,
      onOk: doJoin,
    })
    return
  }
  doJoin()
}

const handleExit = async () => {
  try {
    await exitStar(starId)
    starInfo.value.isJoined = false
    starInfo.value.memberCount = Math.max(0, (starInfo.value.memberCount || 1) - 1)
    antMessage.success('已退出星球')
    await fetchMembers()
  } catch (err) {
    antMessage.error(err?.description || '退出失败，请重试')
  }
}

const handleRemoveMember = (member) => {
  if (!member?.canRemove) return
  Modal.confirm({
    title: `确认移除成员「${member.username}」？`,
    content: '移除后该成员将退出星球。',
    okType: 'danger',
    onOk: async () => {
      try {
        await removeStarMember(starId, member.userId)
        antMessage.success('成员已移除')
        await fetchMembers()
        await fetchDetail()
      } catch (err) {
        antMessage.error(err?.description || '移除失败，请重试')
      }
    },
  })
}

const viewContent = (content) => {
  router.push(`/user/noteDetail/${content.id}`)
}

const formatTime = formatShortDate

// ===== 成员互相关注 / 私信 =====
const followMap = ref({}) // userId -> 是否已关注

const isSelfMember = (item) => Number(item.userId) === Number(userStore.id)

// 一次性加载我的关注列表，用于标记成员关注状态
const loadFollowState = async () => {
  if (!userStore.isLogin) return
  try {
    const res = await getFollowList({ page: 1, pageSize: 100 })
    const records = res.data?.records || res.data || []
    const map = {}
    records.forEach((u) => {
      const id = u.id || u.userId
      if (id != null) map[id] = true
    })
    followMap.value = map
  } catch (_) {}
}

const handleFollowMember = async (item) => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    if (followMap.value[item.userId]) {
      await unfollowUser(item.userId)
      followMap.value = { ...followMap.value, [item.userId]: false }
      antMessage.success('已取消关注')
    } else {
      await followUser(item.userId)
      followMap.value = { ...followMap.value, [item.userId]: true }
      antMessage.success(`已关注 ${item.username}`)
    }
  } catch (err) {
    // 后端返回"已关注"类错误时，同步状态为已关注
    if (err?.description?.includes('已关注')) {
      followMap.value = { ...followMap.value, [item.userId]: true }
    }
    antMessage.error(err?.description || '操作失败，请重试')
  }
}

const handleDmMember = (item) => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push({ path: '/user/messages', query: { to: item.userId } })
}

onMounted(() => {
  fetchDetail().then(fetchMembers)
  loadFollowState()
})
</script>

<template>
  <div class="star-detail-page">
    <a-button type="link" @click="router.back()" class="back-button">
      <ArrowLeftOutlined /> 返回
    </a-button>

    <a-spin :spinning="loading">
      <a-card :bordered="false" class="star-header-card">
        <div class="star-header">
          <div class="star-cover-wrapper">
            <img v-if="starInfo.coverImage" :src="starInfo.coverImage" alt="星球封面" class="star-cover-img" />
            <div v-else class="star-cover-placeholder">
              <StarOutlined style="font-size: 64px; color: #8ba7c4" />
            </div>
          </div>
          <div class="star-info">
            <h1 class="star-name">
              {{ starInfo.name || '加载中...' }}
              <a-tag v-if="isPaidStar" color="gold" class="price-tag">¥{{ starInfo.price }} 付费星球</a-tag>
            </h1>
            <div class="star-description">{{ starInfo.description }}</div>
            <div class="star-stats">
              <span><UserOutlined /> {{ starInfo.memberCount || 0 }} 成员</span>
              <span><StarOutlined /> {{ starInfo.contentCount || 0 }} 内容</span>
            </div>
            <div class="star-actions">
              <a-button
                v-if="!starInfo.isJoined"
                type="primary"
                size="large"
                @click="handleJoin"
              >{{ isPaidStar ? `支付 ¥${starInfo.price} 加入` : '加入星球' }}</a-button>
              <a-button v-else size="large" @click="handleExit">已加入 · 退出星球</a-button>
              <a-button
                v-if="starInfo.isJoined"
                size="large"
                @click="router.push(`/user/publish?starId=${starInfo.id}`)"
              >
                <PlusOutlined /> 在星球发帖
              </a-button>
              <a-button size="large" @click="router.push(`/user/knowledgeMap/${starInfo.id}`)">
                查看知识地图
              </a-button>
            </div>
          </div>
        </div>
      </a-card>

      <a-card title="星球公告" bordered style="margin-top: 16px">
        <template #extra>
          <a-button v-if="canEditAnnouncement && !editingAnnouncement" type="link" size="small" @click="startEditAnnouncement">
            <EditOutlined /> 编辑
          </a-button>
        </template>
        <div v-if="!editingAnnouncement" class="announcement-box">
          {{ starInfo.announcement || starInfo.description || '暂无公告' }}
        </div>
        <div v-else>
          <a-textarea v-model:value="announcementInput" :rows="4" :max-length="500" show-count />
          <div style="margin-top: 12px; display: flex; gap: 8px">
            <a-button type="primary" size="small" @click="saveAnnouncement">保存</a-button>
            <a-button size="small" @click="editingAnnouncement = false">取消</a-button>
          </div>
        </div>
      </a-card>

      <a-card title="星球内容" bordered style="margin-top: 16px">
        <!-- 付费星球未加入时的内容锁提示 -->
        <div v-if="starInfo.locked" class="star-lock-tip">
          <LockOutlined style="font-size: 20px" />
          <span>本星球为付费星球，<b>加入后</b>才能阅读全部内容，以下仅展示标题</span>
        </div>
        <a-empty
          v-if="!loading && (!starInfo.contentList || starInfo.contentList.length === 0)"
          description="暂无内容"
          style="padding: 40px 0"
        />
        <a-list
          v-else
          :data-source="starInfo.contentList"
          item-layout="vertical"
        >
          <template #footer>
            <div v-if="contentTotal > contentPageSize" style="text-align: center; margin-top: 16px">
              <a-pagination
                v-model:current="contentPage"
                :page-size="contentPageSize"
                :total="contentTotal"
                show-quick-jumper
                @change="handlePageChange"
              />
            </div>
          </template>
          <template #renderItem="{ item }">
            <a-list-item class="content-item" @click="viewContent(item)">
              <a-list-item-meta>
                <template #title>
                  <a>{{ item.title }}</a>
                  <a-tag v-if="item.isTop" color="red" style="margin-left: 8px">置顶</a-tag>
                  <a-tag
                    :color="item.contentType === 'article' ? 'green' : item.contentType === 'question' ? 'orange' : 'purple'"
                    style="margin-left: 8px"
                  >
                    {{ item.contentType === 'article' ? '文章' : item.contentType === 'question' ? '问题' : '笔记' }}
                  </a-tag>
                </template>
                <template #description>
                  <div class="content-meta">
                    <span>{{ item.author }}</span>
                    <span>{{ formatTime(item.publishTime) }}</span>
                    <span><EyeOutlined /> {{ item.viewCount || 0 }}</span>
                    <span><MessageOutlined /> {{ item.commentCount || 0 }}</span>
                    <span><LikeOutlined /> {{ item.likeCount || 0 }}</span>
                  </div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </a-card>

      <a-card title="成员列表" bordered style="margin-top: 16px">
        <a-spin :spinning="memberLoading">
          <a-empty v-if="!memberLoading && memberList.length === 0" description="暂无成员数据" style="padding: 40px 0" />
          <a-list v-else :data-source="memberList">
            <template #renderItem="{ item }">
              <a-list-item class="member-item">
                <a-list-item-meta>
                  <template #avatar>
                    <UserAvatar :src="item.avatarUrl" />
                  </template>
                  <template #title>
                    <span>{{ item.username }}</span>
                    <a-tag v-if="item.isOwner" color="gold" style="margin-left: 8px">创建者</a-tag>
                    <a-tag v-else-if="item.role === 'admin'" color="blue" style="margin-left: 8px">管理员</a-tag>
                    <a-tag v-else color="default" style="margin-left: 8px">成员</a-tag>
                  </template>
                  <template #description>
                    <div class="member-meta">加入时间：{{ formatTime(item.joinTime) }}</div>
                  </template>
                </a-list-item-meta>
                <template #actions>
                  <a v-if="!isSelfMember(item)" @click.prevent="handleDmMember(item)">
                    <MessageOutlined /> 私信
                  </a>
                  <a
                    v-if="!isSelfMember(item)"
                    @click.prevent="handleFollowMember(item)"
                    :style="{ color: followMap[item.userId] ? '#999' : 'var(--color-accent)' }"
                  >
                    {{ followMap[item.userId] ? '已关注' : '+ 关注' }}
                  </a>
                  <a v-if="item.canRemove" @click.prevent="handleRemoveMember(item)" style="color: #ff4d4f">
                    <DeleteOutlined /> 移除
                  </a>
                </template>
              </a-list-item>
            </template>
          </a-list>
        </a-spin>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.star-detail-page {
  padding: 16px;
}
.back-button {
  margin-bottom: 16px;
  padding-left: 0;
}
.star-header-card {
  margin-bottom: 16px;
}
.star-header {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.star-cover-wrapper {
  width: 280px;
  flex-shrink: 0;
}
.star-cover-img {
  width: 100%;
  height: 180px;
  object-fit: cover;
  border-radius: 8px;
}
.star-cover-placeholder {
  width: 100%;
  height: 180px;
  background: #f4f6f9;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.star-info {
  flex: 1;
  min-width: 0;
}
.star-name {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 12px;
}
.star-description {
  color: #666;
  font-size: 14px;
  line-height: 1.7;
  margin-bottom: 16px;
}
.star-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
  color: #666;
  font-size: 14px;
}
.star-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.price-tag {
  margin-left: 12px;
  font-size: 0.8125rem;
  vertical-align: middle;
}
.star-lock-tip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin-bottom: 16px;
  background: rgba(250, 173, 20, 0.08);
  border: 1px dashed #faad14;
  border-radius: 8px;
  color: #ad6800;
  font-size: 0.875rem;
}
.content-item {
  cursor: pointer;
  transition: background 0.15s;
  padding: 12px 8px;
  border-radius: 4px;
}
.content-item:hover {
  background: #f7f8fa;
}
.content-meta {
  display: flex;
  gap: 16px;
  color: #999;
  font-size: 12px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.announcement-box {
  color: #555;
  line-height: 1.8;
  white-space: pre-wrap;
}
.member-item :deep(.ant-list-item-action) {
  margin-left: 16px;
}
.member-meta {
  color: #888;
  font-size: 12px;
}
</style>
