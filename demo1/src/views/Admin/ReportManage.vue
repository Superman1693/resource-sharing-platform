<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getReportList, handleReport } from '../../utils/api'
import { message } from 'ant-design-vue'
import { formatDateTime } from '../../utils/dateUtils'

const router = useRouter()
const loading = ref(false)
const activeStatus = ref('pending')
const dataSource = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

// 状态筛选 tab
const statusTabs = [
  { key: 'pending', label: '待处理' },
  { key: 'resolved', label: '已处理' },
  { key: 'ignored', label: '已忽略' },
  { key: '', label: '全部' },
]

// 文案映射
const typeText = { note: '笔记', comment: '评论', user: '用户' }
const statusText = { pending: '待处理', resolved: '已处理', ignored: '已忽略' }
const statusColor = { pending: 'orange', resolved: 'green', ignored: 'default' }

// 处理弹窗
const modalVisible = ref(false)
const current = ref(null)
const handleAction = ref('resolved')
const handleRemark = ref('')
const submitting = ref(false)

// 表格列
const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '类型', dataIndex: 'targetType', width: 90, customRender: ({ text }) => typeText[text] || text },
  { title: '目标ID', dataIndex: 'targetId', width: 90 },
  { title: '举报原因', dataIndex: 'reason', ellipsis: true, customRender: ({ text }) => text || '（未填写）' },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '举报时间', dataIndex: 'createTime', width: 170, customRender: ({ text }) => formatDateTime(text) },
  { title: '操作', key: 'action', width: 180, fixed: 'right' },
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getReportList({
      status: activeStatus.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (res.code === 0 && res.data) {
      dataSource.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (err) {
    message.error('加载举报列表失败')
  } finally {
    loading.value = false
  }
}

const onTabChange = (key) => {
  activeStatus.value = key
  page.value = 1
  fetchList()
}

const onPageChange = (p) => {
  page.value = p
  fetchList()
}

const openHandle = (record, action) => {
  current.value = record
  handleAction.value = action
  handleRemark.value = ''
  modalVisible.value = true
}

const submitHandle = async () => {
  if (!current.value) return
  submitting.value = true
  try {
    const res = await handleReport(current.value.id, {
      status: handleAction.value,
      remark: handleRemark.value,
    })
    if (res.code === 0) {
      message.success(handleAction.value === 'resolved' ? '已标记为已处理' : '已忽略该举报')
      modalVisible.value = false
      fetchList()
    }
  } catch (err) {
    message.error('处理失败')
  } finally {
    submitting.value = false
  }
}

const viewTarget = (record) => {
  if (record.targetType === 'note') {
    router.push(`/user/noteDetail/${record.targetId}`)
  } else {
    message.info('该类型暂不支持跳转查看')
  }
}

onMounted(() => {
  fetchList()
})
</script>

<template>
  <div class="report-page">
    <div class="page-title">
      <h2>举报管理</h2>
      <span class="page-sub">处理用户举报的笔记 / 评论 / 用户</span>
    </div>

    <a-tabs :active-key="activeStatus" @change="onTabChange" class="status-tabs">
      <a-tab-pane v-for="t in statusTabs" :key="t.key" :tab="t.label" />
    </a-tabs>

    <a-table
      :columns="columns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="{
        current: page,
        pageSize,
        total,
        showTotal: (t) => `共 ${t} 条`,
        onChange: onPageChange,
        showSizeChanger: false,
      }"
      row-key="id"
      size="middle"
      :scroll="{ x: 900 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor[record.status] || 'default'">
            {{ statusText[record.status] || record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="viewTarget(record)">查看</a-button>
            <a-button
              v-if="record.status === 'pending'"
              type="link"
              size="small"
              @click="openHandle(record, 'resolved')"
            >通过处理</a-button>
            <a-button
              v-if="record.status === 'pending'"
              type="link"
              danger
              size="small"
              @click="openHandle(record, 'ignored')"
            >忽略</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="modalVisible"
      :title="handleAction === 'resolved' ? '处理举报 · 标记已处理' : '处理举报 · 忽略'"
      :confirm-loading="submitting"
      ok-text="确认"
      cancel-text="取消"
      @ok="submitHandle"
    >
      <a-form layout="vertical">
        <a-form-item label="举报类型">
          {{ typeText[current?.targetType] || current?.targetType }} · 目标ID {{ current?.targetId }}
        </a-form-item>
        <a-form-item label="举报原因">
          {{ current?.reason || '（未填写）' }}
        </a-form-item>
        <a-form-item label="处理备注">
          <a-textarea
            v-model:value="handleRemark"
            :rows="3"
            placeholder="选填，记录处理说明（如：内容违规已删除 / 经核实忽略）"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.report-page {
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
.status-tabs {
  margin-bottom: 8px;
}
</style>
