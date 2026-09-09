<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message as antMessage, Modal } from 'ant-design-vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, StarOutlined, ApartmentOutlined } from '@ant-design/icons-vue'
import { adminGetStarList, adminCreateStar, adminUpdateStar, adminDeleteStar, adminToggleStarStatus, uploadImage } from '../../utils/api'
import { formatShortDate } from '../../utils/dateUtils'

const router = useRouter()

const loading = ref(false)
const starList = ref([])
const modalVisible = ref(false)
const modalTitle = ref('创建星球')
const submitting = ref(false)
const editingId = ref(null)
const filterKeyword = ref('')

const formRef = ref()
const formState = reactive({
  name: '',
  description: '',
  coverImage: '',
  status: 'active',
})

// 封面上传（走通用图片上传接口，上传后回填 URL）
const uploadingCover = ref(false)
const beforeCoverUpload = (file) => {
  const isImage = /^image\/(jpeg|png|gif|bmp|webp)$/.test(file.type)
  if (!isImage) {
    antMessage.error('仅支持 jpg/png/gif/bmp/webp 图片')
    return false
  }
  if (file.size / 1024 / 1024 >= 10) {
    antMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}
const handleCoverUpload = async ({ file, onSuccess, onError }) => {
  uploadingCover.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadImage(formData)
    formState.coverImage = res.data
    onSuccess(res)
  } catch (err) {
    onError(err)
    antMessage.error(err?.description || '封面上传失败')
  } finally {
    uploadingCover.value = false
  }
}

const rules = {
  name: [
    { required: true, message: '请输入星球名称', trigger: 'blur' },
    { max: 50, message: '名称不超过50个字符', trigger: 'blur' },
  ],
  description: [
    { required: true, message: '请输入星球描述', trigger: 'blur' },
  ],
}

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '星球名称', dataIndex: 'name', key: 'name', width: 180 },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '成员数', dataIndex: 'memberCount', key: 'memberCount', width: 90 },
  { title: '内容数', dataIndex: 'contentCount', key: 'contentCount', width: 90 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 260, fixed: 'right' },
]

const fetchList = async () => {
  loading.value = true
  try {
    const res = await adminGetStarList({ keyword: filterKeyword.value || undefined })
    starList.value = Array.isArray(res.data) ? res.data : []
  } catch (err) {
    antMessage.error(err?.description || '加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  modalTitle.value = '创建星球'
  Object.assign(formState, { name: '', description: '', coverImage: '', status: 'active' })
  modalVisible.value = true
}

const openEdit = (record) => {
  editingId.value = record.id
  modalTitle.value = '编辑星球'
  Object.assign(formState, {
    name: record.name || '',
    description: record.description || '',
    coverImage: record.coverImage || '',
    status: record.status || 'active',
  })
  modalVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    if (editingId.value) {
      await adminUpdateStar(editingId.value, { ...formState })
      antMessage.success('更新成功')
    } else {
      await adminCreateStar({ ...formState })
      antMessage.success('创建成功')
    }
    modalVisible.value = false
    fetchList()
  } catch (err) {
    if (err?.errorFields) return
    antMessage.error(err?.description || '操作失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = (record) => {
  Modal.confirm({
    title: `确认删除星球「${record.name}」？`,
    content: '删除后无法恢复，星球内的成员关系也将清除',
    okType: 'danger',
    onOk: async () => {
      try {
        await adminDeleteStar(record.id)
        antMessage.success('删除成功')
        fetchList()
      } catch (err) {
        antMessage.error(err?.description || '删除失败')
      }
    },
  })
}

const handleToggle = async (record) => {
  try {
    await adminToggleStarStatus(record.id)
    antMessage.success(record.status === 'active' ? '已停用' : '已启用')
    fetchList()
  } catch (err) {
    antMessage.error(err?.description || '操作失败')
  }
}

const formatTime = (time) => formatShortDate(time, '-')

onMounted(() => fetchList())
</script>

<template>
  <div class="star-manage-page">
    <a-card title="星球管理" bordered>
      <div class="toolbar">
        <a-space>
          <a-input
            v-model:value="filterKeyword"
            placeholder="搜索星球名称或描述"
            allow-clear
            style="width: 260px"
            @pressEnter="fetchList"
          />
          <a-button type="primary" @click="fetchList" :loading="loading">查询</a-button>
          <a-button @click="() => { filterKeyword = ''; fetchList() }">重置</a-button>
        </a-space>
        <a-button type="primary" @click="openCreate">
          <PlusOutlined /> 创建星球
        </a-button>
      </div>

      <a-table
        :columns="columns"
        :data-source="starList"
        :loading="loading"
        :scroll="{ x: 1000 }"
        row-key="id"
        style="margin-top: 16px"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <div style="display: flex; align-items: center; gap: 8px">
              <a-avatar
                v-if="record.coverImage"
                :src="record.coverImage"
                shape="square"
                :size="32"
              />
              <StarOutlined v-else style="color: #8ba7c4; font-size: 20px" />
              <span style="font-weight: 500">{{ record.name }}</span>
            </div>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'active' ? 'success' : 'default'">
              {{ record.status === 'active' ? '活跃' : '停用' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="openEdit(record)">
                <EditOutlined /> 编辑
              </a-button>
              <a-button type="link" size="small" @click="router.push(`/main/knowledgeMapEdit/${record.id}`)">
                <ApartmentOutlined /> 知识地图
              </a-button>
              <a-button
                type="link"
                size="small"
                :style="{ color: record.status === 'active' ? '#faad14' : '#52c41a' }"
                @click="handleToggle(record)"
              >
                {{ record.status === 'active' ? '停用' : '启用' }}
              </a-button>
              <a-popconfirm
                :title="`确认删除「${record.name}」？`"
                @confirm="handleDelete(record)"
              >
                <a-button type="link" danger size="small">
                  <DeleteOutlined /> 删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
        <template #empty>
          <div style="text-align: center; padding: 40px; color: #999">暂无星球数据</div>
        </template>
      </a-table>
    </a-card>

    <!-- 创建/编辑弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="modalTitle"
      :confirm-loading="submitting"
      @ok="handleSubmit"
      @cancel="() => { modalVisible = false; formRef?.resetFields() }"
      ok-text="保存"
      cancel-text="取消"
      width="560px"
    >
      <a-form
        ref="formRef"
        :model="formState"
        :rules="rules"
        layout="vertical"
        style="margin-top: 16px"
      >
        <a-form-item label="星球名称" name="name">
          <a-input
            v-model:value="formState.name"
            placeholder="请输入星球名称"
            :maxlength="50"
            show-count
          />
        </a-form-item>
        <a-form-item label="星球描述" name="description">
          <a-textarea
            v-model:value="formState.description"
            :rows="4"
            placeholder="请输入星球描述"
            :maxlength="500"
            show-count
          />
        </a-form-item>
        <a-form-item label="星球封面（可上传或填 URL）" name="coverImage">
          <a-input
            v-model:value="formState.coverImage"
            placeholder="上传图片或直接填写图片 URL"
          >
            <template #addonAfter>
              <a-upload
                accept="image/*"
                :show-upload-list="false"
                :custom-request="handleCoverUpload"
                :before-upload="beforeCoverUpload"
              >
                <span style="cursor: pointer">上传图片</span>
              </a-upload>
            </template>
          </a-input>
          <div v-if="formState.coverImage" style="margin-top: 8px">
            <img
              :src="formState.coverImage"
              alt="封面预览"
              style="max-width: 200px; max-height: 120px; object-fit: cover; border-radius: 4px"
            />
          </div>
        </a-form-item>
        <a-form-item label="状态" name="status">
          <a-radio-group v-model:value="formState.status">
            <a-radio value="active">活跃</a-radio>
            <a-radio value="inactive">停用</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.star-manage-page {
  padding: 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
</style>
