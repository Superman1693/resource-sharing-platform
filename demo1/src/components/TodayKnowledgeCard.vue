<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { QuestionCircleOutlined, FileTextOutlined, BookOutlined } from '@ant-design/icons-vue'
import { getNoteList, getNoteDetail } from '../utils/api'

const router = useRouter()

const todayCard = ref({
  id: null,
  title: '',
  content: '',
  author: '',
  starName: '学习资源',
  starId: null,
  source: 'note',
  publishTime: '',
})

const loading = ref(false)

const cardTypes = {
  article: { icon: BookOutlined,           label: '文章' },
  question: { icon: QuestionCircleOutlined, label: '问题' },
  note: { icon: FileTextOutlined,          label: '笔记' },
}

const extractSummary = (content, maxLength = 180) => {
  if (!content) return ''
  const text = content.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
  return text.length <= maxLength ? text : text.substring(0, maxLength) + '...'
}

const setDefaultCard = () => {
  todayCard.value = {
    id: 1,
    title: 'Vue 3 Composition API 的核心优势',
    content:
      'Composition API 允许我们更好地组织和复用代码逻辑，通过 setup 函数可以将相关的逻辑组合在一起，而不是按照选项（data、methods、computed）来组织，极大提升了代码的可维护性。',
    author: '知识星球',
    starName: 'Vue3 深入学习',
    starId: 1,
    source: 'article',
    publishTime: '2025-10-01',
  }
}

const fetchTodayKnowledge = async () => {
  loading.value = true
  try {
    const listRes = await getNoteList({ pageSize: 50 })
    const data = listRes.data || {}
    const noteList = Array.isArray(data.records) ? data.records : (Array.isArray(data) ? data : [])

    if (!Array.isArray(noteList) || noteList.length === 0) {
      setDefaultCard()
      return
    }

    const randomNote = noteList[Math.floor(Math.random() * noteList.length)]
    const detailRes = await getNoteDetail(randomNote.id)
    const d = detailRes.data

    if (d) {
      todayCard.value = {
        id: d.id,
        title: d.title || '无标题',
        content: extractSummary(d.content),
        author: d.author || '匿名用户',
        starName: d.starName || '学习资源',
        starId: d.starId,
        source: d.contentType || 'note',
        publishTime: d.publishTime || d.createTime || '',
      }
    } else {
      setDefaultCard()
    }
  } catch (_) {
    setDefaultCard()
  } finally {
    loading.value = false
  }
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return String(dateStr).slice(0, 10)
}

onMounted(fetchTodayKnowledge)
</script>

<template>
  <div class="kc-card" @click="todayCard.id && router.push(`/user/noteDetail/${todayCard.id}`)">
    <!-- 左侧装饰条 -->
    <div class="kc-rail" />

    <div class="kc-body">
      <!-- 顶栏：标签 + 类型 -->
      <div class="kc-top">
        <div class="kc-label">
          <span class="kc-pulse" />
          TODAY'S KNOWLEDGE
        </div>
        <span class="kc-type" v-if="!loading">
          <component :is="cardTypes[todayCard.source]?.icon" />
          {{ cardTypes[todayCard.source]?.label || '笔记' }}
        </span>
      </div>

      <!-- 骨架屏 -->
      <a-skeleton v-if="loading" active :paragraph="{ rows: 3 }" />

      <template v-else>
        <!-- 标题 -->
        <h3 class="kc-title">{{ todayCard.title || '正在加载今日知识...' }}</h3>

        <!-- 内容引用块 -->
        <blockquote class="kc-quote">
          {{ todayCard.content || '每天一点学习，积累改变生活。' }}
        </blockquote>

        <!-- 底部信息 -->
        <div class="kc-footer">
          <div class="kc-meta">
            <span class="kc-author">{{ todayCard.author }}</span>
            <span class="kc-sep">·</span>
            <span class="kc-star">{{ todayCard.starName }}</span>
          </div>
          <time class="kc-date">{{ formatDate(todayCard.publishTime) }}</time>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* ── Card Shell ────────────────────────────────── */
.kc-card {
  display: flex;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 16px;
  overflow: hidden;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', sans-serif;
  cursor: pointer;
}

.kc-card:hover {
  box-shadow: 0 8px 32px rgba(27, 53, 87, 0.1);
  transform: translateY(-2px);
}

/* 左侧海军蓝装饰条 */
.kc-rail {
  width: 5px;
  flex-shrink: 0;
  background: linear-gradient(180deg, var(--color-accent) 0%, var(--color-accent-light) 100%);
}

/* ── Body ──────────────────────────────────────── */
.kc-body {
  flex: 1;
  padding: 26px 28px;
  min-width: 0;
}

/* ── Top Row ───────────────────────────────────── */
.kc-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.kc-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-accent);
}

/* 小呼吸动点 */
.kc-pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-accent);
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(0.85); }
}

.kc-type {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  background: #f4f4f5;
  color: #52525b;
}

/* ── Title ─────────────────────────────────────── */
.kc-title {
  font-size: 20px;
  font-weight: 700;
  color: #18181b;
  letter-spacing: -0.02em;
  line-height: 1.4;
  margin-bottom: 14px;
}

/* ── Quote Block ───────────────────────────────── */
.kc-quote {
  font-size: 14px;
  line-height: 1.8;
  color: #52525b;
  margin: 0 0 20px 0;
  padding-left: 16px;
  border-left: 2px solid #e4e4e7;
  font-style: normal;
}

/* ── Footer ────────────────────────────────────── */
.kc-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 16px;
  border-top: 1px solid #f4f4f5;
}

.kc-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #a1a1aa;
}

.kc-sep {
  color: #d4d4d8;
}

.kc-author {
  font-weight: 500;
  color: #71717a;
}

.kc-star {
  color: #a1a1aa;
}

.kc-date {
  font-size: 12px;
  color: #a1a1aa;
  font-variant-numeric: tabular-nums;
}
</style>
