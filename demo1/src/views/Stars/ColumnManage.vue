<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyColumns, createColumn, updateColumn, deleteColumn, getMyStars } from '../../utils/api'
import { message, Modal } from 'ant-design-vue'
import { PlusOutlined, EditOutlined, DeleteOutlined, ArrowRightOutlined } from '@ant-design/icons-vue'

const router = useRouter()
const loading = ref(false)
const columns = ref([])
const stars = ref([])
const modalVisible = ref(false)
const editing = ref(false)
const submitting = ref(false)
const form = reactive({ id: null, title: '', description: '', coverImage: '', starId: undefined, status: 'active' })

const fetchColumns = async () => {
  loading.value = true
  try {
    const res = await getMyColumns()
    if (res.code === 0) columns.value = res.data || []
  } catch (e) {
    message.error('加载专栏失败')
  } finally {
    loading.value = false
  }
}

const fetchStars = async () => {
  try {
    const res = await getMyStars()
    stars.value = Array.isArray(res.data) ? res.data : []
  } catch (_) {
    stars.value = []
  }
}

const openCreate = () => {
  editing.value = false
  Object.assign(form, { id: null, title: '', description: '', coverImage: '', starId: undefined, status: 'active' })
  modalVisible.value = true
}

const openEdit = (col) => {
  editing.value = true
  Object.assign(form, {
    id: col.id,
    title: col.title,
    description: col.description || '',
    coverImage: col.coverImage || '',
    starId: col.starId,
    status: col.status
  })
  modalVisible.value = true
}

const submit = async () => {
  if (!form.title || !form.starId) {
    message.warning('请填写标题并选择星球')
    return
  }
  submitting.value = true
  try {
    const body = {
      title: form.title,
      description: form.description,
      coverImage: form.coverImage,
      starId: form.starId,
      status: form.status
    }
    const res = editing.value ? await updateColumn(form.id, body) : await createColumn(body)
    if (res.code === 0) {
      message.success(editing.value ? '更新成功' : '创建成功')
      modalVisible.value = false
      fetchColumns()
    }
  } catch (e) {
    message.error(e?.description || '操作失败')
  } finally {
    submitting.value = false
  }
}

const remove = (col) => {
  Modal.confirm({
    title: `确认删除专栏《${col.title}》吗？`,
    content: '专栏下笔记将自动解绑，笔记本身不受影响',
    onOk: async () => {
      try {
        const res = await deleteColumn(col.id)
        if (res.code === 0) {
          message.success('已删除')
          fetchColumns()
        }
      } catch (e) {
        message.error('删除失败')
      }
    }
  })
}

const goDetail = (col) => router.push(`/user/column/${col.id}`)

onMounted(() => {
  fetchColumns()
  fetchStars()
})
</script>

<template>
  <div class="column-manage-page">
    <div class="page-head">
      <div>
        <h2>我的专栏</h2>
        <span class="sub">把同星球的系列笔记串成专栏，详情页可管理章节顺序</span>
      </div>
      <a-button type="primary" @click="openCreate"><PlusOutlined /> 新建专栏</a-button>
    </div>

    <a-spin :spinning="loading">
      <div v-if="columns.length === 0 && !loading" class="empty">
        <p>还没有专栏，点击右上角创建第一个</p>
      </div>
      <div v-else class="col-grid">
        <div v-for="col in columns" :key="col.id" class="col-card" @click="goDetail(col)">
          <div class="col-cover" :style="{ backgroundImage: col.coverImage ? `url(${col.coverImage})` : '' }"></div>
          <div class="col-body">
            <div class="col-title">{{ col.title }}</div>
            <div class="col-desc">{{ col.description || '暂无描述' }}</div>
            <div class="col-meta">
              <a-tag :color="col.status === 'active' ? 'green' : 'default'">{{ col.status === 'active' ? '连载中' : '已完结' }}</a-tag>
              <span>{{ col.noteCount || 0 }} 篇</span>
              <span v-if="col.starName">{{ col.starName }}</span>
            </div>
          </div>
          <div class="col-actions" @click.stop>
            <a-button size="small" @click="openEdit(col)"><EditOutlined /> 编辑</a-button>
            <a-button size="small" danger @click="remove(col)"><DeleteOutlined /></a-button>
            <a-button size="small" type="link" @click="goDetail(col)">管理章节 <ArrowRightOutlined /></a-button>
          </div>
        </div>
      </div>
    </a-spin>

    <a-modal v-model:open="modalVisible" :title="editing ? '编辑专栏' : '新建专栏'" :confirm-loading="submitting" @ok="submit" ok-text="保存" cancel-text="取消">
      <a-form layout="vertical">
        <a-form-item label="专栏标题" required>
          <a-input v-model:value="form.title" placeholder="如：Vue3 源码精读" :maxlength="100" show-count />
        </a-form-item>
        <a-form-item label="所属星球" required>
          <a-select v-model:value="form.starId" placeholder="选择星球" :options="stars.map(s => ({ label: s.name, value: s.id }))" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="form.description" :rows="3" :maxlength="200" show-count placeholder="专栏简介" />
        </a-form-item>
        <a-form-item label="状态">
          <a-radio-group v-model:value="form.status">
            <a-radio value="active">连载中</a-radio>
            <a-radio value="archived">已完结</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.column-manage-page { padding: 16px; max-width: 960px; margin: 0 auto; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 20px; gap: 16px; flex-wrap: wrap; }
.page-head h2 { margin: 0 0 4px; font-size: 22px; font-weight: 600; }
.sub { color: #888; font-size: 13px; }
.empty { text-align: center; padding: 60px 0; color: #999; }
.col-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.col-card { background: #fff; border: 1px solid #f0f0f0; border-radius: 10px; overflow: hidden; cursor: pointer; transition: box-shadow .2s; }
.col-card:hover { box-shadow: 0 4px 16px rgba(0,0,0,.1); }
.col-cover { height: 120px; background: linear-gradient(135deg,#6366f1,#818cf8); background-size: cover; background-position: center; }
.col-body { padding: 14px; }
.col-title { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.col-desc { color: #888; font-size: 13px; line-height: 1.5; height: 40px; overflow: hidden; }
.col-meta { display: flex; align-items: center; gap: 8px; margin-top: 10px; font-size: 12px; color: #999; flex-wrap: wrap; }
.col-actions { display: flex; gap: 6px; padding: 0 14px 14px; flex-wrap: wrap; }
</style>
