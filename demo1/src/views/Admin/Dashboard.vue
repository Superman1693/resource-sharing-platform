<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStatsOverview } from '../../utils/api'
import { message } from 'ant-design-vue'
import {
  UserOutlined,
  FileTextOutlined,
  MessageOutlined,
  FolderOutlined,
  RiseOutlined,
  AlertOutlined,
} from '@ant-design/icons-vue'

const router = useRouter()
const loading = ref(false)
const overview = ref({
  totalUsers: 0,
  totalNotes: 0,
  totalComments: 0,
  totalResources: 0,
  todayNewNotes: 0,
  pendingReports: 0,
})

// 总览卡片配置
const cards = [
  { key: 'totalUsers', label: '总用户数', icon: UserOutlined, color: '#1890ff' },
  { key: 'totalNotes', label: '已发布笔记', icon: FileTextOutlined, color: '#52c41a' },
  { key: 'totalComments', label: '总评论数', icon: MessageOutlined, color: '#faad14' },
  { key: 'totalResources', label: '总资源数', icon: FolderOutlined, color: '#722ed1' },
  { key: 'todayNewNotes', label: '今日新增笔记', icon: RiseOutlined, color: '#13c2c2' },
  { key: 'pendingReports', label: '待审核举报', icon: AlertOutlined, color: '#f5222d' },
]

// 快捷入口
const shortcuts = [
  { label: '举报管理', desc: '处理用户举报的内容', path: '/main/reportManage' },
  { label: '敏感词管理', desc: '维护内容过滤词库', path: '/main/sensitiveWord' },
  { label: '内容管理', desc: '审核 / 管理笔记内容', path: '/main/contentManage' },
  { label: '评论管理', desc: '审核评论内容', path: '/main/commentManage' },
]

const fetchOverview = async () => {
  loading.value = true
  try {
    const res = await getStatsOverview()
    if (res.code === 0 && res.data) {
      overview.value = { ...overview.value, ...res.data }
    }
  } catch (err) {
    message.error('加载平台数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchOverview()
})
</script>

<template>
  <div class="dashboard-page">
    <a-spin :spinning="loading">
      <div class="page-title">
        <h2>平台数据总览</h2>
        <span class="page-sub">实时统计平台核心运营指标</span>
      </div>

      <div class="stat-grid">
        <div
          v-for="c in cards"
          :key="c.key"
          class="stat-card"
          :style="{ borderTopColor: c.color }"
        >
          <div class="stat-icon" :style="{ background: c.color + '1a', color: c.color }">
            <component :is="c.icon" />
          </div>
          <div class="stat-body">
            <div class="stat-num">{{ overview[c.key] ?? 0 }}</div>
            <div class="stat-label">{{ c.label }}</div>
          </div>
        </div>
      </div>

      <div class="section">
        <h3>快捷入口</h3>
        <div class="shortcut-grid">
          <div
            v-for="s in shortcuts"
            :key="s.path"
            class="shortcut-card"
            @click="router.push(s.path)"
          >
            <div class="sc-label">{{ s.label }}</div>
            <div class="sc-desc">{{ s.desc }}</div>
          </div>
        </div>
      </div>

      <div class="tip-bar" v-if="overview.pendingReports > 0">
        <AlertOutlined style="color: #faad14; margin-right: 6px" />
        待审核举报数为
        <b style="color: #f5222d; margin: 0 4px">{{ overview.pendingReports }}</b>
        条，请及时进入「举报管理」处理。
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.dashboard-page {
  padding: 4px 0;
}
.page-title {
  margin-bottom: 20px;
}
.page-title h2 {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 600;
}
.page-title .page-sub {
  color: #888;
  font-size: 13px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.stat-card {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-top: 3px solid #1890ff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}
.stat-num {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  color: #888;
  font-size: 13px;
  margin-top: 4px;
}
.section {
  margin-top: 32px;
}
.section h3 {
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 600;
}
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
}
.shortcut-card {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 18px;
  cursor: pointer;
  transition: all 0.2s;
}
.shortcut-card:hover {
  border-color: var(--color-accent);
  background: #fff7ed;
}
.sc-label {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 6px;
}
.sc-desc {
  color: #888;
  font-size: 13px;
}
.tip-bar {
  margin-top: 28px;
  padding: 12px 16px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 6px;
  color: #614700;
  font-size: 13px;
}
</style>
