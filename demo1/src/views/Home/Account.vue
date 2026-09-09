<script setup>
import { searchUsers, updateUser,uploadFile } from '../../utils/api'
import { cloneDeep } from 'lodash-es';
import { reactive, ref } from 'vue';
import { onMounted } from 'vue';
import { message as antMessage } from 'ant-design-vue';
import defaultAvatar from '../../assets/images/1.jpg'

const message = ref('')
const dataSource = ref([]);
const editableData = reactive({});
// 新增：上传相关变量
const fileList = ref([]);  // 上传文件列表（用于临时存储）
// 新增：按行缓存待上传的头像文件
const avatarFileMap = reactive({})

// 新增：头像上传前校验（限制文件类型和大小）并拦截自动上传，仅做本地预览
const handleBeforeAvatarUpload = (file, key) => {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    antMessage.error('请上传图片格式文件（JPG、PNG等）');
    return false;
  }
  const isLt2M = file.size / 1024 / 1024 < 2;  // 限制2MB以内
  if (!isLt2M) {
    antMessage.error('图片大小不能超过2MB');
    return false;
  }
  // 缓存文件，设置本地预览，不立刻上传
  avatarFileMap[key] = file
  if (!editableData[key]) editableData[key] = {}
  editableData[key].avatarUrl = URL.createObjectURL(file)
  return false; // 阻止 a-upload 自动上传
};

// 新增：统一处理头像上传变更（保留以兼容某些浏览器的状态更新）
const handleAvatarChange = (info, key) => {
  if (info.file.status === 'removed') {
    avatarFileMap[key] = undefined
  }
};
//数据
const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 60,
    fixed: 'left',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
    width: 100,
  },
  {
    title: '用户名',
    dataIndex: 'username',
    width: 120,
  },
  {
    title: '性别',
    dataIndex: 'gender',
    width: 80,
  },
  {
    title: '手机号',
    dataIndex: 'phone',
    width: 120,
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    width: 120,
  },
  {
    title: '头像',
    dataIndex: 'avatarUrl',
    width: 110,
  },
  {
    title: '角色',
    dataIndex: 'userRole',
    width: 100,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    width: 150,
  },
  {
    title: '操作',
    dataIndex: 'operation',
    width: 120,
    fixed: 'right',
  },
];
const sendMsg = async () => {
  try {
    const res = await searchUsers({})
    const list = Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : [])
    message.value = list // 保存原始数据
    dataSource.value = list.map(item => ({
      ...item,
      key: item.id, 
      // 处理性别显示
      gender: item.gender === 1 ? '男' : item.gender === 0 ? '女' : '未知',
      // 处理角色显示
      userRole: item.userRole === 1 ? '管理员' : '普通用户'
    }));
    console.log("请求成功，表格数据已更新", dataSource.value);
  } catch (err) {
    console.log("请求失败", err);
    if (err?.isBusinessError) {
      if (err.code === 40100) antMessage.warning('未登录或登录已过期，请先登录')
      else if (err.code === 40101) antMessage.warning('无权限')
      else antMessage.error(err.description || err.message || '数据加载失败！')
    } else {
      antMessage.error('数据加载失败！')
    }
  }
};

// 编辑时基于当前行的 key（即 id）保存临时数据
const edit = (key) => {
  // 从 dataSource 中找到当前行数据并深拷贝
  const currentRow = dataSource.value.find(item => item.key === key);
  if (currentRow) {
    editableData[key] = cloneDeep(currentRow);
  }
};

// 保存时更新 dataSource 中对应行的数据
const save = async(key) => {
  const targetIndex = dataSource.value.findIndex(item => item.key === key);
  if (targetIndex !== -1) {
    //备份原始数据
    const originalRowData = cloneDeep(dataSource.value[targetIndex]);
    try {
      //更新后端，处理性别字段的数据类型转换
      const updateData = {
        id: key,
        ...editableData[key],
      };
      
      // 若本行存在待上传头像文件，则先上传获取 URL
      if (avatarFileMap[key]) {
        const formData = new FormData()
        formData.append('file', avatarFileMap[key])
        const uploadResp = await uploadFile(formData)
        const uploadData = uploadResp?.data
        const uploadedUrl = typeof uploadData === 'string' ? uploadData : uploadData?.url
        if (uploadedUrl) {
          updateData.avatarUrl = uploadedUrl
          editableData[key].avatarUrl = uploadedUrl
        }
      }
      
      // 处理性别字段：将中文转换为数字
      if (updateData.gender === '男') {
        updateData.gender = 1;
      } else if (updateData.gender === '女') {
        updateData.gender = 0;
      } else if (updateData.gender === '未知') {
        updateData.gender = null; // 或者设置为默认值
      }
      
      // 处理角色字段：将中文转换为数字
      if (updateData.userRole === '管理员') {
        updateData.userRole = 1;
      } else if (updateData.userRole === '普通用户') {
        updateData.userRole = 0;
      }
      
      await updateUser(updateData);
      antMessage.success('更新成功！');
       // 3. 更新成功，同步到前端
      Object.assign(dataSource.value[targetIndex], editableData[key]);
      delete editableData[key];
      // 成功后清理待上传文件缓存
      if (avatarFileMap[key]) delete avatarFileMap[key]
    } catch (error) {
      // 优先显示 description，其次 message，最后使用默认提示
      const errorMsg = error?.description || error?.message || '更新失败，请重试！';
      antMessage.error(errorMsg);
      // 如果后端更新失败，可以考虑将前端数据回滚
       // 回滚当前行
        dataSource.value.splice(targetIndex, 1, originalRowData);
    }
    delete editableData[key];
  }
};

// 取消编辑还原为原始行
const cancel = (key) => {
  delete editableData[key]
}
onMounted(() => {
  sendMsg();
});
</script>
<template>
  <div class="account-container">
    <a-table 
      :columns="columns" 
      :data-source="dataSource" 
      bordered
      :scroll="{ x: 1200, y: 500 }"
      :pagination="{ pageSize: 10, showSizeChanger: true, showQuickJumper: true }"
    >
      <template #bodyCell="{ column, text, record }">
        <template v-if="['id','userAccount', 'username', 'gender','phone','email','avatarUrl','userRole','createTime'].includes(column.dataIndex)">
          <div>
            <!-- 性别字段使用下拉选择 -->
            <a-select
              v-if="editableData[record.key] && column.dataIndex === 'gender'"
              v-model:value="editableData[record.key][column.dataIndex]"
              style="margin: -5px 0; width: 100%"
              size="small"
            >
              <a-select-option value="男">男</a-select-option>
              <a-select-option value="女">女</a-select-option>
              <a-select-option value="未知">未知</a-select-option>
            </a-select>
            <!-- 角色字段使用下拉选择 -->
            <a-select
              v-else-if="editableData[record.key] && column.dataIndex === 'userRole'"
              v-model:value="editableData[record.key][column.dataIndex]"
              style="margin: -5px 0; width: 100%"
              size="small"
            >
              <a-select-option value="普通用户">普通用户</a-select-option>
              <a-select-option value="管理员">管理员</a-select-option>
            </a-select>
            <!-- 头像字段：仅做本地预览，保存时再统一上传 -->
      <div v-else-if="editableData[record.key] && column.dataIndex === 'avatarUrl'">
        <a-upload
          :before-upload="(file) => handleBeforeAvatarUpload(file, record.key)"
          list-type="picture-card"
          :file-list="fileList"
          @change="(info) => handleAvatarChange(info, record.key)"
          :show-upload-list="false"  
          style="display: inline-block"
        >
          <!-- 上传按钮/预览图 -->
          <img
            :src="editableData[record.key].avatarUrl || defaultAvatar"
            alt="头像预览"
            class="avatar-img"
            style="width: 60px; height: 60px; border-radius: 50%"
           
           
          />
          
        </a-upload>
      </div>
            <!-- 其他字段使用输入框 -->
            <a-input
              v-else-if="editableData[record.key]"
              v-model:value="editableData[record.key][column.dataIndex]"
              style="margin: -5px 0"
              :size="column.dataIndex === 'avatarUrl' ? 'small' : 'default'"
            />
            <template v-else>
              <span v-if="column.dataIndex === 'avatarUrl'" class="avatar-display">
                <img :src="text || defaultAvatar" alt="头像" class="avatar-img" />
              </span>
              <span v-else>{{ text }}</span>
            </template>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'operation'">
          <div class="editable-row-operations">
            <span v-if="editableData[record.key]">
              <a-typography-link @click="save(record.key)">保存</a-typography-link>
              <a-popconfirm title="取消?" @confirm="cancel(record.key)">
                <a>取消</a>
              </a-popconfirm>
            </span>
            <span v-else>
              <a @click="edit(record.key)">编辑</a>
            </span>
          </div>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.account-container {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.account-container .ant-table {
  height: calc(100vh - 200px);
  overflow: auto;
}

.avatar-display {
  display: inline-block;
  width: 40px;
  height: 40px;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.editable-row-operations {
  display: flex;
  gap: 8px;
  align-items: center;
}

.editable-row-operations a {
  margin-right: 8px;
}

/* 响应式调整 */
@media (max-width: 1200px) {
  .account-container .ant-table {
    font-size: 12px;
  }
  
  .avatar-display {
    width: 30px;
    height: 30px;
  }
}

/* 确保表格列宽合理 */
.account-container .ant-table-thead > tr > th {
  padding: 8px 4px;
  font-size: 12px;
}

.account-container .ant-table-tbody > tr > td {
  padding: 8px 4px;
  font-size: 12px;
}
/* 上传组件预览图样式优化 */
.ant-upload-picture-card-wrapper {
  display: inline-block !important;
}

/* 确保上传按钮与表格单元格对齐 */
.ant-upload {
  vertical-align: middle;
}
</style>