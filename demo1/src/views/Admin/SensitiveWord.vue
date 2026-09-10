<script setup>
import { ref, computed, onMounted } from 'vue'
import { getSensitiveWords, updateSensitiveWords, reloadSensitiveWords } from '../../utils/api'
import { message } from 'ant-design-vue'

const loading = ref(false)
const saving = ref(false)
const reloading = ref(false)
const total = ref(0)
const wordsText = ref('')

// 统计当前输入的词数（去空去重前）
const inputCount = computed(() => {
  if (!wordsText.value) return 0
  return wordsText.value
    .split(/[\n,，\s]+/)
    .map((w) => w.trim())
    .filter((w) => w.length > 0).length
})

const fetchWords = async () => {
  loading.value = true
  try {
    const res = await getSensitiveWords()
    if (res.code === 0 && res.data) {
      const words = res.data.words || []
      total.value = res.data.total ?? words.length
      wordsText.value = words.join('\n')
    }
  } catch (err) {
    message.error('加载敏感词库失败')
  } finally {
    loading.value = false
  }
}

const saveWords = async () => {
  saving.value = true
  try {
    // 按行 / 逗号 / 空白拆分，去空去重
    const words = wordsText.value
      .split(/[\n,，\s]+/)
      .map((w) => w.trim())
      .filter((w) => w.length > 0)
    const unique = [...new Set(words)]
    const res = await updateSensitiveWords(unique)
    if (res.code === 0 && res.data) {
      total.value = res.data.total ?? unique.length
      message.success(`保存成功，共 ${res.data.total ?? unique.length} 个敏感词`)
    }
  } catch (err) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

const reloadWords = async () => {
  reloading.value = true
  try {
    const res = await reloadSensitiveWords()
    if (res.code === 0 && res.data) {
      message.success(`词库已重载，共 ${res.data.total} 个敏感词`)
      await fetchWords()
    }
  } catch (err) {
    message.error('重载失败')
  } finally {
    reloading.value = false
  }
}

onMounted(() => {
  fetchWords()
})
</script>

<template>
  <div class="sensitive-page">
    <div class="page-head">
      <div>
        <h2>敏感词管理</h2>
        <span class="page-sub">维护内容过滤词库，保存后即时生效（评论 / 笔记命中将被拦截或待审）</span>
      </div>
      <div class="head-actions">
        <a-statistic title="当前词库" :value="total" suffix="个" />
        <a-button :loading="reloading" @click="reloadWords">从文件重载</a-button>
      </div>
    </div>

    <a-spin :spinning="loading">
      <a-card bordered>
        <div class="edit-tip">
          每行一个敏感词（也支持逗号分隔），保存时自动去重。修改后点击「保存」即热生效。
        </div>
        <a-textarea
          v-model:value="wordsText"
          :rows="18"
          placeholder="每行输入一个敏感词，例如：&#10;博彩&#10;赌博&#10;诈骗"
          class="words-textarea"
        />
        <div class="edit-footer">
          <span class="footer-tip">当前输入 {{ inputCount }} 项</span>
          <a-space>
            <a-button @click="fetchWords">放弃修改</a-button>
            <a-button type="primary" :loading="saving" @click="saveWords">保存</a-button>
          </a-space>
        </div>
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped>
.sensitive-page {
  padding: 4px 0;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 24px;
  flex-wrap: wrap;
}
.page-head h2 {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 600;
}
.page-sub {
  color: #888;
  font-size: 13px;
}
.head-actions {
  display: flex;
  align-items: center;
  gap: 24px;
}
.edit-tip {
  color: #888;
  font-size: 13px;
  margin-bottom: 12px;
  line-height: 1.6;
}
.words-textarea {
  font-family: 'Courier New', Consolas, monospace;
}
.edit-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}
.footer-tip {
  color: #999;
  font-size: 13px;
}
</style>
