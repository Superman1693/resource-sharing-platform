<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { message as antMessage, Modal } from 'ant-design-vue'

import { getCommentList, deleteComment, approveComment } from '../../utils/api'

const loading = ref(false)

const statusOptions = [
  { label: '已审核', value: 'approved' },
  { label: '待审核', value: 'pending' },
  { label: '已屏蔽', value: 'hidden' },
]

// ✅ 【核心修复1】声明缺失的filterForm响应式对象，初始化所有绑定字段
const filterForm = reactive({
  keyword: '',    // 关键词
  noteId: '',     // 笔记ID
  status: undefined // 状态
})

const commentList = ref([])

// ✅ 【核心修复2】计算属性加全量空值校验+字符串安全处理，杜绝空指针
const filteredList = computed(() => {
  return commentList.value.filter((item) => {
    // 先校验item是否有效，无效项直接过滤
    if (!item) return false
    
    // 安全获取值，避免属性不存在报错 + 统一转字符串防类型错误
    const content = String(item.content || '').toLowerCase()
    const username = String(item.username || '').toLowerCase()
    const keyword = String(filterForm.keyword || '').toLowerCase()
    const itemNoteId = String(item.noteId || '')
    const formNoteId = String(filterForm.noteId || '')

    // 关键词匹配（兼容空值）
    const matchKeyword = keyword 
      ? content.includes(keyword) || username.includes(keyword) 
      : true
    // 笔记ID匹配（兼容空值）
    const matchNoteId = formNoteId ? itemNoteId === formNoteId : true
    // 状态匹配（兼容空值）
    const matchStatus = filterForm.status ? item.status === filterForm.status : true
    
    return matchKeyword && matchNoteId && matchStatus
  })
})

const fetchCommentList = async () => {
  loading.value = true
  try {
    const params = {
      keyword: filterForm.keyword || undefined,
      noteId: filterForm.noteId || undefined,
      status: filterForm.status,
    }
    const res = await getCommentList(params)
    const data = res.data || []
    // ✅ 加固：确保永远是数组，过滤无效项
    commentList.value = Array.isArray(data) ? data.filter(Boolean) : []
  } catch (err) {
    if (err?.isBusinessError) {
      if (err.code === 40100) {
        antMessage.warning('未登录或登录已过期，请先登录')
      } else if (err.code === 40101) {
        antMessage.warning('无权限')
      } else {
        antMessage.error(err.description || err.message || '加载评论列表失败')
      }
    } else {
      antMessage.error('加载评论列表失败')
    }
    commentList.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  filterForm.keyword = ''
  filterForm.noteId = ''
  filterForm.status = undefined
  fetchCommentList()
}

const handleSearch = () => {
  fetchCommentList()
}

// ✅ 【加固】操作方法加record有效性校验，避免传参异常报错
const handleApprove = async (record) => {
  if (!record?.id) return antMessage.warning('数据异常，无法操作')
  try {
    const res = await approveComment(record.id, 'approved')
    if (res.code === 0) {
      record.status = 'approved'
      antMessage.success('评论已通过审核')
    } else {
      antMessage.error('操作失败')
    }
  } catch (err) {
    antMessage.error(err?.description || err?.message || '操作失败')
  }
}

const handleHide = async (record) => {
  if (!record?.id) return antMessage.warning('数据异常，无法操作')
  try {
    const res = await approveComment(record.id, 'hidden')
    if (res.code === 0) {
      record.status = 'hidden'
      antMessage.success('评论已屏蔽')
    } else {
      antMessage.error('操作失败')
    }
  } catch (err) {
    antMessage.error(err?.description || err?.message || '操作失败')
  }
}

const handleDelete = (record) => {
  if (!record?.id) return antMessage.warning('数据异常，无法删除')
  Modal.confirm({
    title: '确认删除该评论吗？',
    onOk: async () => {
      try {
        const res = await deleteComment(record.id)
        if (res.code === 0 || res.data === true) {
          antMessage.success('删除成功')
          await fetchCommentList()
        } else {
          antMessage.error('删除失败')
        }
      } catch (err) {
        antMessage.error(err?.description || err?.message || '删除失败')
      }
    },
  })
}

const statusText = {
  approved: '已审核',
  pending: '待审核',
  hidden: '已屏蔽',
}

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '笔记标题', dataIndex: 'noteTitle', width: 200 },
  {
    title: '评论用户',
    dataIndex: 'username',
    width: 120,
    customRender: ({ record }) => record.username || '未知用户',
  },
  { title: '评论内容', dataIndex: 'content', width: 300 },
  {
    title: '状态',
    dataIndex: 'status',
    width: 100,
    customRender: ({ text }) => statusText[text] || '未知状态',
  },
  { title: '点赞数', dataIndex: 'likeCount', width: 100 },
  { title: '评论时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', fixed: 'right', width: 220 },
]

onMounted(() => {
  fetchCommentList()
})
</script>

<template>
  <div class="comment-manage-page">
    <a-card title="评论筛选" bordered>
      <a-form :model="filterForm" layout="inline" class="filter-form">
        <a-form-item label="关键词">
          <a-input
            v-model:value="filterForm.keyword"
            allow-clear
            placeholder="搜索评论内容或用户"
            style="width: 240px"
          />
        </a-form-item>
        <a-form-item label="笔记ID">
          <a-input
            v-model:value="filterForm.noteId"
            allow-clear
            placeholder="输入笔记ID"
            style="width: 150px"
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

    <a-card title="评论列表" bordered style="margin-top: 16px">
      <a-table 
        :columns="columns" 
        :data-source="filteredList" 
        :loading="loading" 
        row-key="id" 
        :scroll="{ x: 1200 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-space>
              <a-button
                v-if="record?.status === 'pending'"
                type="link"
                @click="handleApprove(record)"
              >通过</a-button>
              <a-button type="link" @click="handleHide(record)">屏蔽</a-button>
              <a-button type="link" danger @click="handleDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.comment-manage-page {
  padding: 16px;
}

/* ✅ 修复样式：适配AntD表单行间距，加:deep()穿透作用域 */
:deep(.filter-form) {
  row-gap: 12px;
}
</style>