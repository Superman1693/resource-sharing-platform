<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
// ✅ 修复：保留需要的图标，确保按需导入
import { DownloadOutlined } from '@ant-design/icons-vue'
// ✅ 新增：补充下载接口（需在api文件中定义）
import { getResourceList, deleteResource, toggleResourceStatus, reviewResource } from '../../utils/api'
import { formatLocalDateTime } from '../../utils/dateUtils'

const loading = ref(false)
const router = useRouter()

// 筛选表单响应式对象
const filterForm = reactive({
  keyword: '',
  tag: undefined,
  status: undefined,
})

const tagOptions = [
  { label: '文档', value: 'document' },
  { label: '视频', value: 'video' },
  { label: '代码', value: 'code' },
  { label: '其他', value: 'other' },
]

const statusOptions = [
  { label: '待审核', value: 'pending' },
  { label: '启用', value: 'enabled' },
  { label: '停用', value: 'disabled' },
]

const resourceList = ref([])

// ✅ 修复1：筛选逻辑增加【可选链+空值兜底】，兼容tag=null的情况
const filteredList = computed(() => {
  return resourceList.value.filter((item) => {
    const matchKeyword = filterForm.keyword
      ? item.name?.toLowerCase().includes(filterForm.keyword.toLowerCase())
      : true
    // 兼容后端tag为null的场景
    const matchTag = filterForm.tag ? item.tag === filterForm.tag : true
    const matchStatus = filterForm.status ? item.status === filterForm.status : true
    return matchKeyword && matchTag && matchStatus
  })
})

// ✅ 新增：时间格式化工具函数（适配后端UTC时间，转成本地友好格式）
const formatTime = formatLocalDateTime

const formatFileSize = (sizeBytes) => {
  if (!sizeBytes || sizeBytes <= 0) return '--'
  if (sizeBytes < 1024) return `${sizeBytes} B`
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`
  if (sizeBytes < 1024 * 1024 * 1024) return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`
  return `${(sizeBytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

// 获取资源列表
const fetchResourceList = async () => {
  loading.value = true
  try {
    const params = {
      keyword: filterForm.keyword || undefined,
      tag: filterForm.tag,
      status: filterForm.status,
    }
    const res = await getResourceList(params)
    const data = res.data || []
    resourceList.value = Array.isArray(data) ? data : []
  } catch (err) {
    if (err?.isBusinessError) {
      if (err.code === 40100) {
        antMessage.warning('未登录或登录已过期，请先登录')
      } else if (err.code === 40101) {
        antMessage.warning('无权限')
      } else {
        antMessage.error(err.description || err.message || '加载资源列表失败')
      }
    } else {
      antMessage.error('加载资源列表失败')
    }
  } finally {
    loading.value = false
  }
}

// 重置筛选
const handleReset = () => {
  filterForm.keyword = ''
  filterForm.tag = undefined
  filterForm.status = undefined
  fetchResourceList()
}

// 搜索
const handleSearch = () => {
  fetchResourceList()
}

// ✅ 修复2：优化状态切换的响应式（从数据源修改，避免失效）
const toggleStatus = async (record) => {
  try {
    const newStatus = record.status === 'enabled' ? 'disabled' : 'enabled'
    const res = await toggleResourceStatus(record.id, newStatus)
    if (res.code === 0) {
      // ✅ 规范写法：从resourceList数据源中修改，保证响应式稳定
      const targetItem = resourceList.value.find(item => item.id === record.id)
      if (targetItem) targetItem.status = newStatus
      antMessage.success(`资源「${record.name}」已${newStatus === 'enabled' ? '启用' : '停用'}`)
    } else {
      antMessage.error('操作失败')
    }
  } catch (err) {
    if (err?.isBusinessError) {
      antMessage.error(err.description || err.message || '操作失败')
    } else {
      antMessage.error('操作失败')
    }
  }
}

const handleReview = async (record, status) => {
  try {
    const res = await reviewResource(record.id, status)
    if (res.code === 0) {
      const targetItem = resourceList.value.find(item => item.id === record.id)
      if (targetItem) targetItem.status = status
      antMessage.success(status === 'enabled' ? '审核通过' : '已驳回')
    } else {
      antMessage.error('审核失败')
    }
  } catch (err) {
    antMessage.error(err?.description || err?.message || '审核失败')
  }
}

const handleOpenDetail = (record) => {
  router.push(`/user/resourceDetail/${record.id}`)
}

// 删除资源
const handleDelete = (record) => {
  Modal.confirm({
    title: '确认删除该资源吗？',
    onOk: async () => {
      try {
        const res = await deleteResource(record.id)
        if (res.code === 0 || res.data === true) {
          antMessage.success('删除成功')
          await fetchResourceList()
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

// ✅ 新增：核心下载方法（两种方案可选，适配后端/download/{id}接口）
const handleDownload = async (record) => {
  try {
    antMessage.loading('正在准备下载...', 0)
    // ✅ 方案1：调用后端下载接口（累加次数+返回URL文件，推荐）
    window.open(`/api/resource/download/${record.id}`, '_blank')
    // ✅ 方案2：直接打开OSS地址（备用，如需跳过后端累加可使用）
    // window.open(record.downloadUrl, '_blank')
    antMessage.success('下载请求已发起')
  } catch (err) {
    antMessage.error(err.message || '下载失败')
  } finally {
    antMessage.destroy()
  }
}

// ✅ 修复3：表格列配置全量优化（字段映射+空值兜底+时间格式化+样式优化）
const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '资源名称', dataIndex: 'name', width: 300, ellipsis: true }, // 超长名称省略
  {
    title: '类型',
    dataIndex: 'resourceType',
    width: 120,
    customRender: ({ text }) => {
      const resourceTypeMap = {
        document: '文档',
        video: '视频教程',
        code: '代码示例',
        tool: '工具软件',
        image: '图片资源' // 预留，后续新增类型直接加
      };
      // 有值显示对应中文，无值兜底「未知类型」
      return resourceTypeMap[text] || '未知类型';
    },
  },
  { 
    title: '文件大小', 
    dataIndex: 'fileSize', 
    width: 120,
    customRender: ({ record }) => {
      if (record.fileSize && record.fileSize.trim()) {
        return record.fileSize;
      } else if (record.fileSizeBytes) {
        return formatFileSize(record.fileSizeBytes);
      } else {
        return '暂无数据';
      }
    }
  },
  { 
    title: '下载次数', 
    dataIndex: 'downloadCount', 
    width: 120,
    customRender: ({ text }) => text || 0 //次数为null时显示0
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    customRender: ({ text }) => {
      if (text === 'enabled') return '启用'
      if (text === 'pending') return '待审核'
      return '停用'
    },
  },
  { 
    title: '更新时间', 
    dataIndex: 'updateTime', // 字段名 updateTime
    width: 180,
    customRender: ({ text }) => formatTime(text) // 格式化UTC时间为本地格式
  },
  { title: '操作', key: 'action', fixed: 'right', width: 240 }, // 适配3个按钮
]

onMounted(() => {
  fetchResourceList()
})
</script>

<template>
  <div class="resource-manage-page">
    <a-card title="资源筛选" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="关键词">
          <a-input
            v-model:value="filterForm.keyword"
            allow-clear
            placeholder="搜索资源名称"
            style="width: 240px"
          />
        </a-form-item>
        <a-form-item label="类型">
          <a-select
            v-model:value="filterForm.tag"
            :options="tagOptions"
            placeholder="全部"
            allow-clear
            style="width: 180px"
          />
        </a-form-item>
        <a-form-item label="状态">
          <a-select
            v-model:value="filterForm.status"
            :options="statusOptions"
            placeholder="全部"
            allow-clear
            style="width: 160px"
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

    <a-card title="资源列表" bordered style="margin-top: 16px">
      <a-table 
        :columns="columns" 
        :data-source="filteredList" 
        :loading="loading" 
        row-key="id" 
        :scroll="{ x: 1200 }" 
        bordered
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'enabled' ? 'success' : record.status === 'pending' ? 'gold' : 'default'">
              {{ record.status === 'enabled' ? '启用' : record.status === 'pending' ? '待审核' : '停用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-space size="small">
              <a-button type="link" @click="handleOpenDetail(record)">详情</a-button>
              <a-button v-if="record.status === 'pending'" type="link" @click="handleReview(record, 'enabled')">通过</a-button>
              <a-button v-if="record.status === 'pending'" type="link" danger @click="handleReview(record, 'disabled')">驳回</a-button>
              <a-button type="link" @click="handleDownload(record)">
                <DownloadOutlined />下载
              </a-button>
              <a-button v-if="record.status !== 'pending'" type="link" @click="toggleStatus(record)">
                {{ record.status === 'enabled' ? '停用' : '启用' }}
              </a-button>
              <a-button type="link" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.resource-manage-page {
  padding: 16px;
}

.filter-form {
  row-gap: 12px;
}
/* ✅ 优化：表格单元格垂直居中，提升视觉体验 */
:deep(.ant-table-cell) {
  vertical-align: middle !important;
}
</style>