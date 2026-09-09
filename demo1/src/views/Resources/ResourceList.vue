<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { DownloadOutlined, FileOutlined, ThunderboltOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { getResourceList, searchResources } from '../../utils/api'

const router = useRouter()
const loading = ref(false)
const isEsSearch = ref(false)
const usedDbFallback = ref(false)

const filterForm = reactive({
  keyword: '',
  tag: '',
})

const tagOptions = [
  { label: '文档', value: 'document' },
  { label: '视频', value: 'video' },
  { label: '代码', value: 'code' },
  { label: '其他', value: 'other' },
]

const resourceList = ref([])
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

let searchTimer = null

const fetchResourceList = async () => {
  loading.value = true
  const hasKeyword = !!filterForm.keyword?.trim()
  isEsSearch.value = hasKeyword
  usedDbFallback.value = false
  try {
    let data = {}
    if (hasKeyword) {
      const keyword = filterForm.keyword.trim()
      const res = await searchResources({
        keyword,
        tag: filterForm.tag || undefined,
        page: pagination.current,
        pageSize: pagination.pageSize,
      })
      data = res.data || {}

      // ES 不可用或无结果时自动降级到数据库检索，避免"有资源但搜不到"
      const esRecords = Array.isArray(data.records) ? data.records : []
      if (esRecords.length === 0) {
        const fallbackRes = await getResourceList({
          keyword,
          tag: filterForm.tag || undefined,
        })
        const fallbackData = fallbackRes.data || []
        const fallbackRecords = Array.isArray(fallbackData) ? fallbackData : []
        if (fallbackRecords.length > 0) {
          isEsSearch.value = false
          usedDbFallback.value = true
          // 数据库接口返回全量列表，这里做前端分页
          const start = (pagination.current - 1) * pagination.pageSize
          resourceList.value = fallbackRecords.slice(start, start + pagination.pageSize)
          pagination.total = fallbackRecords.length
          return
        }
      }
      resourceList.value = esRecords
      pagination.total = data.total || 0
    } else {
      const res = await getResourceList({ tag: filterForm.tag || undefined })
      const data = res.data || []
      resourceList.value = Array.isArray(data) ? data : []
      pagination.total = resourceList.value.length
    }
  } catch (err) {
    antMessage.error(err?.description || err?.message || '加载资源列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page, pageSize) => {
  pagination.current = page
  pagination.pageSize = pageSize
  fetchResourceList()
}

const handleReset = () => {
  filterForm.keyword = ''
  filterForm.tag = ''
  pagination.current = 1
  fetchResourceList()
}

const handleSearch = () => {
  pagination.current = 1
  fetchResourceList()
}

const handleKeywordInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.current = 1
    fetchResourceList()
  }, 400)
}

const handleDownload = (resource) => {
  if (!resource?.downloadUrl) {
    antMessage.warning('该资源无下载链接，无法下载！')
    return
  }
  try {
    const link = document.createElement('a')
    link.href = `/api/resource/download/${resource.id}`
    link.download = resource.name || '学习资源'
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    antMessage.success(`开始下载：${resource.name}`)
  } catch (err) {
    antMessage.error('下载失败，请重试！')
  }
}

const handleOpenDetail = (resource) => {
  router.push(`/user/resourceDetail/${resource.id}`)
}

const sanitizeHighlight = (text) => {
  if (text == null) return ''
  return String(text).replace(/<(?!\/?em\b)[^>]*>/gi, '')
}

const tagText = {
  document: '文档', code: '代码', other: '其他',
}

onMounted(() => {
  fetchResourceList()
})
</script>

<template>
  <div class="resource-list-page">
    <a-card title="资源筛选" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="关键词">
          <a-input
            v-model:value="filterForm.keyword"
            allow-clear
            placeholder="搜索资源名称或描述"
            style="width: 300px"
            @input="handleKeywordInput"
          />
        </a-form-item>
        <a-form-item label="类型">
          <a-select
            v-model:value="filterForm.tag"
            :options="tagOptions"
            placeholder="全部类型"
            allow-clear
            style="width: 180px"
            @change="handleSearch"
          />
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" @click="handleSearch" :loading="loading">查询</a-button>
            <a-button @click="handleReset">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
      <div v-if="isEsSearch" class="es-tip">
        <ThunderboltOutlined style="color: #faad14" /> 正在使用全文检索引擎搜索
      </div>
      <div v-else-if="usedDbFallback" class="es-tip es-tip-warning">
        <ThunderboltOutlined style="color: #faad14" /> 检索引擎暂不可用，已自动切换数据库检索
      </div>
    </a-card>

    <a-card title="学习资源" bordered style="margin-top: 16px">
      <a-list :data-source="resourceList" :loading="loading" item-layout="vertical">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #avatar>
                <FileOutlined style="font-size: 32px; color: var(--color-accent)" />
              </template>
              <template #title>
                <div class="resource-title" @click="handleOpenDetail(item)">
                  <span v-html="sanitizeHighlight(item.name)"></span>
                  <a-tag color="blue">{{ tagText[item.tag || item.resourceType] || item.tag }}</a-tag>
                </div>
              </template>
              <template #description>
                <div class="resource-desc" v-html="sanitizeHighlight(item.description)"></div>
                <div class="resource-meta">
                  <span>文件大小：{{ item.fileSize }}</span>
                  <span>下载次数：{{ item.downloadCount }}</span>
                  <span>更新时间：{{ item.updateTime }}</span>
                </div>
              </template>
            </a-list-item-meta>
            <template #actions>
              <a
                @click="handleOpenDetail(item)"
                style="color: #52c41a; cursor: pointer"
              >
                <EyeOutlined /> 详情
              </a>
              <a
                @click="handleDownload(item)"
                :disabled="!item.downloadUrl"
                :style="{ color: item.downloadUrl ? 'var(--color-accent)' : '#999', cursor: item.downloadUrl ? 'pointer' : 'not-allowed' }"
              >
                <DownloadOutlined /> {{ item.downloadUrl ? '下载' : '无下载链接' }}
              </a>
            </template>
          </a-list-item>
        </template>
        <template #empty>
          <div style="text-align: center; padding: 32px; color: #999">暂无匹配的学习资源</div>
        </template>
      </a-list>

      <a-pagination
        v-if="pagination.total > pagination.pageSize"
        v-model:current="pagination.current"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        @change="handlePageChange"
        style="margin-top: 16px; text-align: right"
      />
    </a-card>
  </div>
</template>

<style scoped>
.resource-list-page { padding: 16px; }
:deep(.filter-form) { row-gap: 12px; }
.es-tip { margin-top: 10px; font-size: 12px; color: #8c8c8c; }
.es-tip-warning { color: #ad6800; }
.resource-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-family: var(--font-body);
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--color-text);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-in-out);
}
.resource-title:hover { color: var(--color-accent); }
.resource-desc { margin: 8px 0; color: var(--color-text-secondary); font-family: var(--font-body); font-size: 0.9375rem; line-height: 1.6; }
:deep(.resource-title em),
:deep(.resource-desc em) {
  color: var(--color-accent);
  font-style: normal;
  font-weight: 600;
}
.resource-meta { display: flex; gap: 24px; color: var(--color-text-muted); font-family: var(--font-body); font-size: 0.8125rem; flex-wrap: wrap; }
:deep(.ant-list-item-meta-title) { font-family: var(--font-body) !important; }
:deep(.ant-list-item-actions > li > a) { display: inline-flex; align-items: center; gap: 4px; }
</style>
