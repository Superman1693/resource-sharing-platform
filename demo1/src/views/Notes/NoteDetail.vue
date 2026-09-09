<script setup>
import { ref, reactive, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import { EyeOutlined, LikeOutlined, MessageOutlined, UserOutlined, LockOutlined, TeamOutlined, RightOutlined, EditOutlined, UnorderedListOutlined, OrderedListOutlined, CodeOutlined, ClearOutlined, UndoOutlined, RedoOutlined, LinkOutlined, PictureOutlined, FontColorsOutlined, HighlightOutlined, StarOutlined, StarFilled } from '@ant-design/icons-vue'
import LikeButton from '../../components/LikeButton.vue'
import FollowButton from '../../components/FollowButton.vue'
import UserAvatar from '../../components/UserAvatar.vue'
import { useUserStore } from '../../store/userLogin'
import { getNoteDetail, addComment, replyComment, likeNote, likeComment, getCommentList, reportComment, reportNote, getNoteList, updateNote, toggleCollectNote, checkCollected, searchPublicUsers } from '../../utils/api'
import { CATEGORY_TEXT } from '../../utils/constant'
import { formatDateTime } from '../../utils/dateUtils'

// 引入Markdown解析和代码高亮
import { marked } from 'marked'
import hljs from 'highlight.js'
import DOMPurify from 'dompurify'
// 选用和语雀接近的代码高亮主题（atom-one-light，语雀默认亮色主题）
import 'highlight.js/styles/atom-one-light.css'
import { renderMarkdown } from '../../utils/markdown'

// ========== 配置marked：兼容语雀MD语法（marked v12+ 使用 extensions API） ==========
const renderer = {
  // 链接：新窗口打开
  link({ href, title, tokens }) {
    const text = this.parser.parseInline(tokens)
    const titleAttr = title ? ` title="${title}"` : ''
    return `<a href="${href}"${titleAttr} target="_blank" rel="noopener">${text}</a>`
  },
  // 代码块：语法高亮（在 renderer 阶段处理，避免被二次转义）
  code({ text, lang }) {
    let highlighted
    if (lang && hljs.getLanguage(lang)) {
      highlighted = hljs.highlight(text, { language: lang }).value
    } else if (text.trim()) {
      highlighted = hljs.highlightAuto(text).value
    } else {
      highlighted = text
    }
    return `<pre><code class="hljs language-${lang || 'auto'}">${highlighted}</code></pre>`
  }
}

marked.use({
  gfm: true,
  breaks: true,
  renderer
})

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const noteDetail = ref({
  id: null,
  title: '',
  category: '',
  contentType: '',
  authorId: null,
  content: '', // 语雀导出的MD纯文本
  author: '',
  authorAvatar: '',
  starId: null,
  locked: false,
  viewCount: 0,
  likeCount: 0,
  publishTime: '',
  coverImage: '',
})
const getCategoryLabel = (value) => CATEGORY_TEXT[value] || '未知分类'

// 跳转作者个人主页（自己的头像跳个人中心编辑页，避免被个人主页重定向回首页）
const goAuthorPage = () => {
  if (!noteDetail.value.authorId) return
  if (Number(noteDetail.value.authorId) === Number(userStore.id)) {
    router.push('/user/profile')
  } else {
    router.push(`/user/user/${noteDetail.value.authorId}`)
  }
}

// 从付费锁提示跳转到所属星球加入
const goLockedStar = () => {
  if (noteDetail.value.starId) {
    router.push(`/user/starDetail/${noteDetail.value.starId}`)
  }
}

// ========== 更多推荐（同分类热门内容） ==========
const recommendations = ref([])

const fetchRecommendations = async () => {
  try {
    const res = await getNoteList({
      category: noteDetail.value.category || undefined,
      sortType: 'hot',
      pageSize: 5,
    })
    const data = res.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const currentId = Number(route.params.id)
    recommendations.value = records.filter((n) => n.id !== currentId).slice(0, 4)
  } catch (_) {
    recommendations.value = []
  }
}

const viewRecommend = (note) => {
  router.push(`/user/noteDetail/${note.id}`)
}

// 私信作者：跳转到私信中心并直接打开与作者的会话
const handleDmAuthor = () => {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  router.push({ path: '/user/messages', query: { to: noteDetail.value.authorId } })
}

// Markdown 预览（marked v12+ 的 parse 是异步的）
const markdownHtml = ref('')

watch(
  () => noteDetail.value.content,
  async (newContent) => {
    if (!newContent) {
      markdownHtml.value = ''
      return
    }
    try {
      // 规范化连续星号：4 个及以上的 * 多为编辑器对已加粗文本重复加粗产生（如 ****答：********），
      // CommonMark 不识别，原样显示星号；统一收敛为 ** 后 marked 可正常渲染加粗
      const cleaned = String(newContent).replace(/\*{4,}/g, '**')
      markdownHtml.value = await renderMarkdown(cleaned, 'full')
    } catch (e) {
      console.error('Markdown 解析失败:', e)
      markdownHtml.value = ''
    }
  },
  { immediate: true }
)

// 评论/回复 Markdown 渲染（同步；白名单仅基础排版+链接，防止 XSS）
const renderCommentHtml = (content) => {
  if (!content) return ''
  try {
    const html = marked.parse(String(content))
    return DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'code', 'pre', 'ul', 'ol', 'li', 'blockquote', 'a'],
      ALLOWED_ATTR: ['href', 'target', 'rel', 'class'],
    })
  } catch {
    // 渲染异常时退化为 HTML 转义纯文本，避免 v-html 注入风险
    return String(content).replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]))
  }
}

// ========== 以下评论/点赞/加载逻辑完全保留，无修改 ==========
const noteLiked = ref(false)
const collected = ref(false)
const comments = ref([])
const commentForm = reactive({
  content: '',
  replyTo: null,
})
const submitting = ref(false)

const handleComment = async () => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再评论！')
    router.push('/login')
    return
  }
  const content = commentForm.content.trim()
  if (!content) {
    antMessage.warning('请输入评论内容！')
    return
  }
  if (content.length > 500) {
    antMessage.warning('评论内容不能超过500字符！')
    return
  }

  submitting.value = true
  try {
    const noteId = Number(route.params.id)
    const mentionedUserIdsArr = Array.from(mentionedUserIds.value)
    if (commentForm.replyTo) {
      const res = await replyComment(Number(commentForm.replyTo), { noteId, content, mentionedUserIds: mentionedUserIdsArr })
      if (res.code === 0) {
        antMessage.success('回复成功！')
        await fetchComments()
      }
    } else {
      const res = await addComment({ noteId, content, mentionedUserIds: mentionedUserIdsArr })
      if (res.code === 0) {
        antMessage.success('评论成功！')
        await fetchComments()
      }
    }
    commentForm.content = ''
    commentForm.replyTo = null
    mentionedUserIds.value = new Set()
    closeMention()
  } catch (err) {
    const errorMsg = err?.response?.data?.description || err?.message || '操作失败，请重试！'
    antMessage.error(errorMsg)
  } finally {
    submitting.value = false
  }
}

const handleReply = (comment) => {
  commentForm.replyTo = comment.id
  commentForm.content = `@${comment.username} `
  setTimeout(() => {
    const textarea = document.querySelector('textarea')
    textarea?.focus()
    textarea.selectionStart = textarea.selectionEnd = commentForm.content.length
  }, 0)
}

// ===== @提及：评论框输入 @ 触发用户联想 =====
const mentionedUserIds = ref(new Set())
const mentionUsers = ref([])
const mentionVisible = ref(false)
const mentionActiveIdx = ref(0)
let mentionTimer = null
let mentionContext = null // { start, query } 当前 @ 的位置与查询词

const closeMention = () => {
  mentionVisible.value = false
  mentionUsers.value = []
  mentionActiveIdx.value = 0
  mentionContext = null
}

// 输入时检测光标前最近的 @xxx，触发用户联想
const onCommentInput = (e) => {
  const ta = e.target
  const pos = ta.selectionStart ?? (ta.value?.length || 0)
  const before = (ta.value || '').slice(0, pos)
  const m = before.match(/@([^\s@]{1,20})$/)
  if (!m) { closeMention(); return }
  const query = m[1]
  mentionContext = { start: pos - m[0].length, query }
  clearTimeout(mentionTimer)
  mentionTimer = setTimeout(async () => {
    try {
      const res = await searchPublicUsers({ keyword: query })
      mentionUsers.value = (res.data || []).filter(u => !mentionedUserIds.value.has(u.id))
      mentionActiveIdx.value = 0
      mentionVisible.value = mentionUsers.value.length > 0
    } catch (_) { mentionVisible.value = false }
  }, 250)
}

// 选中联想用户：把 @query 替换为 @username ，记录 userId
const selectMention = (user) => {
  if (!mentionContext) { closeMention(); return }
  const val = commentForm.content
  const before = val.slice(0, mentionContext.start)
  const after = val.slice(mentionContext.start + mentionContext.query.length + 1) // +1 跳过 @
  const insert = `@${user.username} `
  commentForm.content = before + insert + after
  mentionedUserIds.value.add(user.id)
  closeMention()
  setTimeout(() => {
    const t = document.querySelector('textarea')
    if (t) {
      const caret = (before + insert).length
      t.focus()
      t.selectionStart = t.selectionEnd = caret
    }
  }, 0)
}

// 浮层键盘导航：上下选、回车/Tab 确认、Esc 关闭
const onMentionKeydown = (e) => {
  if (!mentionVisible.value) return
  if (e.key === 'ArrowDown') { e.preventDefault(); mentionActiveIdx.value = (mentionActiveIdx.value + 1) % mentionUsers.value.length }
  else if (e.key === 'ArrowUp') { e.preventDefault(); mentionActiveIdx.value = (mentionActiveIdx.value - 1 + mentionUsers.value.length) % mentionUsers.value.length }
  else if (e.key === 'Enter' || e.key === 'Tab') {
    const u = mentionUsers.value[mentionActiveIdx.value]
    if (u) { e.preventDefault(); selectMention(u) }
  } else if (e.key === 'Escape') { closeMention() }
}

const handleNoteLike = async () => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再点赞！')
    router.push('/login')
    return
  }
  if (noteLiked.value) {
    antMessage.info('您已点赞过该笔记')
    return
  }
  try {
    const res = await likeNote(Number(noteDetail.value.id))
    if (res.code === 0) {
      noteDetail.value.likeCount = res.data?.likeCount ?? (noteDetail.value.likeCount || 0) + 1
      noteLiked.value = true
      antMessage.success('点赞成功！')
    }
  } catch (err) {
    // 业务异常：已点赞
    if (err.isBusinessError && err.code === 40000 && err.description?.includes('已点赞')) {
      noteLiked.value = true
      antMessage.info('您已点赞过该笔记')
    } else {
      antMessage.error(err.description || err.message || '点赞失败，请重试！')
    }
  }
}

// 收藏/取消收藏（toggle）
const handleCollect = async () => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再收藏！')
    router.push('/login')
    return
  }
  try {
    const res = await toggleCollectNote(Number(noteDetail.value.id))
    if (res.code === 0) {
      collected.value = res.data?.collected ?? false
      antMessage.success(collected.value ? '已收藏' : '已取消收藏')
    }
  } catch (err) {
    antMessage.error(err?.description || err?.message || '收藏操作失败')
  }
}

const handleLike = async (comment) => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再点赞！')
    router.push('/login')
    return
  }
  if (comment._liked) {
    antMessage.info('您已点赞过该评论')
    return
  }
  try {
    const res = await likeComment(Number(comment.id))
    if (res.code === 0) {
      comment.likeCount = res.data?.likeCount ?? (comment.likeCount || 0) + 1
      comment._liked = true
      antMessage.success('点赞成功！')
    }
  } catch (err) {
    if (err.isBusinessError && err.code === 40000 && err.description?.includes('已点赞')) {
      comment._liked = true
      antMessage.info('您已点赞过该评论')
    } else {
      antMessage.error(err.description || err.message || '点赞失败，请重试！')
    }
  }
}

const handleReportNote = () => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再举报！')
    router.push('/login')
    return
  }
  Modal.confirm({
    title: '举报内容',
    content: '确认将该笔记提交审核吗？',
    okType: 'danger',
    onOk: async () => {
      try {
        const res = await reportNote(Number(noteDetail.value.id))
        if (res.code === 0) {
          antMessage.success('已提交举报，内容将进入审核')
        } else {
          antMessage.error('举报失败')
        }
      } catch (err) {
        antMessage.error(err?.description || err?.message || '举报失败')
      }
    },
  })
}

const handleReportComment = (comment) => {
  if (!userStore.isLogin) {
    antMessage.warning('请先登录后再举报！')
    router.push('/login')
    return
  }
  Modal.confirm({
    title: '举报评论',
    content: `确认举报这条评论吗？`,
    okType: 'danger',
    onOk: async () => {
      try {
        const res = await reportComment({ id: Number(comment.id), reason: '用户举报' })
        if (res.code === 0) {
          antMessage.success('已提交举报，评论将进入审核')
          await fetchComments()
        } else {
          antMessage.error('举报失败')
        }
      } catch (err) {
        antMessage.error(err?.description || err?.message || '举报失败')
      }
    },
  })
}

const fetchNoteDetail = async () => {
  loading.value = true
  try {
    const id = Number(route.params.id)
    const res = await getNoteDetail(id)
    if (res.code === 0 && res.data) {
      noteDetail.value = res.data
      // 已登录用户初始化收藏态
      if (userStore.isLogin) {
        try {
          const cRes = await checkCollected(Number(res.data.id))
          if (cRes.code === 0) collected.value = cRes.data?.collected ?? false
        } catch (_) { /* 未登录或接口失败，忽略 */ }
      }
    }
    await fetchComments()
    fetchRecommendations()
  } catch (err) {
    if (err?.code === 40100 || err?.response?.data?.code === 40100) {
      antMessage.warning('未登录或登录已过期，请先登录！')
      router.push('/login')
    } else {
      const errorMsg = err?.description || err?.response?.data?.description || '加载笔记详情失败，请重试！'
      antMessage.error(errorMsg)
    }
  } finally {
    loading.value = false
  }
}

const fetchComments = async () => {
  try {
    const res = await getCommentList({ 
      noteId: Number(route.params.id),
      page: 1,
      pageSize: 20
    })
    if (res.code === 0 && res.data) {
      comments.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (err) {
    console.error('加载评论失败', err)
    antMessage.error('加载评论失败，请刷新页面！')
  }
}

const formatTime = formatDateTime

// 当前用户是否可编辑此笔记（作者本人）
const canEdit = computed(() =>
  userStore.isLogin && noteDetail.value.authorId != null
  && Number(noteDetail.value.authorId) === Number(userStore.id)
)

// 内联编辑：作者双击正文或点编辑按钮，直接在已渲染的原文上所见即所得地编辑
// 不进入 Vditor 编辑器，编辑容器 = 渲染后的 HTML（contenteditable），保存时用 turndown 转回 Markdown
// turndown 按需动态加载，避免详情页首屏加载该库
let turndownService = null
const getTurndownService = async () => {
  if (!turndownService) {
    const { default: TurndownService } = await import('turndown')
    turndownService = new TurndownService({
      headingStyle: 'atx',       // 标题用 # 风格
      codeBlockStyle: 'fenced',  // 代码块用 ``` 风格（pre>code 的 textContent 会自动剥离高亮 span，还原纯代码）
      bulletListMarker: '-',     // 无序列表用 -
      emDelimiter: '*',
      strongDelimiter: '**',
    })
    // 颜色/背景色/下划线 Markdown 无原生语法，统一保留为 <span style="...">（marked full 预设已允许 span+style）
    // Chrome execCommand('foreColor') 产生 <span style="color:...">，原样保留
    turndownService.addRule('styledSpan', {
      filter: (node) => node.nodeName === 'SPAN' && /color/i.test(node.getAttribute('style') || ''),
      replacement: (content, node) => `<span style="${node.getAttribute('style')}">${content}</span>`,
    })
    // Firefox foreColor 可能产生 <font color>，统一转 span style
    turndownService.addRule('fontColor', {
      filter: (node) => node.nodeName === 'FONT' && node.getAttribute('color'),
      replacement: (content, node) => `<span style="color: ${node.getAttribute('color')};">${content}</span>`,
    })
    // 下划线 <u> 转 span style（DOMPurify full 预设未放行 <u>，但放行 span+style）
    turndownService.addRule('underline', {
      filter: 'u',
      replacement: (content) => `<span style="text-decoration: underline;">${content}</span>`,
    })
    // 工具栏「代码」按钮 (formatBlock PRE) 产生裸 <pre>（无 <code> 子节点），
    // 默认 fenced 规则只认 pre>code，补一条把裸 pre 也转成 ``` 围栏代码块
    turndownService.addRule('preWithoutCode', {
      filter: (node) => node.nodeName === 'PRE' && !(node.firstChild && node.firstChild.nodeName === 'CODE'),
      replacement: (_content, node) => '\n\n```\n' + node.textContent + '\n```\n\n',
    })
  }
  return turndownService
}
const editing = ref(false)
const editableRef = ref(null)
const savingEdit = ref(false)

// 富文本格式化命令：document.execCommand 对当前选区执行（浏览器原生支持，兼容性好）
// 配合 @mousedown.prevent 调用，避免点击按钮夺焦导致选区丢失
const exec = (cmd, value = null) => {
  editableRef.value?.focus()
  document.execCommand(cmd, false, value)
}

// 预设颜色色板：点击即应用，不夺焦，选区保留（比 color picker 更快）
const fgColors = ['#e74c3c', '#e67e22', '#f1c40f', '#2ecc71', '#3498db', '#9b59b6', '#1abc9c', '#000000']
const bgColors = ['#ffcccc', '#ffe0b3', '#fff5b3', '#c6f7c6', '#b3d9ff', '#e0ccff', '#b3f0ee', 'transparent']

// 插入链接 / 图片：弹窗输入地址
const insertLink = () => {
  const url = window.prompt('请输入链接地址：', 'https://')
  if (url) exec('createLink', url)
}
const insertImage = () => {
  const url = window.prompt('请输入图片地址：', 'https://')
  if (url) exec('insertImage', url)
}

// 快捷键：Ctrl+B 加粗 / Ctrl+I 斜体 / Ctrl+U 下划线 / Ctrl+Shift+S 删除线 / Ctrl+K 链接
const onEditKeydown = (e) => {
  if (!(e.ctrlKey || e.metaKey)) return
  const key = e.key.toLowerCase()
  if (key === 'b') { e.preventDefault(); exec('bold') }
  else if (key === 'i') { e.preventDefault(); exec('italic') }
  else if (key === 'u') { e.preventDefault(); exec('underline') }
  else if (key === 's' && e.shiftKey) { e.preventDefault(); exec('strikeThrough') }
  else if (key === 'k') { e.preventDefault(); insertLink() }
}

// 记录双击所在元素的 DOM 路径，用于进入编辑后把光标与滚动定位回双击处（避免跳到内容开头）
const editAnchor = ref(null)
const getNodePath = (target, root) => {
  const path = []
  let el = target
  while (el && el !== root && el.parentElement) {
    path.unshift(Array.prototype.indexOf.call(el.parentElement.children, el))
    el = el.parentElement
  }
  return path
}
const findNodeByPath = (root, path) => {
  let el = root
  for (const idx of path) {
    if (!el || !el.children || idx < 0 || idx >= el.children.length) return null
    el = el.children[idx]
  }
  return el && el !== root ? el : null
}
// 还原编辑锚点：滚动到双击处并把光标放到该元素开头
const restoreEditAnchor = () => {
  const root = editableRef.value
  const anchor = editAnchor.value
  if (!root || !anchor || !anchor.length) return
  const target = findNodeByPath(root, anchor)
  if (!target) return
  try {
    target.scrollIntoView({ block: 'center', behavior: 'auto' })
    const range = document.createRange()
    range.selectNodeContents(target)
    range.collapse(true)
    const sel = window.getSelection()
    sel.removeAllRanges()
    sel.addRange(range)
  } catch (_) { /* 定位失败不影响编辑 */ }
}
// 双击内容进入编辑：先记录双击位置，进入编辑后在原地恢复光标
const onContentDblClick = async (e) => {
  if (!canEdit.value) return
  editAnchor.value = getNodePath(e.target, e.currentTarget)
  await startEdit()
  editAnchor.value = null // 用完即清，避免下次点"编辑"按钮时定位到旧的 双击位置
}
const startEdit = async () => {
  if (!canEdit.value) return
  editing.value = true
  await nextTick()
  // 以当前已渲染的 HTML 作为编辑基础：直接在原文上修改，所见即所得
  if (editableRef.value) {
    editableRef.value.innerHTML = markdownHtml.value || ''
    // 让 execCommand 优先用 CSS style（颜色/背景存为 span style），而非 <font>
    try { document.execCommand('styleWithCSS', false, true) } catch (_) { /* 部分浏览器不支持，忽略 */ }
    editableRef.value.focus()
    // 把光标与滚动定位回双击处，避免进入编辑后视觉跳到内容开头
    restoreEditAnchor()
  }
}

const cancelEdit = () => {
  editing.value = false
}

const saveEdit = async () => {
  if (!noteDetail.value.id || !editableRef.value) return
  if (savingEdit.value) return // 防双击重入
  savingEdit.value = true
  try {
    // contenteditable 空内容时浏览器可能残留 <br>，turndown 转换后需对空白做校验
    const html = editableRef.value.innerHTML
    const td = await getTurndownService()
    const markdown = td.turndown(html).trim()
    if (markdown.length < 10) {
      antMessage.warning('内容至少 10 个字符')
      return
    }
    await updateNote(noteDetail.value.id, { content: markdown })
    antMessage.success('已保存')
    editing.value = false
    await fetchNoteDetail()
  } catch (err) {
    antMessage.error(err?.description || err?.message || '保存失败')
  } finally {
    savingEdit.value = false
  }
}

onMounted(() => {
  fetchNoteDetail()
})
</script>

<template>
  <div class="note-detail-page">
    <a-card :loading="loading" :bordered="false" class="yuque-note-card">
      <div class="note-header">
        <h1 class="note-title">{{ noteDetail.title }}</h1>
        <!-- 作者信息栏：头像可点击跳转个人主页，关注/私信按钮样式统一 -->
        <div class="author-bar">
          <div class="author-left" @click="goAuthorPage">
            <UserAvatar :src="noteDetail.authorAvatar" :size="44" class="author-avatar" />
            <div class="author-info">
              <span class="author-name">{{ noteDetail.author || '未知用户' }}</span>
              <span class="author-sub">
                {{ formatTime(noteDetail.publishTime) }} · <EyeOutlined /> {{ noteDetail.viewCount || 0 }} 阅读
              </span>
            </div>
          </div>
          <div class="author-actions">
            <LikeButton :liked="noteLiked" :count="noteDetail.likeCount || 0" @toggle="handleNoteLike" />
            <a-button v-if="userStore.isLogin" size="small" class="author-action-btn" :class="{ 'collect-active': collected }" @click="handleCollect">
              <StarFilled v-if="collected" />
              <StarOutlined v-else />
              {{ collected ? '已收藏' : '收藏' }}
            </a-button>
            <a-button v-if="canEdit" size="small" class="author-action-btn" @click="startEdit">
              <EditOutlined /> 编辑
            </a-button>
            <template v-if="userStore.isLogin && noteDetail.authorId && noteDetail.authorId !== userStore.id">
              <FollowButton
                :userId="noteDetail.authorId"
                :initialFollowing="false"
                class="author-action-btn"
              />
              <a-button size="small" class="dm-author-btn author-action-btn" @click="handleDmAuthor">
                <MessageOutlined /> 私信
              </a-button>
            </template>
            <a class="stat-item report-btn" @click="handleReportNote">举报</a>
          </div>
        </div>
      </div>
      <!-- 封面图：语雀风格适配 -->
      <div v-if="noteDetail.coverImage" class="note-cover">
        <img :src="noteDetail.coverImage" alt="笔记封面" />
      </div>
      <!-- 付费星球内容锁：未加入星球时提示解锁 -->
      <div v-if="noteDetail.locked" class="star-lock-banner">
        <LockOutlined class="lock-icon" />
        <div class="lock-text">
          <p class="lock-title">本内容为付费星球专属内容</p>
          <p class="lock-desc">加入星球后即可阅读全文，并享受星球内全部内容</p>
        </div>
        <a-button type="primary" @click="goLockedStar">
          <TeamOutlined /> 去加入星球
        </a-button>
      </div>
      <!-- 核心：语雀风格MD展示容器 -->
      <div v-else>
        <div v-if="!editing" class="yuque-markdown-body" v-html="markdownHtml" @dblclick="onContentDblClick" :title="canEdit ? '双击进入编辑 · 编辑中再次双击可保存' : ''"></div>
        <div v-else class="inline-edit-wrap">
          <!-- 左侧浮动工具栏：文本/段落格式（仅编辑时出现） -->
          <div class="rt-toolbar rt-toolbar-left">
            <button class="rt-btn" title="加粗 (Ctrl+B)" @mousedown.prevent="exec('bold')"><span class="rt-glyph rt-bold">B</span></button>
            <button class="rt-btn" title="斜体 (Ctrl+I)" @mousedown.prevent="exec('italic')"><span class="rt-glyph rt-italic">I</span></button>
            <button class="rt-btn" title="下划线 (Ctrl+U)" @mousedown.prevent="exec('underline')"><span class="rt-glyph rt-underline">U</span></button>
            <button class="rt-btn" title="删除线 (Ctrl+Shift+S)" @mousedown.prevent="exec('strikeThrough')"><span class="rt-glyph rt-strike">S</span></button>
            <div class="rt-sep"></div>
            <button class="rt-btn" title="一级标题" @mousedown.prevent="exec('formatBlock','H1')">H1</button>
            <button class="rt-btn" title="二级标题" @mousedown.prevent="exec('formatBlock','H2')">H2</button>
            <button class="rt-btn" title="三级标题" @mousedown.prevent="exec('formatBlock','H3')">H3</button>
            <button class="rt-btn rt-btn-text" title="正文段落" @mousedown.prevent="exec('formatBlock','P')">正文</button>
            <div class="rt-sep"></div>
            <button class="rt-btn" title="无序列表" @mousedown.prevent="exec('insertUnorderedList')"><UnorderedListOutlined /></button>
            <button class="rt-btn" title="有序列表" @mousedown.prevent="exec('insertOrderedList')"><OrderedListOutlined /></button>
            <button class="rt-btn rt-btn-text" title="引用" @mousedown.prevent="exec('formatBlock','BLOCKQUOTE')">引用</button>
            <button class="rt-btn" title="代码块" @mousedown.prevent="exec('formatBlock','PRE')"><CodeOutlined /></button>
            <div class="rt-sep"></div>
            <button class="rt-btn" title="清除格式" @mousedown.prevent="exec('removeFormat')"><ClearOutlined /></button>
            <button class="rt-btn" title="撤销 (Ctrl+Z)" @mousedown.prevent="exec('undo')"><UndoOutlined /></button>
            <button class="rt-btn" title="重做 (Ctrl+Y)" @mousedown.prevent="exec('redo')"><RedoOutlined /></button>
          </div>
          <!-- 右侧浮动工具栏：颜色与插入（仅编辑时出现） -->
          <div class="rt-toolbar rt-toolbar-right">
            <div class="rt-palette">
              <span class="rt-palette-label"><FontColorsOutlined /> 文字色</span>
              <div class="rt-swatches">
                <button v-for="c in fgColors" :key="'fg'+c" class="rt-swatch"
                  :style="{ background: c }" :title="c" @mousedown.prevent="exec('foreColor', c)"></button>
              </div>
            </div>
            <div class="rt-sep"></div>
            <div class="rt-palette">
              <span class="rt-palette-label"><HighlightOutlined /> 背景色</span>
              <div class="rt-swatches">
                <button v-for="c in bgColors" :key="'bg'+c" class="rt-swatch"
                  :style="{ background: c }" :title="c" @mousedown.prevent="exec('hiliteColor', c)"></button>
              </div>
            </div>
            <div class="rt-sep"></div>
            <button class="rt-btn" title="插入链接 (Ctrl+K)" @mousedown.prevent="insertLink"><LinkOutlined /></button>
            <button class="rt-btn" title="插入图片" @mousedown.prevent="insertImage"><PictureOutlined /></button>
          </div>

          <!-- 直接在渲染后的原文上编辑：contenteditable 容器，所见即所得，保存时 turndown 转回 Markdown -->
          <div
            ref="editableRef"
            class="yuque-markdown-body inline-editable"
            contenteditable="true"
            spellcheck="false"
            @keydown="onEditKeydown"
            @dblclick="saveEdit"
            title="再次双击即可保存"
            data-placeholder="编辑笔记内容...（左侧文本格式 / 右侧颜色插入，或用 Ctrl+B/I/U/K 快捷键；再次双击可保存）"
          ></div>
          <div class="inline-edit-actions">
            <span class="edit-tip">提示：再次双击内容可快速保存</span>
            <a-space>
              <a-button @click="cancelEdit">取消</a-button>
              <a-button type="primary" :loading="savingEdit" @click="saveEdit">保存</a-button>
            </a-space>
          </div>
        </div>
      </div>
    </a-card>

    <!-- 更多推荐：同分类热门内容 -->
    <a-card
      v-if="recommendations.length > 0"
      title="更多推荐"
      :bordered="false"
      style="margin-top: 24px;"
      class="recommend-card"
    >
      <div class="recommend-list">
        <div
          v-for="item in recommendations"
          :key="item.id"
          class="recommend-item"
          @click="viewRecommend(item)"
        >
          <div class="recommend-main">
            <span class="recommend-title">{{ item.title }}</span>
            <span class="recommend-meta">
              {{ item.author }} · <EyeOutlined /> {{ item.viewCount || 0 }} · <LikeOutlined /> {{ item.likeCount || 0 }}
            </span>
          </div>
          <RightOutlined class="recommend-arrow" />
        </div>
      </div>
    </a-card>

    <!-- 评论区：保留原有布局，适配语雀整体风格 -->
    <a-card title="评论区" :bordered="false" style="margin-top: 24px;" class="comment-card">
      <template v-if="userStore.isLogin">
        <a-form :model="commentForm" layout="vertical">
          <a-form-item class="comment-input-wrap">
            <a-textarea
              v-model:value="commentForm.content"
              :rows="4"
              placeholder="写下你的想法...（输入 @ 可提及他人）"
              :max-length="500"
              show-count
              class="comment-textarea"
              @input="onCommentInput"
              @keydown="onMentionKeydown"
              @blur="() => setTimeout(closeMention, 200)"
            />
            <div v-if="mentionVisible" class="mention-dropdown">
              <div
                v-for="(u, i) in mentionUsers"
                :key="u.id"
                class="mention-item"
                :class="{ 'mention-active': i === mentionActiveIdx }"
                @mousedown.prevent="selectMention(u)"
                @mouseenter="mentionActiveIdx = i"
              >
                <span class="mention-avatar">{{ (u.username || '?').charAt(0).toUpperCase() }}</span>
                <span class="mention-name">{{ u.username }}</span>
              </div>
            </div>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="submitting" @click="handleComment" class="submit-btn">
              {{ commentForm.replyTo ? '回复评论' : '发表评论' }}
            </a-button>
            <a-button v-if="commentForm.replyTo" style="margin-left: 8px" @click="() => { commentForm.replyTo = null; commentForm.content = '' }">
              取消回复
            </a-button>
          </a-form-item>
        </a-form>
        <a-divider />
      </template>
      <div v-else class="login-prompt">
        <p>登录后即可发表评论、点赞和回复</p>
        <a-button type="primary" @click="router.push('/login')">去登录</a-button>
      </div>

      <div v-if="comments.length === 0" class="empty-comment">
        <p>暂无评论，快来抢沙发吧～</p>
      </div>

      <div v-else class="comment-list">
        <div v-for="item in comments" :key="item.id" class="comment-item">
          <div class="comment-head">
            <UserAvatar :src="item.avatar" class="comment-avatar" />
            <div class="comment-info">
              <span :class="{ 'author-name': item.isAuthor }">
                {{ item.username || '匿名用户' }}
                <a-tag v-if="item.isAuthor" color="gold" size="small" style="margin-left: 8px">作者</a-tag>
              </span>
              <span class="comment-time">{{ formatTime(item.createTimeStr || item.createTime) }}</span>
            </div>
          </div>
          <div class="comment-content" :class="{ 'author-comment': item.isAuthor }" v-html="renderCommentHtml(item.content)"></div>
          <div class="comment-actions" v-if="userStore.isLogin">
            <LikeButton :liked="item._liked" :count="item.likeCount || 0" @toggle="handleLike(item)" />
            <a @click="handleReply(item)" class="comment-action">回复</a>
            <a @click="handleReportComment(item)" class="comment-action report-action">举报</a>
          </div>

          <div v-if="item.replies && item.replies.length > 0" class="replies-list">
            <div v-for="reply in item.replies" :key="reply.id" class="reply-item">
              <div class="comment-head">
                <UserAvatar :src="reply.avatar" :size="24" class="comment-avatar" />
                <div class="comment-info">
                  <span :class="{ 'author-name': reply.isAuthor }">
                    {{ reply.username || '匿名用户' }}
                    <a-tag v-if="reply.isAuthor" color="gold" size="small" style="margin-left: 8px">作者</a-tag>
                  </span>
                  <span class="comment-time">{{ formatTime(reply.createTimeStr || reply.createTime) }}</span>
                </div>
              </div>
              <div class="comment-content reply-content" :class="{ 'author-comment': reply.isAuthor }" v-html="renderCommentHtml(reply.content)"></div>
              <div class="comment-actions reply-actions" v-if="userStore.isLogin">
                <LikeButton :liked="reply._liked" :count="reply.likeCount || 0" @toggle="handleLike(reply)" />
                <a @click="handleReply(reply)" class="comment-action">回复</a>
                <a @click="handleReportComment(reply)" class="comment-action report-action">举报</a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
/* 全局页面容器：匹配语雀的页面宽度、边距 */
.note-detail-page {
  padding: 24px 16px;
  max-width: 960px;
  margin: 0 auto;
  background-color: var(--color-bg);
  min-height: 100vh;
  animation: slideUpFade 0.4s var(--ease-out);
}

@keyframes slideUpFade {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 笔记卡片：语雀无边框、白背景、圆角 */
.yuque-note-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: 40px 60px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-light);
  transition: box-shadow var(--duration-normal) var(--ease-out);
}

/* 笔记标题：语雀风格（字体、大小、间距） */
.note-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.3;
  margin-bottom: 24px;
  text-align: left;
  letter-spacing: -0.02em;
}

/* 笔记元信息：分类、作者、时间、统计 */
.note-meta {
  display: flex;
  gap: 16px;
  align-items: center;
  color: var(--color-text-muted);
  font-size: 0.875rem;
  flex-wrap: wrap;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 32px;
}

/* ===== 作者信息栏 ===== */
.author-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 32px;
}
.author-left {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  min-width: 0;
}
.author-avatar {
  flex-shrink: 0;
  transition: box-shadow var(--duration-fast);
}
.author-left:hover .author-avatar {
  box-shadow: 0 0 0 3px var(--color-accent-glow);
}
.author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.author-name {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-text);
  transition: color var(--duration-fast);
}
.author-left:hover .author-name {
  color: var(--color-accent);
}
.author-sub {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.author-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
/* 关注/私信按钮统一为 32px 胶囊样式 */
.author-action-btn {
  height: 32px;
  border-radius: 999px;
  padding: 0 20px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 0.8125rem;
}
.dm-author-btn.author-action-btn {
  background: var(--color-accent, #6366f1);
  border: 1.5px solid var(--color-accent, #6366f1);
  color: #fff;
  min-width: 80px;
}
.dm-author-btn.author-action-btn:hover {
  background: var(--color-accent-light, #818cf8);
  border-color: var(--color-accent-light, #818cf8);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  transform: translateY(-1px);
}

/* ===== 付费星球内容锁 ===== */
.star-lock-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 28px 32px;
  background: linear-gradient(135deg, var(--color-accent-glow, rgba(99,102,241,0.08)), rgba(22, 163, 74, 0.06));
  border: 1px dashed var(--color-accent, #6366f1);
  border-radius: var(--radius-lg, 12px);
}
.lock-icon {
  font-size: 32px;
  color: var(--color-accent, #6366f1);
  flex-shrink: 0;
}
.lock-text {
  flex: 1;
  min-width: 0;
}
.lock-title {
  margin: 0 0 4px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text);
}
.lock-desc {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

/* ===== 更多推荐 ===== */
.recommend-card :deep(.ant-card-head-title) {
  font-weight: 600;
}
.recommend-list {
  display: flex;
  flex-direction: column;
}
.recommend-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 8px;
  border-bottom: 1px solid var(--color-border-light, #f0f0f0);
  cursor: pointer;
  transition: background var(--duration-fast);
  border-radius: 6px;
}
.recommend-item:last-child {
  border-bottom: none;
}
.recommend-item:hover {
  background: var(--color-accent-glow, rgba(99, 102, 241, 0.04));
}
.recommend-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.recommend-title {
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recommend-item:hover .recommend-title {
  color: var(--color-accent);
}
.recommend-meta {
  font-size: 0.75rem;
  color: var(--color-text-muted);
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.recommend-arrow {
  color: var(--color-text-muted);
  font-size: 12px;
  flex-shrink: 0;
}
.category-tag {
  background-color: var(--color-accent-glow) !important;
  color: var(--color-accent) !important;
  border: none;
  font-weight: 600;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color var(--duration-fast);
}
.stat-item.like-btn:hover {
  color: var(--color-accent);
}
.stat-item.liked {
  color: var(--color-accent);
}
.report-btn {
  font-size: 0.8125rem;
  color: var(--color-text-muted);
  transition: color var(--duration-fast);
}
.report-btn:hover {
  color: var(--color-error);
}
.follow-author-btn {
  margin-left: 4px;
  transform: scale(0.85);
  transform-origin: left center;
}

.dm-author-btn {
  margin-left: 4px;
  transform: scale(0.85);
  transform-origin: left center;
}

/* 封面图：语雀风格，圆角、居中 */
.note-cover {
  text-align: center;
  margin-bottom: 32px;
}
.note-cover img {
  max-width: 100%;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  transition: transform var(--duration-normal) var(--ease-out);
}
.note-cover img:hover {
  transform: scale(1.01);
}

/* ========== 核心：语雀Markdown原生样式（1:1复刻） ========== */
:deep(.yuque-markdown-body) {
  /* 统一使用全局正文字体栈（含中英文回退），避免与外围组件字体割裂 */
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.75; /* 语雀核心行高1.75，比1.8更贴合 */
  color: #1d2129;
  word-wrap: break-word;
}
/* 语雀MD：标题统一使用正文字体（无衬线），避免同容器内标题与段落字体跳变 */
:deep(.yuque-markdown-body h1),
:deep(.yuque-markdown-body h2),
:deep(.yuque-markdown-body h3),
:deep(.yuque-markdown-body h4),
:deep(.yuque-markdown-body h5),
:deep(.yuque-markdown-body h6) {
  font-family: var(--font-body);
}

/* 语雀MD：标题样式（h1-h6，和语雀完全一致的大小、间距、颜色） */
:deep(.yuque-markdown-body h1) {
  font-size: 24px;
  font-weight: 700;
  color: #1d2129;
  margin: 32px 0 16px;
  line-height: 1.4;
}
:deep(.yuque-markdown-body h2) {
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
  margin: 28px 0 12px;
  line-height: 1.4;
  padding-bottom: 8px;
  border-bottom: 1px solid #eaecef;
}
:deep(.yuque-markdown-body h3) {
  font-size: 18px;
  font-weight: 700;
  color: #1d2129;
  margin: 24px 0 10px;
  line-height: 1.4;
}
:deep(.yuque-markdown-body h4),
:deep(.yuque-markdown-body h5),
:deep(.yuque-markdown-body h6) {
  font-size: 16px;
  font-weight: 700;
  color: #1d2129;
  margin: 20px 0 8px;
  line-height: 1.4;
}

/* 语雀MD：段落样式 */
:deep(.yuque-markdown-body p) {
  margin: 0 0 16px;
}

/* 语雀MD：列表样式（有序/无序，和语雀一致的缩进、间距） */
:deep(.yuque-markdown-body ul),
:deep(.yuque-markdown-body ol) {
  padding-left: 24px;
  margin: 0 0 16px;
}
:deep(.yuque-markdown-body ul) {
  list-style-type: disc;
}
:deep(.yuque-markdown-body ol) {
  list-style-type: decimal;
}
:deep(.yuque-markdown-body li) {
  margin: 4px 0;
  line-height: 1.75;
}
:deep(.yuque-markdown-body li > ul),
:deep(.yuque-markdown-body li > ol) {
  margin: 4px 0 0;
}

/* 语雀MD：代码块样式（1:1复刻语雀的代码块、行内代码） */
:deep(.yuque-markdown-body code) {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.9em;
  padding: 0.2em 0.4em;
  margin: 0;
  background-color: #f7f8fa;
  border-radius: 3px;
  color: #f53f3f; /* 语雀行内代码红色，和原版一致 */
}
/* 语雀代码块：圆角、内边距、阴影、行高，匹配语雀 */
:deep(.yuque-markdown-body pre) {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 14px;
  line-height: 1.5;
  padding: 16px;
  margin: 0 0 16px;
  background-color: #f7f8fa;
  border-radius: 6px;
  border: 1px solid #eaecef;
  overflow-x: auto;
  color: #1d2129;
}
:deep(.yuque-markdown-body pre > code) {
  padding: 0;
  margin: 0;
  background-color: transparent;
  border-radius: 0;
  color: inherit;
  font-size: inherit;
}

/* 语雀MD：链接样式（蓝色、无下划线，hover下划线） */
:deep(.yuque-markdown-body a) {
  color: var(--color-accent);
  text-decoration: none;
  background-color: transparent;
}
:deep(.yuque-markdown-body a:hover) {
  text-decoration: underline;
}

/* 语雀MD：引用样式（语雀的浅蓝背景、左侧边框） */
:deep(.yuque-markdown-body blockquote) {
  margin: 0 0 16px;
  padding: 0 16px;
  border-left: 4px solid #4285f4;
  color: #86909c;
  background-color: #f7f8fa;
  border-radius: 0 3px 3px 0;
}
:deep(.yuque-markdown-body blockquote p) {
  margin: 8px 0;
}

/* 语雀MD：表格样式（1:1复刻语雀的表格边框、背景） */
:deep(.yuque-markdown-body table) {
  width: 100%;
  margin: 0 0 16px;
  border-collapse: collapse;
  border-spacing: 0;
  font-size: 14px;
}
:deep(.yuque-markdown-body th) {
  font-weight: 700;
  padding: 12px 8px;
  border: 1px solid #eaecef;
  background-color: #f7f8fa;
  text-align: left;
}
:deep(.yuque-markdown-body td) {
  padding: 12px 8px;
  border: 1px solid #eaecef;
  text-align: left;
}

/* 语雀MD：图片样式（居中、圆角、最大宽度） */
:deep(.yuque-markdown-body img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 8px 0;
  display: block;
  margin-left: auto;
  margin-right: auto;
}

/* 语雀MD：水平线样式 */
:deep(.yuque-markdown-body hr) {
  border: 0;
  height: 1px;
  background-color: #eaecef;
  margin: 32px 0;
}

/* ========== 评论区：适配语雀整体风格，轻微优化 ========== */
.comment-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-lg);
  padding: 32px 40px;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border-light);
}
.comment-textarea {
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  transition: all var(--duration-fast) var(--ease-in-out);
}

/* @提及浮层 */
.comment-input-wrap { position: relative; }
.mention-dropdown {
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  margin-top: 4px;
  background: #fff;
  border: 1px solid var(--color-border-light, #f1f5f9);
  border-radius: var(--radius-md, 8px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
  max-height: 220px;
  overflow-y: auto;
  z-index: 50;
}
.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  transition: background var(--duration-fast);
}
.mention-item:hover, .mention-item.mention-active {
  background: var(--color-accent-soft, rgba(99, 102, 241, 0.08));
}
.mention-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-glow, rgba(99, 102, 241, 0.12));
  color: var(--color-accent, #6366f1);
  font-size: 0.75rem;
  font-weight: 600;
  flex-shrink: 0;
}
.mention-name { font-size: 0.875rem; color: var(--color-text, #1e293b); }
.comment-textarea:focus {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 3px var(--color-accent-glow);
}
.submit-btn {
  background-color: var(--color-accent);
  border-color: var(--color-accent);
  border-radius: var(--radius-sm);
  transition: all var(--duration-fast) var(--ease-out);
}
.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-accent);
}
.empty-comment {
  text-align: center;
  padding: 48px 0;
  color: var(--color-text-muted);
  font-size: 0.9375rem;
}
.comment-list {
  width: 100%;
}
.comment-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--color-border-light);
  transition: background var(--duration-fast);
}
.comment-item:hover {
  background: var(--color-accent-soft);
  border-radius: var(--radius-sm);
}
.comment-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  width: 100%;
}
.comment-avatar {
  flex-shrink: 0;
  transition: transform var(--duration-fast) var(--ease-out);
}
.comment-item:hover .comment-avatar {
  transform: scale(1.05);
}
.comment-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.comment-time {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
.comment-content {
  margin-left: 56px;
  line-height: 1.75;
  font-size: 0.9375rem;
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  width: calc(100% - 56px);
  color: var(--color-text);
  transition: background var(--duration-fast);
}
.comment-actions {
  margin-left: 56px;
  display: flex;
  gap: 24px;
  margin-top: 6px;
  padding: 4px 0;
}
.comment-action {
  color: var(--color-text-muted);
  font-size: 0.875rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: color var(--duration-fast);
}
.comment-action:hover {
  color: var(--color-accent);
}
.author-comment {
  background: rgba(245, 158, 11, 0.06);
  border-left: 3px solid var(--color-warning);
}
.replies-list {
  margin-top: 12px;
  margin-left: 24px;
  padding-left: 16px;
  border-left: 2px solid var(--color-border-light);
}
.reply-item {
  padding: 12px 0;
  transition: background var(--duration-fast);
}
.reply-item:hover {
  background: var(--color-accent-soft);
  border-radius: var(--radius-sm);
}
.reply-content {
  margin-left: 48px;
  font-size: 0.8125rem;
  width: calc(100% - 48px);
}
.reply-actions {
  margin-left: 48px;
  gap: 20px;
  font-size: 0.8125rem;
}
.author-name {
  font-weight: 600;
  color: var(--color-accent);
}

/* 响应式适配：语雀移动端适配逻辑 */
@media (max-width: 768px) {
  .note-detail-page {
    padding: 16px 8px;
    max-width: 100%;
  }
  .yuque-note-card {
    padding: 24px 16px;
  }
  .note-title {
    font-size: 24px;
  }
  .note-meta {
    padding-bottom: 16px;
    margin-bottom: 24px;
  }
  .comment-card {
    padding: 24px 16px;
  }
  :deep(.yuque-markdown-body) {
    font-size: 15px;
  }
  :deep(.yuque-markdown-body h1) {
    font-size: 22px;
  }
  :deep(.yuque-markdown-body h2) {
    font-size: 18px;
  }
}

.login-prompt {
  text-align: center;
  padding: 32px 0;
  color: var(--color-text-muted);
}
.login-prompt p {
  margin-bottom: 12px;
  font-size: 0.9375rem;
}
.inline-edit-wrap { padding: 12px; border: 1px solid #d9e1ef; border-radius: 8px; background: #fafbfc; }
/* 直接在原文上编辑的 contenteditable 容器：复用 yuque-markdown-body 排版，加可编辑交互样式 */
.inline-editable {
  min-height: 300px;
  padding: 12px;
  border-radius: 6px;
  background: #fff;
  outline: none;
  cursor: text;
}
.inline-editable:focus { box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2); }
.inline-editable:empty:before {
  content: attr(data-placeholder);
  color: #bfbfbf;
  pointer-events: none;
}
/* 保存/取消操作区：sticky 钉在视口底部，长内容编辑时不必滚到底即可保存 */
.inline-edit-actions {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 8px 4px;
  background: linear-gradient(to bottom, rgba(250, 251, 252, 0) 0%, #fafbfc 40%);
  z-index: 5;
}
.inline-edit-actions .edit-tip { margin-right: auto; color: #8c8c8c; font-size: 12px; }

/* 富文本浮动工具栏：屏幕左右两侧垂直排列，仅编辑时出现；贴合详情页风格（细边框、轻阴影、antd 主色） */
.rt-toolbar {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  background: #fff;
  border: 1px solid #d9e1ef;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 21, 41, 0.06);
  max-height: calc(100vh - 40px);
  overflow-y: auto;
}
.rt-toolbar-left { left: 16px; }
.rt-toolbar-right { right: 16px; }
.rt-btn {
  min-width: 32px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  color: #595959;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: color 0.15s, background 0.15s;
}
.rt-btn:hover { background: rgba(24, 144, 255, 0.06); color: #1890ff; }
.rt-btn-text { font-size: 12px; }
.rt-glyph { font-size: 14px; }
.rt-bold { font-weight: 700; }
.rt-italic { font-style: italic; }
.rt-underline { text-decoration: underline; }
.rt-strike { text-decoration: line-through; }
.rt-sep { height: 1px; background: #f0f0f0; margin: 4px 2px; }
.rt-palette { display: flex; flex-direction: column; gap: 4px; align-items: center; padding: 2px 0; }
.rt-palette-label { font-size: 11px; color: #8c8c8c; display: inline-flex; align-items: center; gap: 4px; }
.rt-swatches { display: flex; flex-wrap: wrap; gap: 3px; max-width: 88px; justify-content: center; }
.rt-swatch {
  width: 16px;
  height: 16px;
  border: 1px solid #d9d9d9;
  border-radius: 3px;
  padding: 0;
  cursor: pointer;
  transition: border-color 0.15s;
}
.rt-swatch:hover { border-color: #1890ff; }
/* 小屏隐藏浮动工具栏（避免遮挡正文），仍可直接编辑保存 */
@media (max-width: 900px) {
  .rt-toolbar { display: none; }
}
</style>