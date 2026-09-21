<script setup>
import { computed, onMounted, ref, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import { ArrowLeftOutlined, DownloadOutlined, FileOutlined, FireOutlined, VideoCameraOutlined, CodeOutlined, FilePdfOutlined } from '@ant-design/icons-vue'
import { renderMarkdown } from '../../utils/markdown'
import { getResourceDetail, getResourceList, getResourceContent } from '../../utils/api'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import css from 'highlight.js/lib/languages/css'
import xml from 'highlight.js/lib/languages/xml'
import sql from 'highlight.js/lib/languages/sql'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import typescript from 'highlight.js/lib/languages/typescript'
import 'highlight.js/styles/github-dark.css'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('css', css)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('typescript', typescript)

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const relatedLoading = ref(false)
const resource = ref(null)
const relatedResources = ref([])
const codePreview = ref('')
const mdPreview = ref('')
const txtPreview = ref('')
// 文本类资源预览失败原因（文件不存在 / 网络异常等），用于提示而非静默"加载中"
const contentError = ref('')
const codeRef = ref(null)

const resourceId = computed(() => Number(route.params.id))

const statusLabel = {
  pending: '待审核',
  enabled: '已公开',
  disabled: '已停用',
}

const typeMeta = {
  document: { label: '文档', icon: FilePdfOutlined },
  video: { label: '视频', icon: VideoCameraOutlined },
  code: { label: '代码', icon: CodeOutlined },
  other: { label: '其他', icon: FileOutlined },
}

const currentTypeMeta = computed(() => typeMeta[resource.value?.resourceType] || typeMeta.other)

const loadResource = async () => {
  loading.value = true
  try {
    const res = await getResourceDetail(resourceId.value)
    resource.value = res.data || null
    // 代码 / Markdown / 纯文本：走后端 /resource/content/{id} 拉取文本（绕过 OSS 前端 CORS 限制）
    contentError.value = ''
    if ((resource.value?.resourceType === 'code' || isMarkdown.value || isText.value) && resource.value?.id) {
      try {
        const res = await getResourceContent(resource.value.id)
        const raw = (res && res.code === 0 && typeof res.data === 'string') ? res.data : ''
        if (resource.value.resourceType === 'code') codePreview.value = raw
        if (isMarkdown.value) mdPreview.value = raw ? await renderMarkdown(raw, 'full') : ''
        if (isText.value) txtPreview.value = raw
        if (!raw) contentError.value = '文件内容为空'
      } catch (e) {
        codePreview.value = ''; mdPreview.value = ''; txtPreview.value = ''
        contentError.value = '文件不存在或已被删除，无法在线预览，可尝试重新上传'
      }
    } else {
      codePreview.value = ''; mdPreview.value = ''; txtPreview.value = ''
    }
  } catch (err) {
    antMessage.error(err?.description || '加载资源详情失败')
    resource.value = null
  } finally {
    loading.value = false
  }
}

const loadRelated = async () => {
  if (!resource.value) return
  relatedLoading.value = true
  try {
    // 优先按标签查（最相关），无标签按分类查
    const params = {}
    if (resource.value.tag) params.tag = resource.value.tag
    else if (resource.value.category) params.category = resource.value.category
    const res = await getResourceList(params)
    // 只推荐已公开资源，排除当前资源
    let items = (Array.isArray(res.data) ? res.data : [])
      .filter((item) => item.id !== resource.value.id && item.status === 'enabled')
    // 不足 6 个：补充热门资源（按下载量降序），保证推荐区始终填满
    if (items.length < 6) {
      const hotRes = await getResourceList({ pageSize: 20 })
      const existIds = new Set(items.map((i) => i.id))
      const fill = (Array.isArray(hotRes.data) ? hotRes.data : [])
        .filter((item) => item.id !== resource.value.id
          && item.status === 'enabled'
          && !existIds.has(item.id))
        .sort((a, b) => (b.downloadCount || 0) - (a.downloadCount || 0))
      items = items.concat(fill)
    }
    relatedResources.value = items.slice(0, 6)
  } catch (err) {
    relatedResources.value = []
  } finally {
    relatedLoading.value = false
  }
}

const handleDownload = () => {
  if (!resource.value?.downloadUrl) {
    antMessage.warning('该资源暂无下载链接')
    return
  }
  window.open(`/api/resource/download/${resource.value.id}`, '_blank')
}

const handleOpenRelated = (item) => {
  router.push(`/user/resourceDetail/${item.id}`)
}

const getFileExt = (urlOrName) => {
  if (!urlOrName) return ''
  const clean = String(urlOrName).split('?')[0]
  const parts = clean.split('.')
  return parts.length > 1 ? parts.pop().toLowerCase() : ''
}

const fileExt = computed(() => getFileExt(resource.value?.downloadUrl || resource.value?.fileName))

const isPdf = computed(() => fileExt.value === 'pdf')
const isImage = computed(() => ['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'].includes(fileExt.value))
const isVideo = computed(() => ['mp4', 'webm', 'ogg', 'mov'].includes(fileExt.value) || resource.value?.resourceType === 'video')
const isMarkdown = computed(() => ['md', 'markdown'].includes(fileExt.value))
const isText = computed(() => ['txt', 'text'].includes(fileExt.value))

const langMap = { js: 'javascript', ts: 'typescript', py: 'python', java: 'java', css: 'css', html: 'xml', xml: 'xml', sql: 'sql', sh: 'bash', json: 'json', jsx: 'javascript', tsx: 'typescript', vue: 'xml' }
const codeLang = computed(() => langMap[fileExt.value] || 'plaintext')

watch(codePreview, async (val) => {
  if (val && codeRef.value) {
    await nextTick()
    codeRef.value.querySelectorAll('pre code').forEach(block => {
      hljs.highlightElement(block)
    })
  }
})

const previewSource = computed(() => resource.value?.downloadUrl || '')

// 在新标签内嵌预览：走后端 inline 流式代理（OSS 直连/attachment 会触发直接下载）
const openPreviewNewTab = () => {
  if (!resource.value?.id) return
  window.open(`/api/resource/preview/${resource.value.id}`, '_blank')
}

// PDF iframe 预览源（同样走后端 inline 代理；video/img 嵌入式标签不受 disposition 影响，继续用 OSS 直连）
const pdfPreviewSource = computed(() => resource.value?.id ? `/api/resource/preview/${resource.value.id}` : '')

onMounted(async () => {
  await loadResource()
  await loadRelated()
})
</script>

<template>
  <div class="resource-detail-page">
    <a-spin :spinning="loading">
      <a-card v-if="resource" class="resource-header" :bordered="false">
        <div class="header-grid">
          <div class="cover-box">
            <img v-if="resource.coverImage" :src="resource.coverImage" :alt="resource.title || resource.name" />
            <div v-else class="cover-placeholder">
              <component :is="currentTypeMeta.icon" />
            </div>
          </div>
          <div class="info-box">
            <div class="title-row">
              <h1>{{ resource.title || resource.name }}</h1>
              <a-tag :color="resource.status === 'enabled' ? 'green' : resource.status === 'pending' ? 'gold' : 'red'">
                {{ statusLabel[resource.status] || resource.status || '未知状态' }}
              </a-tag>
            </div>
            <div class="meta-row">
              <a-tag>{{ currentTypeMeta.label }}</a-tag>
              <a-tag v-if="resource.category">{{ resource.category }}</a-tag>
              <a-tag v-if="resource.tag">{{ resource.tag }}</a-tag>
            </div>
            <p class="description">{{ resource.description || '暂无描述' }}</p>
            <div class="stats-row">
              <span>文件大小：{{ resource.fileSize || '--' }}</span>
              <span>下载次数：{{ resource.downloadCount || 0 }}</span>
              <span>更新时间：{{ resource.updateTime || '--' }}</span>
            </div>
            <div class="action-row">
              <a-button type="primary" @click="handleDownload" :disabled="!resource.downloadUrl">
                <DownloadOutlined /> 下载资源
              </a-button>
            </div>
          </div>
        </div>
      </a-card>

      <a-row :gutter="16" style="margin-top: 16px">
        <a-col :xs="24" :lg="16">
            <a-card title="在线预览" :bordered="false" class="preview-card">
              <div style="display:flex; justify-content:flex-end; gap: 8px; margin-bottom:8px">
                <a-button type="link" v-if="previewSource" @click="openPreviewNewTab">在新标签中打开预览</a-button>
              </div>

              <template v-if="isVideo">
                <video v-if="previewSource" :src="previewSource" controls style="width: 100%; max-height: 560px; border-radius: 12px" />
                <a-empty v-else description="暂无可预览视频" />
              </template>

              <template v-else-if="isPdf">
                <iframe
                  v-if="pdfPreviewSource"
                  :src="pdfPreviewSource"
                  class="preview-frame"
                  title="资源预览"
                />
                <a-empty v-else description="暂无可预览文档" />
              </template>

              <template v-else-if="isImage">
                <img v-if="previewSource" :src="previewSource" alt="图片预览" style="width:100%; border-radius:12px" />
                <a-empty v-else description="暂无可预览图片" />
              </template>

              <template v-else-if="resource?.resourceType === 'code'">
                <div class="code-preview" ref="codeRef">
                  <pre v-if="codePreview"><code :class="codeLang">{{ codePreview }}</code></pre>
                  <a-empty v-else :description="contentError || '当前代码资源未提供可直接读取的原文，建议下载查看'" />
                </div>
              </template>

              <template v-else-if="isMarkdown">
                <div
                  v-if="mdPreview"
                  class="md-preview yuque-markdown-body"
                  style="padding:16px;max-height:560px;overflow:auto;line-height:1.7"
                  v-html="mdPreview"
                ></div>
                <a-empty v-else :description="contentError || 'Markdown 内容加载中或为空，可下载查看'" />
              </template>

              <template v-else-if="isText">
                <pre
                  v-if="txtPreview"
                  style="padding:16px;max-height:560px;overflow:auto;background:#f6f8fa;border-radius:8px;font-size:13px;white-space:pre-wrap;word-break:break-word"
                >{{ txtPreview }}</pre>
                <a-empty v-else :description="contentError || '文本内容加载中或为空，可下载查看'" />
              </template>

              <template v-else>
                <a-empty description="该资源类型暂不支持内嵌预览，可下载查看" />
              </template>
            </a-card>
          </a-col>
        <a-col :xs="24" :lg="8">
          <a-card title="相关推荐" :bordered="false" class="related-card" :loading="relatedLoading">
            <a-list :data-source="relatedResources" :split="false" item-layout="vertical">
              <template #renderItem="{ item }">
                <a-list-item class="related-item" @click="handleOpenRelated(item)">
                  <a-list-item-meta>
                    <template #title>
                      <div class="related-title">{{ item.title || item.name }}</div>
                    </template>
                    <template #description>
                      <div class="related-desc">{{ item.description || '暂无描述' }}</div>
                    </template>
                  </a-list-item-meta>
                  <div class="related-footer">
                    <a-tag>{{ item.category || '未分类' }}</a-tag>
                    <span>{{ item.downloadCount || 0 }} 次下载</span>
                  </div>
                </a-list-item>
              </template>
              <template #empty>
                <a-empty description="暂无相关推荐" />
              </template>
            </a-list>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<style scoped>
.resource-detail-page {
  padding: 16px;
  font-family: var(--font-body);
}
.resource-header {
  border-radius: var(--radius-lg);
  background: var(--color-bg-card);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--color-border-light);
}
.header-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 24px;
}
.cover-box {
  height: 260px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-bg-warm);
  display: flex;
  align-items: center;
  justify-content: center;
}
.cover-box img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cover-placeholder {
  font-size: 64px;
  color: var(--color-accent);
}
.info-box {
  min-width: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.title-row h1 {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  font-family: var(--font-display);
  color: var(--color-primary);
}
.meta-row,
.stats-row,
.action-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.meta-row {
  margin: 14px 0;
}
.description {
  color: var(--color-text-secondary);
  font-size: 0.9375rem;
  line-height: 1.7;
  margin-bottom: 18px;
  white-space: pre-wrap;
}
.stats-row {
  color: var(--color-text-muted);
  font-size: 0.8125rem;
  margin-bottom: 18px;
}
.preview-card,
.related-card {
  min-height: 420px;
  border-radius: var(--radius-lg);
}
.preview-frame {
  width: 100%;
  height: 720px;
  border: 0;
  border-radius: var(--radius-md);
  background: #fff;
}
.code-preview {
  background: var(--color-primary);
  color: #e2e8f0;
  border-radius: var(--radius-md);
  padding: 16px;
  overflow: auto;
  max-height: 720px;
}
.code-preview pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.7;
}
.related-item {
  cursor: pointer;
  padding: 12px 0;
}
.related-title {
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.9375rem;
  color: var(--color-text);
  transition: color var(--duration-fast) var(--ease-in-out);
}
.related-title:hover { color: var(--color-accent); }
.related-desc {
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
  margin-top: 4px;
}
.related-footer {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
@media (max-width: 992px) {
  .header-grid {
    grid-template-columns: 1fr;
  }
  .cover-box {
    height: 220px;
  }
  .preview-frame {
    height: 520px;
  }
}
</style>