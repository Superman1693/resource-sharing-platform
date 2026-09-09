<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import { EditOutlined, DeleteOutlined, PushpinOutlined } from '@ant-design/icons-vue'
import { searchContent, deleteNote, topNote, approveNote, rejectNote, syncNotesToEs, syncResourcesToEs } from '../../utils/api'

// ✅ 修复1：定义缺失的常量（适配后端返回的contentType/status）
const CONTENT_TYPE_OPTIONS = [
  { label: '文章', value: 'article' },
  { label: '笔记', value: 'note' },
  { label: '问题', value: 'question' }
]
const CONTENT_STATUS_OPTIONS = [
  { label: '已发布', value: 'published' },
  { label: '草稿', value: 'draft' },
  { label: '待审核', value: 'pending' },
  { label: '已拒绝', value: 'rejected' },
  { label: '已删除', value: 'deleted' }
]
const CONTENT_TYPE_COLOR = {
  article: 'blue',
  note: 'green',
  question: 'orange'
}
const CONTENT_STATUS_COLOR = {
  published: 'success',
  draft: 'warning',
  pending: 'processing',
  rejected: 'error',
  deleted: 'default'
}

const router = useRouter()
const loading = ref(false)
const syncingNotes = ref(false)
const syncingResources = ref(false)

// 筛选表单（响应式正常）
const filterForm = reactive({
  keyword: '',
  contentType: '',
  status: '',
})

// 分页配置（完整可用）
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total) => `共 ${total} 条数据`
})

const contentList = ref([])

// 表格列配置（操作列已补dataIndex，无插槽失效问题）
const columns = [
  { title: '标题', dataIndex: 'title', key: 'title', width: 300 },
  { title: '类型', dataIndex: 'contentType', key: 'contentType', width: 100 },
  { title: '作者', dataIndex: 'author', key: 'author', width: 120 },
  { title: '浏览量', dataIndex: 'viewCount', key: 'viewCount', width: 100, sorter: (a, b) => a.viewCount - b.viewCount },
  { title: '评论数', dataIndex: 'commentCount', key: 'commentCount', width: 100 },
  { title: '点赞数', dataIndex: 'likeCount', key: 'likeCount', width: 100 },
  { title: '置顶', dataIndex: 'isTop', key: 'isTop', width: 80 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '发布时间', dataIndex: 'publishTime', key: 'publishTime', width: 180 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 220, fixed: 'right' }
]

// ✅ 修复2：适配后端返回的纯数组格式，手动处理分页
const fetchContentList = async () => {
  if (loading.value) return
  loading.value = true
  try {
    const params = {
      keyword: filterForm.keyword || '',
      contentType: filterForm.contentType,
      status: filterForm.status,
      current: pagination.current,
      size: pagination.pageSize
    }
    // 过滤空参数，避免传无效值给后端
    const requestParams = Object.fromEntries(
      Object.entries(params).filter(([_, val]) => val !== '' && val !== undefined)
    )
    const res = await searchContent(requestParams)
    const data = res.data || []
    
    // ✅ 核心修复：后端返回纯数组，手动处理分页
    // 1. 先把total设置为数组长度
    pagination.total = Array.isArray(data) ? data.length : 0
    // 2. 手动计算分页数据（如果后端未做分页）
    const start = (pagination.current - 1) * pagination.pageSize
    const end = start + pagination.pageSize
    contentList.value = Array.isArray(data) ? data.slice(start, end) : []
    
  } catch (err) {
    console.error('加载失败：', err)
    const errMsg = err?.isBusinessError 
      ? (err.code === 40100 ? '未登录或登录过期，请重新登录' : err.description || err.message)
      : '加载内容列表失败'
    antMessage.error(errMsg)
    contentList.value = []
  } finally {
    loading.value = false
  }
}

// 置顶/取消置顶
const handleTop = async (record) => {
  const newIsTop = record.isTop === 1 ? 0 : 1
  try {
    await topNote(record.id, newIsTop)
    antMessage.success(newIsTop === 1 ? '置顶成功' : '已取消置顶')
    await fetchContentList()
  } catch (err) {
    antMessage.error(err?.description || err?.message || '操作失败')
  }
}

// 审核通过
const handleApprove = async (record) => {
  try {
    await approveNote(record.id)
    antMessage.success('审核通过')
    await fetchContentList()
  } catch (err) {
    antMessage.error(err?.description || err?.message || '操作失败')
  }
}

// 拒绝
const handleReject = async (record) => {
  try {
    await rejectNote(record.id)
    antMessage.success('已拒绝')
    await fetchContentList()
  } catch (err) {
    antMessage.error(err?.description || err?.message || '操作失败')
  }
}

// 编辑跳转
const handleEdit = (record) => {
  router.push(`/main/contentPublish?id=${record.id}`)
}
const handleDelete = (record) => {
  Modal.confirm({
    title: '确认删除该内容吗？',
    content: '删除后无法恢复',
    onOk: async () => {
      try {
        const res = await deleteNote(record.id)
        if (res.code === 0 || res.data === true) {
          antMessage.success('删除成功')
          await fetchContentList()
        } else {
          antMessage.error('删除失败')
        }
      } catch (err) {
        if (err?.isBusinessError) {
          antMessage.error(err.description || err.message || '删除失败')
        } else {
          antMessage.error('删除失败')
        }
      }
    },
  })
}

// 查询/重置/分页回调
const handleSearch = () => fetchContentList()
const handleReset = () => {
  filterForm.keyword = ''
  filterForm.contentType = ''
  filterForm.status = ''
  pagination.current = 1
  handleSearch()
}
const handleTableChange = (pag) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchContentList()
}

const handleSyncNotesToEs = () => {
  Modal.confirm({
    title: '确认全量同步笔记到 ES 吗？',
    content: '该操作可能耗时，建议在低峰期执行。',
    onOk: async () => {
      syncingNotes.value = true
      try {
        const res = await syncNotesToEs()
        const synced = res?.data?.synced
        antMessage.success(`笔记同步完成${typeof synced === 'number' ? `：${synced} 条` : ''}`)
      } catch (err) {
        antMessage.error(err?.description || err?.message || '笔记同步失败')
      } finally {
        syncingNotes.value = false
      }
    }
  })
}

const handleSyncResourcesToEs = () => {
  Modal.confirm({
    title: '确认全量同步资源到 ES 吗？',
    content: '该操作可能耗时，建议在低峰期执行。',
    onOk: async () => {
      syncingResources.value = true
      try {
        const res = await syncResourcesToEs()
        const synced = res?.data?.synced
        antMessage.success(`资源同步完成${typeof synced === 'number' ? `：${synced} 条` : ''}`)
      } catch (err) {
        antMessage.error(err?.description || err?.message || '资源同步失败')
      } finally {
        syncingResources.value = false
      }
    }
  })
}

onMounted(() => fetchContentList())
</script>

<template>
  <div class="content-manage-page">
    <a-card title="内容管理" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="关键词">
          <a-input
            v-model:value="filterForm.keyword"
            placeholder="搜索标题"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="内容类型">
          <a-select
            v-model:value="filterForm.contentType"
            placeholder="全部类型"
            allow-clear
            style="width: 150px"
          >
            <a-select-option 
              v-for="item in CONTENT_TYPE_OPTIONS" 
              :key="item.value" 
              :value="item.value"
            >
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="状态">
          <a-select
            v-model:value="filterForm.status"
            placeholder="全部状态"
            allow-clear
            style="width: 150px"
          >
            <a-select-option 
              v-for="item in CONTENT_STATUS_OPTIONS" 
              :key="item.value" 
              :value="item.value"
            >
              {{ item.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
      <div class="es-sync-actions">
        <a-space>
          <a-button type="default" :loading="syncingNotes" @click="handleSyncNotesToEs">
            全量同步笔记到 ES
          </a-button>
          <a-button type="default" :loading="syncingResources" @click="handleSyncResourcesToEs">
            全量同步资源到 ES
          </a-button>
        </a-space>
      </div>
    </a-card>

    <a-card bordered style="margin-top: 16px">
      <a-table
        :columns="columns"
        :data-source="contentList"
        :loading="loading"
        :scroll="{ x: 1300 }"
        :pagination="pagination"
        @change="handleTableChange"
        row-key="id"
      >
        <template #bodyCell="{ column, text, record }">
          <template v-if="column.key === 'contentType'">
            <a-tag :color="CONTENT_TYPE_COLOR[text]">
              {{ CONTENT_TYPE_OPTIONS.find(item => item.value === text)?.label || '未知类型' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="CONTENT_STATUS_COLOR[text]">
              {{ CONTENT_STATUS_OPTIONS.find(item => item.value === text)?.label || '未知状态' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'isTop'">
            <a-tag :color="record.isTop === 1 ? 'gold' : 'default'">
              {{ record.isTop === 1 ? '已置顶' : '普通' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space wrap>
              <a-button type="link" size="small" @click="handleEdit(record)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button
                v-if="record.status === 'pending'"
                type="link" size="small" style="color: #52c41a"
                @click="handleApprove(record)"
              >通过</a-button>
              <a-button
                v-if="record.status === 'pending'"
                type="link" danger size="small"
                @click="handleReject(record)"
              >拒绝</a-button>
              <a-button type="link" size="small" @click="handleTop(record)">
                <PushpinOutlined />{{ record.isTop === 1 ? '取消置顶' : '置顶' }}
              </a-button>
              <a-popconfirm title="确认删除？" @confirm="handleDelete(record)">
                <a-button type="link" danger size="small">
                  <DeleteOutlined /> 删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
          <!-- ✅ 优化：标题过长时省略显示 -->
          <template v-else-if="column.key === 'title'">
            <div :title="record.title" style="width: 300px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
              {{ record.title }}
            </div>
          </template>
        </template>
        <!-- ✅ 新增：空列表提示 -->
        <template #empty>
          <div style="text-align: center; padding: 40px; color: #999;">
            暂无匹配的内容数据
          </div>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.content-manage-page {
  padding: 16px;
}
:deep(.filter-form) {
  row-gap: 12px;
}
.es-sync-actions {
  margin-top: 12px;
}
/* ✅ 优化表格单元格垂直对齐 */
:deep(.ant-table-tbody > tr > td) {
  vertical-align: middle;
}
</style>