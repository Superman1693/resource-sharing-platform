<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { EyeOutlined, MessageOutlined, LikeOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { getNoteList, searchNotes, getRecommendList } from '../../utils/api'
import { exportAsMarkdown, exportAsPDF, exportAsZip } from '../../utils/exportUtils'
import { DownloadOutlined } from '@ant-design/icons-vue'
import { CATEGORY_OPTIONS, CATEGORY_TEXT } from '../../utils/constant'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const isEsSearch = ref(false)
const usedDbFallback = ref(false)

const routePrefix = route.path.startsWith('/user') ? '/user' : '/main'

const filterForm = reactive({
  keyword: '',
  category: undefined,
  sortType: 'hot',
  contentType: undefined,
})

const categoryOptions = CATEGORY_OPTIONS

const contentTypeOptions = [
  { label: '文章', value: 'article' },
  { label: '问题', value: 'question' },
  { label: '笔记', value: 'note' },
]

const sortOptions = [
  { label: '推荐', value: 'recommend' },
  { label: '热度排序', value: 'hot' },
  { label: '最新排序', value: 'latest' },
]

const noteList = ref([])
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })
const selectedNoteIds = ref([])

const toggleSelect = (noteId) => {
  const idx = selectedNoteIds.value.indexOf(noteId)
  if (idx === -1) {
    selectedNoteIds.value = [...selectedNoteIds.value, noteId]
  } else {
    selectedNoteIds.value = selectedNoteIds.value.filter(id => id !== noteId)
  }
}

const isSelected = (noteId) => selectedNoteIds.value.includes(noteId)

const handleSelectAll = () => {
  if (selectedNoteIds.value.length === noteList.value?.length) {
    selectedNoteIds.value = []
  } else {
    selectedNoteIds.value = (noteList.value || []).map(n => n.id)
  }
}

const handleExportMarkdown = (note) => {
  exportAsMarkdown(note)
  antMessage.success('已导出为 Markdown 文件')
}

const handleExportPDF = (note) => {
  exportAsPDF(note)
}

const handleExportSelected = async () => {
  const selected = noteList.value.filter(n => selectedNoteIds.value.includes(n.id))
  if (selected.length === 0) {
    antMessage.warning('请先选择要导出的笔记')
    return
  }
  await exportAsZip(selected)
  antMessage.success(`已导出 ${selected.length} 篇笔记`)
}

let searchTimer = null

const fetchNoteList = async () => {
  loading.value = true
  const hasKeyword = !!filterForm.keyword?.trim()
  isEsSearch.value = hasKeyword
  usedDbFallback.value = false
  try {
    let data = {}
    // 推荐排序：走个性化推荐接口（登录走标签偏好，未登录走热门兜底，不分页）
    if (filterForm.sortType === 'recommend' && !hasKeyword) {
      const size = pagination.pageSize
      const res = await getRecommendList(size)
      const records = Array.isArray(res.data) ? res.data : []
      noteList.value = records
      pagination.total = records.length
      records.forEach(async (item) => {
        summaryHtmlCache.value[item.id] = await renderSummaryAsync(item)
      })
      return
    }
    if (hasKeyword) {
      const keyword = filterForm.keyword.trim()
      const res = await searchNotes({
        keyword,
        category: filterForm.category,
        contentType: filterForm.contentType,
        sortType: filterForm.sortType,
        page: pagination.current,
        pageSize: pagination.pageSize,
      })
      data = res.data || {}

      // ES 索引未同步时自动降级到数据库检索，避免“有数据但搜不到”
      const esRecords = Array.isArray(data.records) ? data.records : []
      if (esRecords.length === 0) {
        const fallbackRes = await getNoteList({
          page: pagination.current,
          pageSize: pagination.pageSize,
          keyword,
          category: filterForm.category,
          contentType: filterForm.contentType,
          sortType: filterForm.sortType,
        })
        const fallbackData = fallbackRes.data || {}
        const fallbackRecords = Array.isArray(fallbackData.records) ? fallbackData.records : []
        if (fallbackRecords.length > 0) {
          usedDbFallback.value = true
          data = fallbackData
        }
      }
    } else {
      const res = await getNoteList({
        page: pagination.current,
        pageSize: pagination.pageSize,
        category: filterForm.category,
        contentType: filterForm.contentType,
        sortType: filterForm.sortType,
      })
      data = res.data || {}
    }
    noteList.value = Array.isArray(data.records) ? data.records : []
    pagination.total = data.total || 0

    // 异步渲染摘要
    noteList.value.forEach(async (item) => {
      summaryHtmlCache.value[item.id] = await renderSummaryAsync(item)
    })
  } catch (err) {
    antMessage.error(err?.description || err?.message || '加载笔记列表失败')
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page, pageSize) => {
  pagination.current = page
  pagination.pageSize = pageSize
  fetchNoteList()
}

const handleReset = () => {
  filterForm.keyword = ''
  filterForm.category = undefined
  filterForm.contentType = undefined
  filterForm.sortType = 'hot'
  pagination.current = 1
  fetchNoteList()
}

const handleSearch = () => {
  pagination.current = 1
  fetchNoteList()
}

const handleKeywordInput = () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pagination.current = 1
    fetchNoteList()
  }, 400)
}

const viewDetail = (note) => {
  router.push(`${routePrefix}/noteDetail/${note.id}`)
}

const sanitizeHighlight = (text) => {
  if (text == null) return ''
  return String(text).replace(/<(?!\/?em\b)[^>]*>/gi, '')
}

marked.setOptions({ gfm: true, breaks: true, silent: true })

// 预渲染的摘要缓存
const summaryHtmlCache = ref({})

const renderMarkdownSummary = (item) => {
  return summaryHtmlCache.value[item.id] || ''
}

// 异步渲染摘要
const renderSummaryAsync = async (item) => {
  const raw = item.summary || item.content || ''
  if (!raw) return ''
  try {
    const html = await marked.parse(String(raw).replace(/\*{4,}/g, '**'))
    return DOMPurify.sanitize(String(html), {
      ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'code', 'ul', 'ol', 'li', 'blockquote'],
      ALLOWED_ATTR: ['class'],
    })
  } catch (e) {
    console.error('摘要解析失败:', e)
    return ''
  }
}

const categoryText = CATEGORY_TEXT

const contentTypeText = {
  article: '文章',
  question: '问题',
  note: '笔记',
}

onMounted(() => {
  fetchNoteList()
})
</script>

<template>
  <div class="note-list-page">
    <a-card title="笔记筛选" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="关键词">
          <a-input
            v-model:value="filterForm.keyword"
            allow-clear
            placeholder="搜索笔记标题或内容"
            style="width: 300px"
            @input="handleKeywordInput"
          />
        </a-form-item>
        <a-form-item label="分类">
          <a-select
            v-model:value="filterForm.category"
            :options="categoryOptions"
            placeholder="全部分类"
            allow-clear
            style="width: 180px"
            @change="handleSearch"
          />
        </a-form-item>
        <a-form-item label="内容类型">
          <a-select
            v-model:value="filterForm.contentType"
            :options="contentTypeOptions"
            placeholder="全部类型"
            allow-clear
            style="width: 150px"
            @change="handleSearch"
          />
        </a-form-item>
        <a-form-item label="排序方式">
          <a-select
            v-model:value="filterForm.sortType"
            :options="sortOptions"
            style="width: 150px"
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
      <div v-if="isEsSearch && !usedDbFallback" class="es-tip">
        <ThunderboltOutlined style="color: #faad14" /> 正在使用全文检索引擎搜索
      </div>
      <div v-else-if="isEsSearch && usedDbFallback" class="es-tip es-tip-warning">
        <ThunderboltOutlined style="color: #faad14" /> ES 暂无结果，已自动切换数据库检索
      </div>
    </a-card>

    <a-card bordered style="margin-top: 16px">
      <template #title>
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <span>学习笔记</span>
          <a-space>
            <a-button size="small" @click="handleSelectAll">
              {{ selectedNoteIds.length === noteList.length && noteList.length > 0 ? '取消全选' : '全选' }}
            </a-button>
            <a-button size="small" @click="handleExportSelected" :disabled="selectedNoteIds.length === 0">
              <DownloadOutlined /> 导出选中 ({{ selectedNoteIds.length }})
            </a-button>
          </a-space>
        </div>
      </template>
      <a-list
        :data-source="noteList"
        :loading="loading"
        :grid="{ gutter: 16, xs: 1, sm: 2, md: 2, lg: 3, xl: 3, xxl: 3 }"
      >
        <template #renderItem="{ item }">
          <a-list-item>
            <a-card hoverable class="note-card" :class="{ 'note-card-selected': isSelected(item.id) }">
              <div class="card-checkbox" @click.stop="toggleSelect(item.id)">
                <a-checkbox :checked="isSelected(item.id)" @click.stop="toggleSelect(item.id)" />
              </div>
              <template #actions>
                <a-dropdown :trigger="['click']" @click.stop>
                  <DownloadOutlined @click.stop />
                  <template #overlay>
                    <a-menu>
                      <a-menu-item @click.stop="handleExportMarkdown(item)">导出为 Markdown</a-menu-item>
                      <a-menu-item @click.stop="handleExportPDF(item)">导出为 PDF</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </template>
              <template #cover>
                <img :alt="item.title" :src="item.coverImage" style="height: 180px; object-fit: cover" />
              </template>
              <div @click="viewDetail(item)" style="cursor: pointer;">
              <a-card-meta>
                <template #title>
                  <span v-html="sanitizeHighlight(item.title)"></span>
                </template>
                <template #description>
                  <div class="note-meta">
                    <a-space>
                      <a-tag color="blue">{{ categoryText[item.category] }}</a-tag>
                      <a-tag :color="item.contentType === 'article' ? 'green' : item.contentType === 'question' ? 'orange' : 'purple'">
                        {{ contentTypeText[item.contentType] }}
                      </a-tag>
                    </a-space>
                    <div class="note-summary" v-html="renderMarkdownSummary(item)"></div>
                    <div class="note-stats">
                      <span><EyeOutlined /> {{ item.viewCount }}</span>
                      <span><MessageOutlined /> {{ item.commentCount }}</span>
                      <span><LikeOutlined /> {{ item.likeCount }}</span>
                    </div>
                    <div class="note-footer">
                      <span>{{ item.author }}</span>
                      <span>{{ item.publishTime }}</span>
                    </div>
                  </div>
                </template>
              </a-card-meta>
              </div>
            </a-card>
          </a-list-item>
        </template>
      </a-list>

      <a-pagination
        v-model:current="pagination.current"
        :total="pagination.total"
        :page-size="pagination.pageSize"
        show-size-changer
        :page-size-options="['10', '20', '50']"
        @change="handlePageChange"
        @showSizeChange="handlePageChange"
        style="margin-top: 16px; text-align: right"
      />
    </a-card>
  </div>
</template>

<style scoped>
.note-list-page { padding: 16px; }
.filter-form { row-gap: 12px; }
.es-tip { margin-top: 10px; font-size: 12px; color: #8c8c8c; }
.es-tip-warning { color: #ad6800; }
.note-card { cursor: pointer; height: 100%; position: relative; }
.note-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
.note-card-selected { border-color: #1890ff !important; box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2); }
.card-checkbox {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 10;
  cursor: pointer;
  padding: 4px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 4px;
}
.note-meta { padding-top: 8px; }
.note-summary {
  margin: 12px 0; color: #666; font-size: 14px;
  overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}

.note-summary :deep(p) {
  margin: 0;
}

.note-summary :deep(code) {
  padding: 0 4px;
  border-radius: 4px;
  background: #f5f5f5;
  font-size: 12px;
}
:deep(.ant-card-meta-title em),
:deep(.note-summary em) {
  color: #b45309;
  font-style: normal;
  font-weight: 600;
}
.note-stats { display: flex; gap: 16px; margin: 12px 0; color: #999; font-size: 12px; }
.note-footer {
  display: flex; justify-content: space-between;
  margin-top: 12px; padding-top: 12px;
  border-top: 1px solid #f0f0f0; font-size: 12px; color: #999;
}
</style>
