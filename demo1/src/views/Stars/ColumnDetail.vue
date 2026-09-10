<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getColumnDetail, addNoteToColumn, removeNoteFromColumn, getMyNotes } from '../../utils/api'
import { useUserStore } from '../../store/userLogin'
import { message } from 'ant-design-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const column = ref(null)
const notes = ref([])
const myPublishedNotes = ref([])
const addModalVisible = ref(false)
const selectedNoteId = ref(undefined)
const sortOrder = ref(undefined)
const submitting = ref(false)

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getColumnDetail(route.params.id)
    if (res.code === 0 && res.data) {
      column.value = res.data
      notes.value = res.data.notes || []
    }
  } catch (e) {
    message.error('加载专栏失败')
  } finally {
    loading.value = false
  }
}

// 查自己已发布且未归属其他专栏的笔记（供加入专栏选择）
const fetchMyNotes = async () => {
  try {
    const res = await getMyNotes({ status: 'published', page: 1, pageSize: 50 })
    const data = res.data || {}
    myPublishedNotes.value = (data.records || []).filter(n => !n.collectionId)
  } catch (_) {
    myPublishedNotes.value = []
  }
}

const openAdd = async () => {
  selectedNoteId.value = undefined
  sortOrder.value = undefined
  await fetchMyNotes()
  addModalVisible.value = true
}

const submitAdd = async () => {
  if (!selectedNoteId.value) {
    message.warning('请选择笔记')
    return
  }
  submitting.value = true
  try {
    const res = await addNoteToColumn({
      collectionId: Number(route.params.id),
      noteId: Number(selectedNoteId.value),
      sortOrder: sortOrder.value ? Number(sortOrder.value) : undefined
    })
    if (res.code === 0) {
      message.success('已加入专栏')
      addModalVisible.value = false
      fetchDetail()
    }
  } catch (e) {
    message.error(e?.description || '加入失败')
  } finally {
    submitting.value = false
  }
}

const removeNote = (note) => {
  removeNoteFromColumn(note.id).then(res => {
    if (res.code === 0) {
      message.success('已移出专栏')
      fetchDetail()
    }
  }).catch(() => message.error('移出失败'))
}

const goNote = (id) => router.push(`/user/noteDetail/${id}`)

onMounted(fetchDetail)
</script>

<template>
  <div class="column-detail-page" v-if="column">
    <div class="head">
      <a-button @click="router.back()">返回</a-button>
      <div class="head-info">
        <h2>{{ column.title }}</h2>
        <div class="meta">
          <a-tag :color="column.status === 'active' ? 'green' : 'default'">{{ column.status === 'active' ? '连载中' : '已完结' }}</a-tag>
          <span v-if="column.starName">{{ column.starName }}</span>
          <span v-if="column.authorName">作者 {{ column.authorName }}</span>
        </div>
        <p class="desc">{{ column.description || '暂无描述' }}</p>
      </div>
      <a-button type="primary" @click="openAdd">添加笔记到专栏</a-button>
    </div>

    <div class="chapters">
      <div v-if="notes.length === 0" class="empty">专栏还没有章节，点击「添加笔记到专栏」</div>
      <div v-for="(note, idx) in notes" :key="note.id" class="chapter">
        <div class="ch-num">{{ note.collectionSort || idx + 1 }}</div>
        <div class="ch-main" @click="goNote(note.id)">
          <div class="ch-title">{{ note.title }}</div>
          <div class="ch-sub">{{ note.summary || '' }}</div>
        </div>
        <a-button size="small" danger @click="removeNote(note)">移出</a-button>
      </div>
    </div>

    <a-modal v-model:open="addModalVisible" title="添加笔记到专栏" :confirm-loading="submitting" @ok="submitAdd" ok-text="添加" cancel-text="取消">
      <a-form layout="vertical">
        <a-form-item label="选择笔记（仅显示已发布且未归属其他专栏的）" required>
          <a-select
            v-model:value="selectedNoteId"
            placeholder="选择笔记"
            :options="myPublishedNotes.map(n => ({ label: n.title, value: n.id }))"
            show-search
            option-filter-prop="label"
          />
        </a-form-item>
        <a-form-item label="章节序号（留空自动追加到末尾）">
          <a-input-number v-model:value="sortOrder" :min="1" placeholder="如 1" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
  <div v-else-if="!loading" class="loading">
    <a-empty description="专栏不存在" />
  </div>
</template>

<style scoped>
.column-detail-page { padding: 16px; max-width: 860px; margin: 0 auto; }
.head { display: flex; align-items: flex-start; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.head-info { flex: 1; min-width: 200px; }
.head-info h2 { margin: 0 0 8px; font-size: 22px; font-weight: 600; }
.meta { display: flex; gap: 10px; align-items: center; font-size: 13px; color: #888; margin-bottom: 8px; flex-wrap: wrap; }
.desc { color: #666; font-size: 14px; margin: 0; }
.chapters { display: flex; flex-direction: column; gap: 8px; }
.empty { text-align: center; padding: 40px; color: #999; }
.chapter { display: flex; align-items: center; gap: 14px; padding: 14px; background: #fff; border: 1px solid #f0f0f0; border-radius: 8px; }
.ch-num { width: 32px; height: 32px; border-radius: 50%; background: #6366f1; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 600; flex-shrink: 0; }
.ch-main { flex: 1; cursor: pointer; min-width: 0; }
.ch-title { font-weight: 600; font-size: 15px; }
.ch-sub { color: #999; font-size: 13px; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.loading { padding: 60px 0; }
</style>
