<script setup>
import { ref, reactive, onMounted } from 'vue'
import { message as antMessage, Modal } from 'ant-design-vue'
import { getUserList, banUser, unbanUser, deleteUser } from '../utils/api'

const loading = ref(false)
const userList = ref([])
const filterForm = reactive({ keyword: '' })
const pagination = reactive({ current: 1, pageSize: 10, total: 0 })

const STATUS_MAP = { 0: { text: '正常', color: 'success' }, 1: { text: '封禁', color: 'error' } }
const ROLE_MAP = { 0: '普通用户', 1: '管理员', 2: '超级管理员' }

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '账号', dataIndex: 'userAccount', key: 'userAccount', width: 150 },
  { title: '用户名', dataIndex: 'username', key: 'username', width: 150 },
  { title: '角色', dataIndex: 'userRole', key: 'userRole', width: 120 },
  { title: '状态', dataIndex: 'userStatus', key: 'userStatus', width: 100 },
  { title: '注册时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', dataIndex: 'action', key: 'action', width: 180, fixed: 'right' },
]

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getUserList({ userAccount: filterForm.keyword || undefined })
    const data = res.data || []
    pagination.total = data.length
    const start = (pagination.current - 1) * pagination.pageSize
    userList.value = data.slice(start, start + pagination.pageSize)
  } catch (err) {
    antMessage.error(err?.description || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const handleBan = (record) => {
  Modal.confirm({
    title: `确认封禁用户「${record.username}」？`,
    onOk: async () => {
      try {
        await banUser(record.id)
        antMessage.success('封禁成功')
        fetchUsers()
      } catch (err) {
        antMessage.error(err?.description || '操作失败')
      }
    },
  })
}

const handleUnban = async (record) => {
  try {
    await unbanUser(record.id)
    antMessage.success('解封成功')
    fetchUsers()
  } catch (err) {
    antMessage.error(err?.description || '操作失败')
  }
}

const handleDelete = (record) => {
  Modal.confirm({
    title: `确认删除用户「${record.username}」？此操作不可恢复`,
    onOk: async () => {
      try {
        await deleteUser(record.id)
        antMessage.success('删除成功')
        fetchUsers()
      } catch (err) {
        antMessage.error(err?.description || '操作失败')
      }
    },
  })
}

const handleTableChange = (pag) => {
  pagination.current = pag.current
  pagination.pageSize = pag.pageSize
  fetchUsers()
}

onMounted(fetchUsers)
</script>

<template>
  <div style="padding: 16px">
    <a-card title="用户管理" bordered>
      <a-form layout="inline" style="margin-bottom: 16px">
        <a-form-item label="账号搜索">
          <a-input v-model:value="filterForm.keyword" placeholder="输入账号关键词" allow-clear style="width: 200px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="fetchUsers" :loading="loading">查询</a-button>
        </a-form-item>
      </a-form>

      <a-table
        :columns="columns"
        :data-source="userList"
        :loading="loading"
        :pagination="pagination"
        @change="handleTableChange"
        row-key="id"
        :scroll="{ x: 900 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'userRole'">
            {{ ROLE_MAP[record.userRole] || '未知' }}
          </template>
          <template v-else-if="column.key === 'userStatus'">
            <a-tag :color="STATUS_MAP[record.userStatus]?.color">
              {{ STATUS_MAP[record.userStatus]?.text || '未知' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button
                v-if="record.userStatus === 0"
                type="link" danger size="small"
                @click="handleBan(record)"
              >封禁</a-button>
              <a-button
                v-else
                type="link" size="small" style="color: #52c41a"
                @click="handleUnban(record)"
              >解封</a-button>
              <a-popconfirm title="确认删除该用户？" @confirm="handleDelete(record)">
                <a-button type="link" danger size="small">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
