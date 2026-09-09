<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { EyeOutlined, LikeOutlined } from '@ant-design/icons-vue'
import { getNotesByTag } from '../../utils/api'
import { CATEGORY_TEXT_SHORT } from '../../utils/constant'
import { formatDate } from '../../utils/dateUtils'

const route = useRoute()
const router = useRouter()
const tagName = ref(decodeURIComponent(route.params.name || ''))
const notes = ref([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const pageSize = 12

const load = async () => {
  loading.value = true
  try {
    const res = await getNotesByTag(tagName.value, { page: page.value, pageSize })
    notes.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (err) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const goNote = (id) => router.push(`/user/noteDetail/${id}`)
const onPage = (p) => { page.value = p; load() }

watch(() => route.params.name, (n) => {
  if (n) { tagName.value = decodeURIComponent(n); page.value = 1; load() }
})

onMounted(load)
</script>

<template>
  <div class="tag-detail-page">
    <div class="page-header">
      <a class="back" @click="router.push('/user/tags')">← 标签广场</a>
      <h2 class="page-title">标签：{{ tagName }}</h2>
      <p class="page-subtitle">共 {{ total }} 篇笔记</p>
    </div>
    <a-spin :spinning="loading">
      <div v-if="notes.length === 0 && !loading" class="empty">该标签下暂无笔记</div>
      <div v-else class="note-grid">
        <div v-for="n in notes" :key="n.id" class="note-card" @click="goNote(n.id)">
          <span class="cat">{{ CATEGORY_TEXT_SHORT[n.category] || '其他' }}</span>
          <h3 class="title">{{ n.title }}</h3>
          <p class="summary">{{ n.summary }}</p>
          <div class="meta">
            <span><EyeOutlined /> {{ n.viewCount || 0 }}</span>
            <span><LikeOutlined /> {{ n.likeCount || 0 }}</span>
            <span>{{ formatDate(n.publishTime) }}</span>
          </div>
        </div>
      </div>
    </a-spin>
    <div class="pagination" v-if="total > pageSize">
      <a-pagination v-model:current="page" :total="total" :pageSize="pageSize" :showSizeChanger="false" @change="onPage" />
    </div>
  </div>
</template>

<style scoped>
.tag-detail-page { max-width: 1080px; margin: 0 auto; padding: 8px 16px 40px; }
.page-header { padding: 16px 0 24px; }
.back { font-size: 0.875rem; color: var(--color-text-muted, #94a3b8); cursor: pointer; }
.back:hover { color: var(--color-accent, #6366f1); }
.page-title { font-size: 1.5rem; font-weight: 700; color: var(--color-primary, #1e293b); margin: 8px 0 4px; }
.page-subtitle { font-size: 0.875rem; color: var(--color-text-muted, #94a3b8); margin: 0; }
.empty { padding: 60px 0; text-align: center; color: var(--color-text-muted, #94a3b8); }
.note-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.note-card {
  background: #fff; border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 12px; padding: 20px; cursor: pointer; transition: all 0.2s;
  display: flex; flex-direction: column; gap: 8px;
}
.note-card:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(0,0,0,0.06); border-color: var(--color-accent, #6366f1); }
.cat {
  display: inline-block; padding: 2px 8px; border-radius: 999px;
  font-size: 0.6875rem; font-weight: 600;
  background: var(--color-accent-glow, rgba(99,102,241,0.08)); color: var(--color-accent, #6366f1);
  width: fit-content;
}
.title { font-size: 1rem; font-weight: 600; color: var(--color-primary, #1e293b); margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary {
  font-size: 0.8125rem; color: var(--color-text-secondary, #64748b); line-height: 1.6;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  margin: 0; flex: 1;
}
.meta { display: flex; gap: 12px; font-size: 0.75rem; color: var(--color-text-muted, #94a3b8); }
.pagination { display: flex; justify-content: center; margin-top: 24px; }
@media (max-width: 768px) { .note-grid { grid-template-columns: 1fr; } }
</style>
