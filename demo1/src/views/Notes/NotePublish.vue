<script setup>
import { reactive, ref, computed, onMounted, onBeforeUnmount, watch, defineAsyncComponent } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { FileTextOutlined, QuestionCircleOutlined, BookOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { createNote, updateNote, getNoteDetail, getMyStars, uploadImage, suggestTags } from '../../utils/api'
import { CATEGORY_OPTIONS } from '../../utils/constant'

// 异步加载编辑器组件
const VditorEditor = defineAsyncComponent(() =>
  import('../../components/VditorEditor.vue')
)

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const editorRef = ref(null)
const submitting = ref(false)
const isEdit = ref(false)
const draftSaved = ref(false)

// 标签联想：输入时拉取已有标签
const tagOptions = ref([])
let tagSearchTimer = null
const onTagSearch = (q) => {
  clearTimeout(tagSearchTimer)
  if (!q) { tagOptions.value = []; return }
  tagSearchTimer = setTimeout(async () => {
    try {
      const res = await suggestTags(q)
      tagOptions.value = (res.data || []).map(name => ({ label: name, value: name }))
    } catch (_) { tagOptions.value = [] }
  }, 250)
}

const draftKey = computed(() => `note_draft_${route.query.id || 'new'}`)
const successRedirectPath = computed(() =>
  route.path.startsWith('/user') ? '/user/notes' : '/main/contentManage'
)

const formState = reactive({
  title: '',
  contentType: 'article',
  category: undefined,
  tagList: [],
  summary: '',
  content: '',
  coverImage: '',
  starId: undefined,
})

const starList = ref([])
const loadStarList = async () => {
  try {
    const res = await getMyStars()
    starList.value = Array.isArray(res.data) ? res.data : []
  } catch (_) {
    starList.value = []
  }
}

const contentTypeOptions = [
  { label: '文章', value: 'article', icon: FileTextOutlined },
  { label: '问题', value: 'question', icon: QuestionCircleOutlined },
  { label: '笔记', value: 'note', icon: BookOutlined },
]

const categoryOptions = CATEGORY_OPTIONS

const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100个字符', trigger: 'blur' },
  ],
  contentType: [{ required: true, message: '请选择内容类型', trigger: 'change' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  summary: [{ required: true, message: '请输入摘要', trigger: 'blur' }],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
    { min: 10, message: '内容至少10个字符', trigger: 'blur' },
  ],
}

// 字数统计
const wordCount = ref(0)
const readingTime = computed(() => {
  const mins = Math.ceil(wordCount.value / 400)
  return mins < 1 ? '< 1' : mins
})

// Markdown 转纯文本（用于摘要自动生成）
const markdownToPlainText = (markdownText = '') =>
  markdownText
    .replace(/```[\s\S]*?```/g, '')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_~>-]/g, '')
    .replace(/\n+/g, ' ')
    .trim()

// 编辑器内容变化回调
const handleContentChange = (value) => {
  formState.content = value
}

// HTML 变化回调
const handleHtmlChanged = (html) => {
  // 可以在这里处理 HTML（如预览、导出等）
}

// 字数统计回调
const handleWordCount = (count) => {
  wordCount.value = count
}

// 封面图片上传（自定义请求：走 axios 携带登录 token，避免原生上传不带 Authorization 报未登录）
const handleCoverUpload = async ({ file, onSuccess, onError }) => {
  const formData = new FormData()
  formData.append('file', file)
  try {
    const resp = await uploadImage(formData)
    if (resp.code === 0) {
      // 成功回调后 a-upload 将 file.status 置为 done，触发下方 handleCoverImageUpload
      onSuccess(resp)
    } else {
      antMessage.error(resp.description || '封面图片上传失败')
      onError(new Error(resp.description || 'upload failed'))
    }
  } catch (e) {
    antMessage.error('封面图片上传失败')
    onError(e)
  }
}

// 封面图片上传
const handleCoverImageUpload = (info) => {
  if (info.file.status === 'done') {
    const resp = info.file.response
    if (resp && resp.code === 0) {
      const url = typeof resp.data === 'string' ? resp.data : resp.data?.url
      if (url) formState.coverImage = url
      antMessage.success('封面图片上传成功')
    } else {
      antMessage.error(resp?.description || resp?.message || '封面图片上传失败')
    }
  } else if (info.file.status === 'error') {
    antMessage.error('封面图片上传失败')
  }
}

// ========== 数据加载与提交 ==========
const loadNoteDetail = async () => {
  const id = route.query.id
  if (!id) return
  try {
    const res = await getNoteDetail(id)
    if (res.code === 0 && res.data) {
      isEdit.value = true
      Object.assign(formState, {
        title: res.data.title || '',
        contentType: res.data.contentType || 'article',
        category: res.data.category,
        tagList: res.data.tagList || [],
        summary: res.data.summary || '',
        content: res.data.content || '',
        coverImage: res.data.coverImage || '',
      })
    }
  } catch (err) {
    console.error('加载笔记详情失败', err)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    const noteData = {
      title: formState.title,
      contentType: formState.contentType,
      category: formState.category,
      tagList: formState.tagList,
      summary: formState.summary,
      content: formState.content,
      coverImage: formState.coverImage,
      starId: formState.starId || null,
    }
    let res
    if (isEdit.value && route.query.id) {
      res = await updateNote(route.query.id, noteData)
    } else {
      res = await createNote(noteData)
    }
    if (res.code === 0) {
      antMessage.success(isEdit.value ? '更新成功！' : '发布成功！')
      localStorage.removeItem(draftKey.value)
      router.push(successRedirectPath.value)
    } else {
      antMessage.error(res.message || '操作失败')
    }
  } catch (err) {
    if (err?.errorFields) {
      antMessage.warning('请完善表单信息')
      return
    }
    if (err?.isBusinessError) {
      antMessage.error(err.description || err.message || '操作失败，请重试')
    } else {
      antMessage.error('操作失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  formRef.value?.resetFields()
  localStorage.removeItem(draftKey.value)
  draftSaved.value = false
}

// 草稿管理
const saveDraft = () => {
  if (isEdit.value) return
  const draft = {
    title: formState.title,
    contentType: formState.contentType,
    category: formState.category,
    tagList: formState.tagList,
    summary: formState.summary,
    content: formState.content,
    coverImage: formState.coverImage,
    savedAt: Date.now(),
  }
  localStorage.setItem(draftKey.value, JSON.stringify(draft))
  draftSaved.value = true
  setTimeout(() => { draftSaved.value = false }, 2000)
}

const restoreDraft = () => {
  if (isEdit.value) return
  const raw = localStorage.getItem(draftKey.value)
  if (!raw) return
  try {
    const draft = JSON.parse(raw)
    if (Date.now() - draft.savedAt > 7 * 24 * 3600 * 1000) {
      localStorage.removeItem(draftKey.value)
      return
    }
    Object.assign(formState, {
      title: draft.title || '',
      contentType: draft.contentType || 'article',
      category: draft.category,
      tagList: draft.tagList || [],
      summary: draft.summary || '',
      content: draft.content || '',
      coverImage: draft.coverImage || '',
    })
    antMessage.info('已恢复上次未提交的草稿')
  } catch (_) {
    localStorage.removeItem(draftKey.value)
  }
}

let autoSaveTimer = null
watch(
  () => [formState.title, formState.content, formState.summary],
  () => {
    if (isEdit.value) return
    clearTimeout(autoSaveTimer)
    autoSaveTimer = setTimeout(saveDraft, 3000)
  }
)

onBeforeUnmount(() => clearTimeout(autoSaveTimer))

onMounted(() => {
  loadNoteDetail()
  restoreDraft()
  loadStarList().then(() => {
    // 从路由参数预填星球（支持从星球详情页“在星球发帖”跳转过来）
    if (route.query.starId) {
      formState.starId = Number(route.query.starId)
    }
  })
})
</script>

<template>
  <div class="note-publish-page">
    <a-card :title="isEdit ? '编辑笔记' : '发布笔记'" bordered>
      <a-form ref="formRef" :model="formState" :rules="rules" layout="vertical">

        <a-form-item label="内容类型" name="contentType">
          <a-radio-group v-model:value="formState.contentType" button-style="solid">
            <a-radio-button
              v-for="option in contentTypeOptions"
              :key="option.value"
              :value="option.value"
            >
              <component :is="option.icon" style="margin-right: 4px" />
              {{ option.label }}
            </a-radio-button>
          </a-radio-group>
        </a-form-item>

        <a-form-item label="标题" name="title">
          <a-input
            v-model:value="formState.title"
            placeholder="请输入标题"
            :maxlength="100"
            show-count
          />
        </a-form-item>

        <a-form-item label="分类" name="category">
          <a-select
            v-model:value="formState.category"
            :options="categoryOptions"
            placeholder="请选择分类"
          />
        </a-form-item>

        <a-form-item label="标签" name="tagList">
          <a-select
            v-model:value="formState.tagList"
            mode="tags"
            placeholder="输入标签后按回车添加"
            :options="tagOptions"
            @search="onTagSearch"
            :max-tag-count="5"
            style="width: 100%"
          />
        </a-form-item>

        <a-form-item label="发布到星球（可选）" name="starId">
          <a-select
            v-model:value="formState.starId"
            placeholder="选择要发布到的星球"
            allow-clear
            :options="starList.map(s => ({ label: s.name, value: s.id }))"
          />
        </a-form-item>

        <a-form-item label="封面图片（可选）" name="coverImage">
          <a-upload
            :custom-request="handleCoverUpload"
            list-type="picture-card"
            accept="image/*"
            :show-upload-list="false"
            @change="handleCoverImageUpload"
            class="cover-upload"
          >
            <img
              v-if="formState.coverImage"
              :src="formState.coverImage"
              alt="封面预览"
              style="width: 100%; height: 100%; object-fit: cover; border-radius: 4px"
            />
            <div v-else>
              <PlusOutlined />
              <div style="margin-top: 8px">上传封面</div>
            </div>
          </a-upload>
          <div style="margin-top: 8px; color: #999; font-size: 12px">
            提示：支持 JPG、PNG 等图片格式，建议尺寸 800x400 像素
          </div>
        </a-form-item>

        <a-form-item label="摘要" name="summary">
          <a-textarea
            v-model:value="formState.summary"
            :rows="3"
            placeholder="请输入摘要，简要描述内容"
            :maxlength="200"
            show-count
          />
        </a-form-item>

        <a-form-item label="内容" name="content">
          <Suspense>
            <template #default>
              <VditorEditor
                ref="editorRef"
                v-model="formState.content"
                :height="500"
                placeholder="支持 Markdown 格式，请输入内容..."
                @html-changed="handleHtmlChanged"
                @word-count="handleWordCount"
              />
            </template>
            <template #fallback>
              <div style="height: 500px; display: flex; align-items: center; justify-content: center; background: #f5f5f5; border-radius: 8px;">
                <a-spin tip="编辑器加载中..." />
              </div>
            </template>
          </Suspense>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="submitting" @click="handleSubmit">发布</a-button>
            <a-button @click="saveDraft" v-if="!isEdit">
              <SaveOutlined /> 保存草稿
            </a-button>
            <a-button @click="handleReset">重置</a-button>
            <a-button @click="router.push(successRedirectPath)">取消</a-button>
            <span v-if="draftSaved" style="color: #52c41a; font-size: 12px">✓ 草稿已保存</span>
          </a-space>
        </a-form-item>

      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.note-publish-page {
  padding: 16px;
}

.cover-upload :deep(.ant-upload-select) {
  width: 200px;
  height: 150px;
}

.cover-upload :deep(.ant-upload-select-picture-card) {
  width: 200px;
  height: 150px;
}
</style>
