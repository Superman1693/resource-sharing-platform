<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { FileTextOutlined, QuestionCircleOutlined, BookOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { createNote, updateNote, getNoteDetail, uploadImage, suggestTags } from '../../utils/api'
import { CATEGORY_OPTIONS } from '../../utils/constant'
import VditorEditor from '../../components/VditorEditor.vue'

const router = useRouter()
const route = useRoute()

const formRef = ref()
const submitting = ref(false)
const isEdit = ref(false)

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

const formState = reactive({
  title: '',
  contentType: 'article', // 'article' 文章, 'question' 问题, 'note' 笔记
  summary: '',
  content: '',
  category: undefined,
  tagList: [],
  coverImage: '',
  starId: undefined, // 所属星球ID
})

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

// 处理封面图片上传
const handleCoverImageUpload = (info) => {
  if (info.file.status === 'done') {
    const resp = info.file.response
    if (resp && resp.code === 0) {
      // 若后端返回了图片地址则更新；否则仅提示成功
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
  contentType: [
    { required: true, message: '请选择内容类型', trigger: 'change' },
  ],
  content: [
    { required: true, message: '请输入内容', trigger: 'blur' },
    { min: 10, message: '内容至少10个字符', trigger: 'blur' },
  ],
  category: [
    { required: true, message: '请选择分类', trigger: 'change' },
  ],
  summary: [
    { required: true, message: '请输入摘要', trigger: 'blur' },
  ],
}

// 内容编辑器已统一使用 VditorEditor 组件（含工具栏、实时预览、图片粘贴上传、导入 Markdown）

const loadContentDetail = async () => {
  const id = route.query.id
  if (!id) return
  
  try {
    const res = await getNoteDetail(id)
    if (res.code === 0 && res.data) {
      isEdit.value = true
      Object.assign(formState, {
        title: res.data.title || '',
        contentType: res.data.contentType || 'article',
        summary: res.data.summary || '',
        content: res.data.content || '',
        category: res.data.category,
        tagList: res.data.tagList || [],
        coverImage: res.data.coverImage || '',
        starId: res.data.starId,
      })
    }
  } catch (err) {
    console.error('加载内容详情失败', err)
  }
}

// 敏感词错误定位：命中字段红框 + help 文案，编辑该字段时自动清除
const sensitiveError = reactive({ title: '', summary: '', content: '' })
const clearSensitive = (field) => {
  if (field) sensitiveError[field] = ''
  else { sensitiveError.title = ''; sensitiveError.summary = ''; sensitiveError.content = '' }
}
watch(() => formState.title, () => { if (sensitiveError.title) sensitiveError.title = '' })
watch(() => formState.summary, () => { if (sensitiveError.summary) sensitiveError.summary = '' })
watch(() => formState.content, () => { if (sensitiveError.content) sensitiveError.content = '' })

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    
    const contentData = {
      title: formState.title,
      contentType: formState.contentType,
      summary: formState.summary,
      content: formState.content,
      category: formState.category,
      tagList: formState.tagList,
      coverImage: formState.coverImage,
      starId: formState.starId,
    }
    
    let res
    if (isEdit.value && route.query.id) {
      res = await updateNote(route.query.id, contentData)
    } else {
      res = await createNote(contentData)
    }
    
    if (res.code === 0) {
      clearSensitive()
      antMessage.success(isEdit.value ? '更新成功！' : '发布成功！')
      router.push('/main/contentManage')
    } else {
      antMessage.error(res.message || '操作失败')
    }
  } catch (err) {
    if (err?.errorFields) {
      antMessage.warning('请完善表单信息')
      return
    }
    if (err?.isBusinessError) {
      const desc = err.description || err.message || '操作失败，请重试'
      antMessage.error(desc)
      // 敏感词定位：根据错误文案命中的字段，红框高亮对应输入项
      clearSensitive()
      if (desc.includes('标题')) sensitiveError.title = desc
      else if (desc.includes('摘要')) sensitiveError.summary = desc
      else if (desc.includes('正文')) sensitiveError.content = desc
    } else {
      antMessage.error('操作失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  loadContentDetail()
})
</script>

<template>
  <div class="content-publish-page">
    <a-card :title="isEdit ? '编辑内容' : '发布内容'" bordered>
      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        layout="vertical"
      >
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

        <a-form-item label="标题" name="title" :validate-status="sensitiveError.title ? 'error' : undefined" :help="sensitiveError.title || undefined">
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

        <a-form-item label="摘要" name="summary" :validate-status="sensitiveError.summary ? 'error' : undefined" :help="sensitiveError.summary || undefined">
          <a-textarea
            v-model:value="formState.summary"
            :rows="3"
            placeholder="请输入摘要，简要描述内容"
            :maxlength="200"
            show-count
          />
        </a-form-item>

        <a-form-item label="内容" name="content" :validate-status="sensitiveError.content ? 'error' : undefined" :help="sensitiveError.content || undefined">
          <VditorEditor
            v-model="formState.content"
            placeholder="支持 Markdown 格式，请输入内容..."
            :height="500"
          />
        </a-form-item>

        <a-form-item label="所属星球（可选）" name="starId">
          <a-select
            v-model:value="formState.starId"
            placeholder="选择发布到哪个星球（可选）"
            allow-clear
          >
            <a-select-option :value="1">Vue3 深入学习</a-select-option>
            <a-select-option :value="2">Spring Boot 实战</a-select-option>
            <a-select-option :value="3">算法与数据结构精讲</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="submitting" @click="handleSubmit">
              发布
            </a-button>
            <a-button @click="handleReset">重置</a-button>
            <a-button @click="router.back()">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.content-publish-page {
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

