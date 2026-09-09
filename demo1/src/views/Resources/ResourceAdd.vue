<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { UploadOutlined } from '@ant-design/icons-vue'
import { addResource, uploadResourceFile } from '../../utils/api'
import { CATEGORY_OPTIONS, RESOURCE_TYPE_OPTIONS } from '../../utils/constant'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const fileList = ref([])

const formState = reactive({
  title: '',
  description: '',
  resourceType: 'document', // 'document' 文档, 'video' 视频, 'code' 代码, 'other' 其他
  category: undefined,
  tags: [],
  downloadUrl: '',
  coverImage: '',
  isPublic: true,
})

const resourceTypeOptions = RESOURCE_TYPE_OPTIONS

const categoryOptions = CATEGORY_OPTIONS

const rules = {
  title: [
    { required: true, message: '请输入资源标题', trigger: 'blur' },
    { max: 100, message: '标题不能超过100个字符', trigger: 'blur' },
  ],
  resourceType: [
    { required: true, message: '请选择资源类型', trigger: 'change' },
  ],
  category: [
    { required: true, message: '请选择分类', trigger: 'change' },
  ],
  description: [
    { required: true, message: '请输入资源描述', trigger: 'blur' },
  ],
}

// 核心修复：提交前校验文件类资源的downloadUrl
const handleSubmit = async () => {
  if (!formRef.value) return
  
  // 文件类资源强制校验downloadUrl
  const fileResourceTypes = ['document', 'video', 'code']
  if (fileResourceTypes.includes(formState.resourceType) && !formState.downloadUrl) {
    antMessage.warning('文件类资源必须先上传文件获取下载链接！')
    return
  }

  try {
    await formRef.value.validate()
    submitting.value = true
    
    const resourceData = {
      title: formState.title,
      description: formState.description,
      resourceType: formState.resourceType,
      category: formState.category,
      tags: formState.tags, // 直接传数组，后端序列化JSON
      downloadUrl: formState.downloadUrl,
      coverImage: formState.coverImage,
      isPublic: formState.isPublic,
    }
    
    const res = await addResource(resourceData)
    if (res.code === 0) {
      antMessage.success('添加成功！')
      router.push('/main/resourceManage')
    } else {
      antMessage.error(res.message || '添加失败')
    }
  } catch (err) {
    if (err?.errorFields) {
      antMessage.warning('请完善表单信息')
      return
    }
    if (err?.isBusinessError) {
      antMessage.error(err.description || err.message || '添加失败，请重试')
    } else {
      antMessage.error('添加失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}

const handleReset = () => {
  formRef.value?.resetFields()
  fileList.value = []
  // 重置时清空downloadUrl
  formState.downloadUrl = ''
}

// 核心修复：正确解析后端返回的downloadUrl
const handleUpload = async (info) => {
  if (info.file.status === 'uploading') {
    return
  }
  if (info.file.status === 'done') {
    const resp = info.file.response
    if (resp && resp.code === 0) {
      // 适配后端返回结构：优先取downloadUrl，兼容url（兜底）
      const downloadUrl = resp.data?.downloadUrl || resp.data?.url
      if (downloadUrl) {
        formState.downloadUrl = downloadUrl
        antMessage.success('文件上传成功，已自动填充下载链接')
      } else {
        antMessage.warning('文件上传成功，但未获取到下载链接')
      }
    } else {
      antMessage.error(resp?.description || resp?.message || '文件上传失败')
    }
  } else if (info.file.status === 'error') {
    antMessage.error('文件上传失败，请检查网络或文件大小')
  }
}

const beforeUpload = (file) => {
  const isLt100M = file.size / 1024 / 1024 < 100
  if (!isLt100M) {
    antMessage.error('文件大小不能超过 100MB')
    return false
  }
  return true
}

// 自定义上传方法（适配后端/upload接口）
const customRequest = async (options) => {
  const { file, onSuccess, onError } = options
  const formData = new FormData()
  formData.append('file', file)
  
  try {
    // 调用uploadResourceFile方法（api.js中配置为/upload）
    const res = await uploadResourceFile(formData)
    if (res.code === 0) {
      onSuccess(res, file)
    } else {
      onError(new Error(res.message || '上传失败'), file)
    }
  } catch (err) {
    onError(err, file)
  }
}
</script>

<template>
  <div class="resource-add-page">
    <a-alert
      type="info"
      show-icon
      message="资源提交后默认进入待审核，管理员通过后才会公开展示"
      style="margin-bottom: 16px"
    />
    <a-card title="添加学习资源" bordered>
      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        layout="vertical"
      >
        <a-form-item label="资源标题" name="title">
          <a-input
            v-model:value="formState.title"
            placeholder="请输入资源标题"
            :maxlength="100"
            show-count
          />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="资源类型" name="resourceType">
              <a-select
                v-model:value="formState.resourceType"
                :options="resourceTypeOptions"
                placeholder="请选择资源类型"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="分类" name="category">
              <a-select
                v-model:value="formState.category"
                :options="categoryOptions"
                placeholder="请选择分类"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="资源描述" name="description">
          <a-textarea
            v-model:value="formState.description"
            :rows="4"
            placeholder="请输入资源描述"
            :maxlength="500"
            show-count
          />
        </a-form-item>

        <a-form-item label="标签" name="tags">
          <a-select
            v-model:value="formState.tags"
            mode="tags"
            placeholder="输入标签后按回车添加"
            :max-tag-count="5"
            style="width: 100%"
          />
        </a-form-item>

        <!-- 核心修改：适配后端/upload接口，保留自定义上传逻辑 -->
        <a-form-item label="资源文件">
          <a-upload
            v-model:file-list="fileList"
            :custom-request="customRequest" 
            :before-upload="beforeUpload"
            @change="handleUpload"
            :show-upload-list="{ showPreviewIcon: false }"
          >
            <a-button>
              <UploadOutlined /> 上传文件
            </a-button>
          </a-upload>
          <div style="margin-top: 8px; color: #999; font-size: 12px">
            提示：支持上传文档、视频、代码等资源文件，最大 100MB
          </div>
        </a-form-item>

        <a-form-item label="下载链接" name="downloadUrl">
          <a-input
            v-model:value="formState.downloadUrl"
            placeholder="上传文件后自动填充，或手动输入下载链接"
            :disabled="!!formState.downloadUrl"  
          />
          <div v-if="formState.downloadUrl" style="margin-top: 4px; color: #52c41a; font-size: 12px">
            ✅ 已自动填充OSS下载链接
          </div>
        </a-form-item>

        <a-form-item label="封面图片（可选）" name="coverImage">
          <a-input
            v-model:value="formState.coverImage"
            placeholder="请输入图片URL或上传图片"
          />
          <div v-if="formState.coverImage" style="margin-top: 8px">
            <img
              :src="formState.coverImage"
              alt="封面预览"
              style="max-width: 200px; max-height: 150px; object-fit: cover; border-radius: 4px"
            />
          </div>
        </a-form-item>

        <a-form-item label="公开设置" name="isPublic">
          <a-radio-group v-model:value="formState.isPublic">
            <a-radio :value="true">公开</a-radio>
            <a-radio :value="false">仅管理员可见</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="submitting" @click="handleSubmit">
              添加资源
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
.resource-add-page {
  padding: 16px;
}
</style>