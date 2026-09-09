<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getTagList } from '../../utils/api'

const router = useRouter()
const tags = ref([])
const loading = ref(false)

const loadTags = async () => {
  loading.value = true
  try {
    const res = await getTagList()
    tags.value = res.data || []
  } catch (err) {
    message.error('加载标签失败')
  } finally {
    loading.value = false
  }
}

// 根据使用次数算字号（标签云）
const tagFontSize = (count) => {
  const c = count || 0
  if (c >= 20) return '1.25rem'
  if (c >= 10) return '1.0625rem'
  if (c >= 5) return '0.9375rem'
  return '0.8125rem'
}

const goTag = (name) => router.push(`/user/tag/${encodeURIComponent(name)}`)

onMounted(loadTags)
</script>

<template>
  <div class="tag-square-page">
    <div class="page-header">
      <h2 class="page-title">标签广场</h2>
      <p class="page-subtitle">按标签发现感兴趣的内容</p>
    </div>
    <a-spin :spinning="loading">
      <div v-if="tags.length === 0 && !loading" class="empty">暂无标签，发布带标签的笔记后这里会出现</div>
      <div v-else class="tag-cloud">
        <span
          v-for="t in tags"
          :key="t.id"
          class="tag-item"
          :style="{ fontSize: tagFontSize(t.usageCount) }"
          @click="goTag(t.name)"
        >
          {{ t.name }}
          <span class="tag-count">{{ t.usageCount }}</span>
        </span>
      </div>
    </a-spin>
  </div>
</template>

<style scoped>
.tag-square-page { max-width: 860px; margin: 0 auto; padding: 8px 16px 40px; }
.page-header { padding: 16px 0 24px; text-align: center; }
.page-title { font-size: 1.5rem; font-weight: 700; color: var(--color-primary, #1e293b); margin: 0 0 6px; }
.page-subtitle { font-size: 0.875rem; color: var(--color-text-muted, #94a3b8); margin: 0; }
.empty { padding: 60px 0; text-align: center; color: var(--color-text-muted, #94a3b8); }
.tag-cloud {
  display: flex; flex-wrap: wrap; gap: 12px;
  background: #fff; border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: 12px; padding: 28px;
}
.tag-item {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 8px 14px; background: var(--color-accent-glow, rgba(99,102,241,0.08));
  color: var(--color-accent, #6366f1); border-radius: 999px;
  font-weight: 500; cursor: pointer; transition: all 0.2s;
}
.tag-item:hover { background: var(--color-accent, #6366f1); color: #fff; }
.tag-count { font-size: 0.75em; opacity: 0.7; }
</style>
