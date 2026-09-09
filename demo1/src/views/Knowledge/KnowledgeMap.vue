<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { EyeOutlined, LikeOutlined, ArrowLeftOutlined, ZoomInOutlined, ZoomOutOutlined, ExpandOutlined } from '@ant-design/icons-vue'
import { getKnowledgeMap, getKnowledgeNodeContent } from '../../utils/api'
import { formatShortDate } from '../../utils/dateUtils'

const route = useRoute()
const router = useRouter()
const starId = route.params.starId
const loading = ref(false)
const nodeLoading = ref(false)

const mapData = ref({ nodes: [], edges: [] })
const selectedNode = ref(null)
const contentList = ref([])

const nodeColors = { core: '#1890ff', topic: '#52c41a', concept: '#faad14' }
const nodeRadius = { core: 32, topic: 26, concept: 20 }
const routePrefix = route.path.startsWith('/user') ? '/user' : '/main'

const starName = computed(() => mapData.value?.name || (starId ? '星球知识地图' : '知识地图'))

// ── 视口变换状态 ──────────────────────────────────────────
const tx = ref(0)       // 平移 X
const ty = ref(0)       // 平移 Y
const scale = ref(1)    // 缩放比例
const MIN_SCALE = 0.3
const MAX_SCALE = 3

const transform = computed(() => `translate(${tx.value}, ${ty.value}) scale(${scale.value})`)

// ── 拖拽画布 ──────────────────────────────────────────────
const svgRef = ref(null)
const isPanning = ref(false)
const panStart = ref({ x: 0, y: 0 })
const panOrigin = ref({ x: 0, y: 0 })
// 记录 mousedown 位置，用于区分点击和拖拽
const mouseDownPos = ref({ x: 0, y: 0 })
const CLICK_THRESHOLD = 5  // 移动超过5px才算拖拽

const onSvgMouseDown = (e) => {
  // 只响应左键，且目标是背景（非节点）
  if (e.button !== 0) return
  isPanning.value = true
  panStart.value = { x: e.clientX, y: e.clientY }
  panOrigin.value = { x: tx.value, y: ty.value }
  mouseDownPos.value = { x: e.clientX, y: e.clientY }
  e.preventDefault()
}

const onMouseMove = (e) => {
  if (!isPanning.value) return
  tx.value = panOrigin.value.x + (e.clientX - panStart.value.x)
  ty.value = panOrigin.value.y + (e.clientY - panStart.value.y)
}

const onMouseUp = () => {
  isPanning.value = false
}

// ── 滚轮缩放 ──────────────────────────────────────────────
const onWheel = (e) => {
  e.preventDefault()
  const rect = svgRef.value.getBoundingClientRect()
  // 鼠标在 SVG 中的位置
  const mx = e.clientX - rect.left
  const my = e.clientY - rect.top
  const delta = e.deltaY > 0 ? 0.9 : 1.1
  const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale.value * delta))
  // 以鼠标为中心缩放：调整偏移使鼠标指向的内容点不变
  tx.value = mx - (mx - tx.value) * (newScale / scale.value)
  ty.value = my - (my - ty.value) * (newScale / scale.value)
  scale.value = newScale
}

// ── 节点点击（区分拖拽） ──────────────────────────────────
const onNodeClick = async (e, node) => {
  e.stopPropagation()
  const dx = Math.abs(e.clientX - mouseDownPos.value.x)
  const dy = Math.abs(e.clientY - mouseDownPos.value.y)
  if (dx > CLICK_THRESHOLD || dy > CLICK_THRESHOLD) return  // 是拖拽，不触发点击

  // 自动节点（关联笔记）：直接跳转笔记详情
  if (node.noteId) {
    router.push(`${routePrefix}/noteDetail/${node.noteId}`)
    return
  }

  if (selectedNode.value?.id === node.id) return
  selectedNode.value = node
  contentList.value = []
  nodeLoading.value = true
  try {
    const res = await getKnowledgeNodeContent(node.label)
    contentList.value = (res.code === 0 && Array.isArray(res.data)) ? res.data : []
  } catch (_) {
    contentList.value = []
  } finally {
    nodeLoading.value = false
  }
}

// ── 重置视图 ──────────────────────────────────────────────
const resetView = () => {
  tx.value = 0
  ty.value = 0
  scale.value = 1
}

const zoomIn = () => {
  scale.value = Math.min(MAX_SCALE, scale.value * 1.2)
}

const zoomOut = () => {
  scale.value = Math.max(MIN_SCALE, scale.value / 1.2)
}

// ── 数据加载 ──────────────────────────────────────────────
const viewNote = (note) => router.push(`${routePrefix}/noteDetail/${note.id}`)

const formatTime = formatShortDate

const fetchKnowledgeMap = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeMap(starId || undefined)
    if (res.code === 0 && res.data) {
      const d = res.data
      // 用户端地图：过滤被管理员隐藏的节点，及关联这些节点的连线
      const visibleNodes = (d.nodeList || d.nodes || []).filter(n => !n.hidden)
      const visibleIds = new Set(visibleNodes.map(n => n.id))
      const visibleEdges = (d.edgeList || d.edges || []).filter(
        e => visibleIds.has(e.source) && visibleIds.has(e.target)
      )
      mapData.value = {
        nodes: visibleNodes,
        edges: visibleEdges,
        name: d.name,
      }
    }
  } catch (err) {
    console.error('加载知识地图失败', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchKnowledgeMap()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
})

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<template>
  <div class="knowledge-map-page">
    <a-button type="link" @click="router.back()" class="back-button">
      <ArrowLeftOutlined /> 返回
    </a-button>

    <a-card :title="starName" :bordered="false">
      <a-spin :spinning="loading">
        <a-row :gutter="16">
          <!-- 地图画布 -->
          <a-col :xs="24" :md="16">
            <div class="map-canvas-wrap">
              <div v-if="!loading && mapData.nodes.length === 0" class="map-empty">
                <p>暂无知识地图数据</p>
              </div>

              <template v-else>
                <!-- 工具栏 -->
                <div class="map-toolbar">
                  <a-tooltip title="放大">
                    <button class="tool-btn" @click="zoomIn"><ZoomInOutlined /></button>
                  </a-tooltip>
                  <span class="scale-label">{{ Math.round(scale * 100) }}%</span>
                  <a-tooltip title="缩小">
                    <button class="tool-btn" @click="zoomOut"><ZoomOutOutlined /></button>
                  </a-tooltip>
                  <a-tooltip title="重置视图">
                    <button class="tool-btn" @click="resetView"><ExpandOutlined /></button>
                  </a-tooltip>
                </div>

                <svg
                  ref="svgRef"
                  class="map-svg"
                  :class="{ panning: isPanning }"
                  @mousedown="onSvgMouseDown"
                  @wheel.prevent="onWheel"
                >
                  <defs>
                    <filter id="node-shadow">
                      <feDropShadow dx="0" dy="2" stdDeviation="4" flood-color="rgba(0,0,0,0.2)" />
                    </filter>
                    <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse"
                      :patternTransform="`translate(${tx % 40}, ${ty % 40}) scale(${scale})`">
                      <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#eaecef" stroke-width="0.5"/>
                    </pattern>
                  </defs>

                  <!-- 背景网格 -->
                  <rect width="100%" height="100%" fill="url(#grid)" />

                  <!-- 可变换的内容层 -->
                  <g :transform="transform">
                    <!-- 连线 -->
                    <line
                      v-for="edge in mapData.edges"
                      :key="`${edge.source}-${edge.target}`"
                      :x1="mapData.nodes.find(n => n.id === edge.source)?.x || 0"
                      :y1="mapData.nodes.find(n => n.id === edge.source)?.y || 0"
                      :x2="mapData.nodes.find(n => n.id === edge.target)?.x || 0"
                      :y2="mapData.nodes.find(n => n.id === edge.target)?.y || 0"
                      stroke="#c8d0da" stroke-width="1.5"
                    />

                    <!-- 节点 -->
                    <g
                      v-for="node in mapData.nodes"
                      :key="node.id"
                      class="node-group"
                      @click="onNodeClick($event, node)"
                    >
                      <!-- 选中光晕 -->
                      <circle
                        v-if="selectedNode?.id === node.id"
                        :cx="node.x" :cy="node.y"
                        :r="nodeRadius[node.type] + 12"
                        :fill="nodeColors[node.type] || '#8ba7c4'"
                        fill-opacity="0.15"
                        style="pointer-events:none"
                      />
                      <!-- 主圆 -->
                      <circle
                        :cx="node.x" :cy="node.y"
                        :r="nodeRadius[node.type]"
                        :fill="nodeColors[node.type] || '#8ba7c4'"
                        :fill-opacity="selectedNode?.id === node.id ? 1 : 0.82"
                        stroke="#fff" stroke-width="2.5"
                        :filter="selectedNode?.id === node.id ? 'url(#node-shadow)' : ''"
                      />
                      <!-- 标签 -->
                      <text
                        :x="node.x" :y="node.y + 5"
                        text-anchor="middle" fill="#fff"
                        :font-size="node.type === 'core' ? 13 : 11"
                        font-weight="600"
                        style="pointer-events:none;user-select:none"
                      >{{ node.label }}</text>
                    </g>
                  </g>
                </svg>

                <!-- 操作提示 -->
                <div class="map-hint">
                  拖拽画布平移 · 滚轮缩放 · 点击节点查看内容
                </div>
              </template>
            </div>

            <!-- 图例 -->
            <div class="map-legend">
              <span><span class="dot" style="background:var(--color-accent)"></span>核心概念</span>
              <span><span class="dot" style="background:#52c41a"></span>主题</span>
              <span><span class="dot" style="background:#faad14"></span>知识点</span>
            </div>
          </a-col>

          <!-- 内容面板 -->
          <a-col :xs="24" :md="8">
            <div class="content-panel">
              <div v-if="!selectedNode" class="content-hint">
                <div class="hint-icon">🗺️</div>
                <p>点击地图节点<br>查看相关笔记</p>
              </div>
              <template v-else>
                <div class="content-panel-header">
                  <span class="node-badge" :style="{ background: nodeColors[selectedNode.type] || '#8ba7c4' }">
                    {{ selectedNode.label }}
                  </span>
                  <span class="content-count">{{ contentList.length }} 篇相关笔记</span>
                </div>
                <a-spin :spinning="nodeLoading">
                  <div v-if="!nodeLoading && contentList.length === 0" class="no-content">暂无相关笔记</div>
                  <div v-for="item in contentList" :key="item.id" class="content-card" @click="viewNote(item)">
                    <div class="content-card-title">{{ item.title }}</div>
                    <div class="content-card-summary">{{ item.summary }}</div>
                    <div class="content-card-meta">
                      <span>{{ item.author }}</span>
                      <span>{{ formatTime(item.publishTime) }}</span>
                      <span><EyeOutlined /> {{ item.viewCount || 0 }}</span>
                      <span><LikeOutlined /> {{ item.likeCount || 0 }}</span>
                    </div>
                  </div>
                </a-spin>
              </template>
            </div>
          </a-col>
        </a-row>
      </a-spin>
    </a-card>
  </div>
</template>

<style scoped>
.knowledge-map-page { padding: 16px; }
.back-button { margin-bottom: 16px; padding-left: 0; }

.map-canvas-wrap {
  position: relative;
  border: 1px solid #eaecef;
  border-radius: 8px;
  background: #fafbfc;
  overflow: hidden;
  min-height: 500px;
  display: flex;
  flex-direction: column;
}

.map-svg {
  display: block;
  width: 100%;
  height: 500px;
  cursor: grab;
  user-select: none;
}
.map-svg.panning { cursor: grabbing; }

.map-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 500px;
  color: #999;
}

/* 工具栏 */
.map-toolbar {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(255,255,255,0.92);
  border: 1px solid #eaecef;
  border-radius: 8px;
  padding: 4px 8px;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.tool-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #555;
  transition: background 0.15s;
}
.tool-btn:hover { background: #f0f0f0; color: var(--color-accent); }
.scale-label {
  font-size: 11px;
  color: #888;
  min-width: 36px;
  text-align: center;
}

.map-hint {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: #aaa;
  background: rgba(255,255,255,0.85);
  padding: 3px 10px;
  border-radius: 999px;
  pointer-events: none;
  white-space: nowrap;
}

.node-group { cursor: pointer; }
.node-group:hover > circle:last-of-type { fill-opacity: 1 !important; }

.map-legend {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin-top: 10px;
  font-size: 12px;
  color: #666;
}
.dot {
  display: inline-block;
  width: 10px; height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

/* 内容面板 */
.content-panel {
  background: #fff;
  border: 1px solid #eaecef;
  border-radius: 8px;
  min-height: 500px;
  padding: 16px;
  overflow-y: auto;
}
.content-hint {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  height: 460px; color: #999; text-align: center;
}
.hint-icon { font-size: 48px; margin-bottom: 16px; }
.content-panel-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0;
}
.node-badge {
  color: #fff; padding: 4px 12px;
  border-radius: 999px; font-size: 13px; font-weight: 600;
}
.content-count { font-size: 12px; color: #999; }
.no-content { text-align: center; padding: 40px 0; color: #999; font-size: 13px; }
.content-card {
  padding: 12px; border-radius: 6px;
  border: 1px solid #f0f0f0; margin-bottom: 10px;
  cursor: pointer; transition: all 0.2s;
}
.content-card:hover { border-color: var(--color-accent); box-shadow: 0 2px 8px var(--color-accent-glow); }
.content-card-title {
  font-size: 14px; font-weight: 500; color: #1d2129;
  margin-bottom: 4px; overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap;
}
.content-card-summary {
  font-size: 12px; color: #86909c; margin-bottom: 8px;
  overflow: hidden; display: -webkit-box;
  -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.content-card-meta {
  display: flex; gap: 10px;
  font-size: 11px; color: #c9cdd4; flex-wrap: wrap;
}
</style>
