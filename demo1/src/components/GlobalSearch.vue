<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { SearchOutlined } from '@ant-design/icons-vue'
import { searchNotes } from '../utils/api'
import { CATEGORY_TEXT_SHORT } from '../utils/constant'

const router = useRouter()
const keyword = ref('')
const results = ref([])
const loading = ref(false)
const showDropdown = ref(false)

let searchTimer = null

const doSearch = async () => {
  const q = keyword.value.trim()
  if (!q || q.length < 2) {
    results.value = []
    showDropdown.value = false
    return
  }
  loading.value = true
  try {
    const res = await searchNotes({ keyword: q, pageSize: 6 })
    results.value = res.data?.records || []
    showDropdown.value = true
  } catch (_) {
    results.value = []
  } finally {
    loading.value = false
  }
}

watch(keyword, () => {
  clearTimeout(searchTimer)
  if (!keyword.value.trim()) {
    results.value = []
    showDropdown.value = false
    return
  }
  searchTimer = setTimeout(doSearch, 400)
})

const goToNote = (note) => {
  showDropdown.value = false
  keyword.value = ''
  router.push(`/user/noteDetail/${note.id}`)
}

// 回车或点"查看全部"跳独立搜索结果页
const goToSearchPage = () => {
  const q = keyword.value.trim()
  if (!q) return
  showDropdown.value = false
  router.push(`/user/search?q=${encodeURIComponent(q)}`)
  keyword.value = ''
}

const handleBlur = () => {
  setTimeout(() => { showDropdown.value = false }, 200)
}

const sanitizeHighlight = (text) => {
  if (text == null) return ''
  return String(text).replace(/<(?!\/?em\b)[^>]*>/gi, '')
}

const categoryText = CATEGORY_TEXT_SHORT
</script>

<template>
  <div class="search-wrap">
    <div class="search-input-wrap">
      <SearchOutlined class="search-icon" />
      <input
        v-model="keyword"
        class="search-input"
        placeholder="搜索笔记..."
        @focus="keyword && (showDropdown = true)"
        @blur="handleBlur"
        @keyup.enter="goToSearchPage"
      />
      <span v-if="loading" class="search-loading">
        <a-spin size="small" />
      </span>
    </div>

    <div v-if="showDropdown && results.length > 0" class="search-dropdown">
      <div
        v-for="note in results"
        :key="note.id"
        class="search-item"
        @mousedown="goToNote(note)"
      >
        <span class="search-cat">{{ categoryText[note.category] || '其他' }}</span>
        <span class="search-title" v-html="sanitizeHighlight(note.title)"></span>
      </div>
      <div class="search-more" @mousedown.prevent="goToSearchPage">查看全部结果 →</div>
    </div>
    <div v-else-if="showDropdown && !loading && keyword.length >= 2" class="search-dropdown">
      <div class="search-empty">未找到相关笔记</div>
    </div>
  </div>
</template>

<style scoped>
.search-wrap {
  position: relative;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--color-bg-warm);
  border-radius: 999px;
  padding: 0 14px;
  height: 34px;
  transition: all var(--duration-fast);
  border: 1px solid transparent;
}

.search-input-wrap:focus-within {
  background: #fff;
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-glow);
}

.search-icon {
  color: var(--color-text-muted);
  font-size: 14px;
  flex-shrink: 0;
}

.search-input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 0.8125rem;
  color: var(--color-text);
  width: 160px;
  font-family: var(--font-body);
}

.search-input::placeholder {
  color: var(--color-text-muted);
}

.search-loading {
  display: flex;
  align-items: center;
}

.search-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  min-width: 300px;
  background: #fff;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border-light);
  z-index: 1000;
  overflow: hidden;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background var(--duration-fast);
}

.search-item:hover {
  background: var(--color-bg);
}

.search-cat {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 0.6875rem;
  font-weight: 600;
  background: var(--color-accent-glow);
  color: var(--color-accent);
  flex-shrink: 0;
}

.search-title {
  font-size: 0.875rem;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.search-title em) {
  color: var(--color-accent);
  font-style: normal;
  font-weight: 600;
}

.search-empty {
  padding: 20px;
  text-align: center;
  font-size: 0.875rem;
  color: var(--color-text-muted);
}

.search-more {
  padding: 10px 16px;
  text-align: center;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--color-accent);
  cursor: pointer;
  border-top: 1px solid var(--color-border-light);
  transition: background var(--duration-fast);
}

.search-more:hover {
  background: var(--color-accent-soft);
}
</style>
