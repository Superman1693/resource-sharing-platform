<script setup>
import { ref, reactive, onMounted, h } from 'vue'
import { message as antMessage } from 'ant-design-vue'
import { UserOutlined, FireOutlined, BookOutlined, MessageOutlined, LikeOutlined, DownloadOutlined } from '@ant-design/icons-vue'
// ✅ 导入后端接口方法
import { getDataStats, getUserStats } from '../../utils/api'
import UserAvatar from '../../components/UserAvatar.vue'

const loading = ref(false)
// ✅ 筛选表单（保留原有字段，适配后端参数）
const filterForm = reactive({
  dateRange: [], // 时间范围-picker绑定值
  userAccount: '', // 用户账号筛选
  source: '' // 用户来源筛选（后端接口支持，预留）
})

// ✅ 响应式数据：替换模拟数据，初始化为空，承接后端返回值
const summaryStats = ref({
  totalUsers: 0,
  activeUsers: 0,
  totalContent: 0,
  avgContribution: 0,
  totalComments: 0,
  totalDownloads: 0
})
// 用户活跃度表格数据
const activityData = ref([])
// 平台数据概览（总笔记、浏览量等）
const platformStats = ref([])

// ✅ 表格列配置：保留原有结构+样式，仅优化空值兜底，功能不变
const columns = [
  {
    title: '用户',
    dataIndex: 'username',
    key: 'username',
    width: 150,
  },
  {
    title: '发布内容',
    dataIndex: 'publishCount',
    key: 'publishCount',
    width: 100,
    sorter: (a, b) => (a.publishCount || 0) - (b.publishCount || 0),
    customRender: ({ text }) => text || 0
  },
  {
    title: '评论数',
    dataIndex: 'commentCount',
    key: 'commentCount',
    width: 100,
    sorter: (a, b) => (a.commentCount || 0) - (b.commentCount || 0),
    customRender: ({ text }) => text || 0
  },
  {
    title: '获赞数',
    dataIndex: 'likeCount',
    key: 'likeCount',
    width: 100,
    sorter: (a, b) => (a.likeCount || 0) - (b.likeCount || 0),
    customRender: ({ text }) => text || 0
  },
  {
    title: '浏览量',
    dataIndex: 'viewCount',
    key: 'viewCount',
    width: 120,
    sorter: (a, b) => (a.viewCount || 0) - (b.viewCount || 0),
    customRender: ({ text }) => text || 0
  },
  {
    title: '贡献值',
    dataIndex: 'contributionValue',
    key: 'contributionValue',
    width: 120,
    sorter: (a, b) => (a.contributionValue || 0) - (b.contributionValue || 0),
  },
  {
    title: '活跃天数',
    dataIndex: 'activeDays',
    key: 'activeDays',
    width: 120,
    sorter: (a, b) => (a.activeDays || 0) - (b.activeDays || 0),
    customRender: ({ text }) => text || 0
  },
  {
    title: '等级',
    dataIndex: 'level',
    key: 'level',
    width: 80,
    sorter: (a, b) => (a.level || 0) - (b.level || 0),
  },
  {
    title: '最后活跃',
    dataIndex: 'lastActiveTime',
    key: 'lastActiveTime',
    width: 180,
    customRender: ({ text }) => text || '暂无数据'
  },
]

// ✅ 核心方法：参数转换（前端日期范围 → 后端所需yyyy-MM-dd格式）
const formatRequestParams = () => {
  const params = {
    userAccount: filterForm.userAccount.trim() || undefined,
    source: filterForm.source || undefined
  }
  // 处理时间范围：a-range-picker返回 [Moment, Moment] → 转成yyyy-MM-dd字符串
  if (filterForm.dateRange && filterForm.dateRange.length === 2) {
    params.startDate = filterForm.dateRange[0].format('YYYY-MM-DD')
    params.endDate = filterForm.dateRange[1].format('YYYY-MM-DD')
  }
  return params
}

// ✅ 核心方法：查询数据（对接后端接口，替换原有模拟请求）
const handleSearch = async () => {
  loading.value = true
  try {
    // 1. 转换请求参数
    const params = formatRequestParams()
    // 2. 并行请求两个后端接口（用户统计 + 平台数据概览）
    const [userStatsRes, dataStatsRes] = await Promise.all([
      getUserStats(params), // 用户统计（核心）
      getDataStats(params)  // 平台数据概览
    ])
    // 3. 校验接口返回结果
    if (userStatsRes.code !== 0 || dataStatsRes.code !== 0) {
      throw new Error(userStatsRes.message || dataStatsRes.message || '数据查询失败')
    }
    // 4. ✅ 适配【用户统计数据】→ 页面卡片+表格
    formatUserStats(userStatsRes.data)
    // 5. ✅ 适配【平台数据概览】→ 页面统计指标
    formatPlatformStats(dataStatsRes.data)
    antMessage.success('数据查询成功')
  } catch (err) {
    // 异常处理：权限不足、接口报错、网络问题
    antMessage.error(err.message || '数据查询失败，请检查权限或网络')
    console.error('统计数据查询异常：', err)
  } finally {
    loading.value = false
  }
}

// ✅ 数据适配：用户统计数据（后端返回 → 前端页面渲染）
const formatUserStats = (resData) => {
  const { overview, details } = resData || {}
  // 1. 适配顶部统计卡片（总用户、活跃用户等）
  overview.forEach(item => {
    const { title, value } = item
    switch (title) {
      case '累计用户': summaryStats.value.totalUsers = value || 0; break
      case '活跃用户': summaryStats.value.activeUsers = value || 0; break
      case '本月新增': summaryStats.value.totalContent = value || 0; break // 对应总内容数卡片
      case '付费转化率': summaryStats.value.avgContribution = value || 0; break // 对应平均贡献值卡片
    }
  });
   activityData.value = Array.isArray(details) ? details : [];
  // 2. 适配用户活跃度表格数据（后端details → 前端表格）
  if (Array.isArray(details) && details.length) {
    activityData.value = details.map(item => ({
  id: item.id,
  userAccount: item.userAccount,
  username: item.username,
  avatar: item.avatar,
  publishCount: item.publishCount || 0,
  commentCount: item.commentCount || 0,
  likeCount: item.likeCount || 0,
  viewCount: item.viewCount || 0,
  contributionValue: item.contributionValue || 0,
  lastActiveTime: item.lastActiveTime,
  activeDays: item.activeDays || 0,
  level: item.level || 0,
}))
  } else {
    activityData.value = [] // 无数据兜底
  }
}

// ✅ 数据适配：平台数据概览（后端返回 → 前端统计指标）
const formatPlatformStats = (resData) => {
  const { overview } = resData || {}
  if (Array.isArray(overview)) {
    overview.forEach(item => {
      const { title, value } = item
      switch (title) {
        case '总评论数': summaryStats.value.totalComments = value || 0; break
        case '资源下载量': summaryStats.value.totalDownloads = value || 0; break
      }
    })
  }
}

// ✅ 重置筛选条件（保留原有功能，适配新参数）
const handleReset = () => {
  filterForm.dateRange = []
  filterForm.userAccount = ''
  filterForm.source = ''
  handleSearch()
}

// 页面挂载时初始化查询数据
onMounted(() => {
  handleSearch()
})
</script>

<template>
  <div class="user-activity-page">
    <a-card title="用户活跃度统计" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="时间范围">
          <a-range-picker
            v-model:value="filterForm.dateRange"
            format="YYYY-MM-DD"
            style="width: 300px"
          />
        </a-form-item>
        <a-form-item label="用户账号">
          <a-input
            v-model:value="filterForm.userAccount"
            placeholder="请输入用户账号"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch" :loading="loading">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 顶部统计卡片：保留原有样式，绑定后端查询数据 -->
    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :span="6">
        <a-card>
          <a-statistic
            title="总用户数"
            :value="summaryStats.totalUsers"
            :prefix="h(UserOutlined)"
            value-style="color: var(--color-accent)"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic
            title="活跃用户"
            :value="summaryStats.activeUsers"
            :prefix="h(FireOutlined)"
            value-style="color: #ff7d00"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic
            title="总内容数"
            :value="summaryStats.totalContent"
            :prefix="h(BookOutlined)"
            value-style="color: #00b42a"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic
            title="平均贡献值"
            :value="summaryStats.avgContribution"
            :prefix="h(LikeOutlined)"
            value-style="color: #722ed1"
          />
        </a-card>
      </a-col>
    </a-row>

    <!-- 平台数据概览卡片（新增，对接/stats/data接口） -->
    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :span="6">
        <a-card>
          <a-statistic
            title="总评论数"
            :value="summaryStats.totalComments"
            :prefix="h(MessageOutlined)"
            value-style="color: #13c2c2"
          />
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card>
          <a-statistic
            title="资源下载量"
            :value="summaryStats.totalDownloads"
            :prefix="h(DownloadOutlined)"
            value-style="color: #f5222d"
          />
        </a-card>
      </a-col>
    </a-row>

    <!-- 用户活跃度排行表格：保留原有样式+交互，绑定后端查询数据 -->
    <a-card title="用户活跃度排行" bordered style="margin-top: 16px">
      <a-table
        :columns="columns"
        :data-source="activityData"
        :loading="loading"
        :pagination="{ pageSize: 10, showSizeChanger: true, showQuickJumper: true, total: activityData.length }"
        row-key="id"
        bordered
      >
        <template #bodyCell="{ column, text, record }">
          <template v-if="column.key === 'username'">
            <a-space>
              <UserAvatar :src="record.avatar" :size="32" />
              <span>{{ text }}</span>
            </a-space>
          </template>
          <template v-else-if="column.key === 'contributionValue'">
            <a-tag color="gold">
              <FireOutlined style="margin-right: 4px" />
              {{ text || 0 }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'level'">
            <a-tag color="purple">Lv.{{ text || 0 }}</a-tag>
          </template>
        </template>
        <!-- 无数据兜底插槽 -->
        <template #empty>
          <div style="text-align: center; padding: 30px 0">
            <p>暂无用户活跃度数据</p>
          </div>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.user-activity-page {
  padding: 16px;
}

.filter-form {
  row-gap: 12px;
}
/* 表格单元格垂直居中，优化体验 */
:deep(.ant-table-cell) {
  vertical-align: middle !important;
}
/* 统计卡片样式优化 */
:deep(.ant-statistic-title) {
  font-size: 14px;
  color: #666;
}
:deep(.ant-statistic-content-value) {
  font-size: 20px;
  font-weight: 600;
}
</style>