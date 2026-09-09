<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message as antMessage } from 'ant-design-vue'
import {
  PlusOutlined, DeleteOutlined, ArrowLeftOutlined,
  SaveOutlined, LinkOutlined, DisconnectOutlined, DragOutlined
} from '@ant-design/icons-vue'
import { getKnowledgeMap, saveKnowledgeMap, adminGetStarList, syncKnowledgeMap } from '../../utils/api'

const route = useRoute()
const router = useRouter()

// starId 来自路由参数或 query
const routeStarId = route.params.starId || route.query.starId
const selectedStarId = ref(routeStarId || null)

const loading = ref(false)
const saving = ref(false)
const starOptions = ref([])   // 星球下拉选项

// ── 地图数据 ──────────────────────────────────────────────
const mapData = ref({ nodes: [], edges: [] })

// ── 交互状态 ──────────────────────────────────────────────
const mode = ref('select')   // 'select' | 'edge' | 'drag'
const selectedNode = ref(null)
const selectedEdgeKey = ref(null)
const firstEdgeNode = ref(null)   // 连线模式第一个节点

// 拖拽状态
const dragging = ref(false)
const dragNode = ref(null)
const dragOffset = ref({ x: 0, y: 0 })

// SVG 画布尺寸
const SVG_W = 800
const SVG_H = 520

// ── 节点表单 ──────────────────────────────────────────────
const showNodeForm = ref(false)
const isEditingNode = ref(false)
const nodeForm = reactive({ id: '', label: '', type: 'concept' })

const nodeTypeOptions = [
  { label: '核心概念', value: 'core' },
  { label: '主题', value: 'topic' },
  { label: '知识点', value: 'concept' },
]
const nodeColors = { core: '#1890ff', topic: '#52c41a', concept: '#faad14' }
const nodeRadius = { core: 34, topic: 26, concept: 20 }

// ── 计算属性 ──────────────────────────────────────────────
const edgeList = computed(() =>
  mapData.value.edges.map(e => {
    const s = mapData.value.nodes.find(n => n.id === e.source)
    const t = mapData.value.nodes.find(n => n.id === e.target)
    return { ...e, key: `${e.source}-${e.target}`, sx: s?.x, sy: s?.y, tx: t?.x, ty: t?.y, valid: !!s && !!t }
  }).filter(e => e.valid)
)

// ── 星球加载 ──────────────────────────────────────────────
const loadStars = async () => {
  try {
    const res = await adminGetStarList()
    starOptions.value = (Array.isArray(res.data) ? res.data : []).map(s => ({
      label: s.name,
      value: String(s.id),
    }))
  } catch (_) {}
}

// ── 地图加载 ──────────────────────────────────────────────
const loadMap = async () => {
  if (!selectedStarId.value) return
  loading.value = true
  try {
    const res = await getKnowledgeMap(selectedStarId.value)
    if (res.code === 0 && res.data) {
      const d = res.data
      mapData.value = {
        nodes: (d.nodeList || d.nodes || []).map(n => ({ ...n })),
        edges: (d.edgeList || d.edges || []).map(e => ({ ...e })),
      }
    } else {
      mapData.value = { nodes: [], edges: [] }
    }
  } catch (_) {
    mapData.value = { nodes: [], edges: [] }
  } finally {
    loading.value = false
  }
}

const handleStarChange = (val) => {
  selectedStarId.value = val
  selectedNode.value = null
  selectedEdgeKey.value = null
  firstEdgeNode.value = null
  loadMap()
}

// ── 保存 ──────────────────────────────────────────────────
const handleSave = async () => {
  if (!selectedStarId.value) {
    antMessage.warning('请先选择星球')
    return
  }
  saving.value = true
  try {
    await saveKnowledgeMap({
      starId: selectedStarId.value,
      nodeList: mapData.value.nodes,
      edgeList: mapData.value.edges,
    })
    antMessage.success('保存成功')
  } catch (err) {
    antMessage.error(err?.description || '保存失败')
  } finally {
    saving.value = false
  }
}

// ── 节点操作 ──────────────────────────────────────────────
const openAddNode = () => {
  isEditingNode.value = false
  Object.assign(nodeForm, { id: `n_${Date.now()}`, label: '', type: 'concept' })
  showNodeForm.value = true
}

const openEditNode = (node) => {
  isEditingNode.value = true
  Object.assign(nodeForm, { id: node.id, label: node.label, type: node.type })
  showNodeForm.value = true
}

const confirmNodeForm = () => {
  if (!nodeForm.label.trim()) { antMessage.warning('请输入节点名称'); return }
  if (isEditingNode.value) {
    const n = mapData.value.nodes.find(n => n.id === nodeForm.id)
    if (n) { n.label = nodeForm.label; n.type = nodeForm.type }
    antMessage.success('节点已更新')
  } else {
    // 自动布局：放在画布中心附近随机偏移
    const cx = SVG_W / 2 + (Math.random() - 0.5) * 200
    const cy = SVG_H / 2 + (Math.random() - 0.5) * 160
    mapData.value.nodes.push({ id: nodeForm.id, label: nodeForm.label, type: nodeForm.type, x: Math.round(cx), y: Math.round(cy), auto: false, hidden: false })
    antMessage.success('节点已添加')
  }
  showNodeForm.value = false
}

const deleteNode = (node) => {
  // 自动节点（关联笔记）不可删除，只能隐藏
  if (node.auto) {
    antMessage.warning('自动节点随笔记存在，不可删除，已改用隐藏')
    toggleNodeHidden(node)
    return
  }
  mapData.value.nodes = mapData.value.nodes.filter(n => n.id !== node.id)
  mapData.value.edges = mapData.value.edges.filter(e => e.source !== node.id && e.target !== node.id)
  if (selectedNode.value?.id === node.id) selectedNode.value = null
  antMessage.success('节点已删除')
}

// 切换节点隐藏/显示（用户端地图不显示 hidden 节点）
const toggleNodeHidden = (node) => {
  node.hidden = !node.hidden
  antMessage.success(node.hidden ? '节点已隐藏（用户端不可见）' : '节点已显示')
}

// 全量重建：按星球笔记重新生成自动节点+连线，保留手动节点与已调整布局
const regenerating = ref(false)
const handleRegenerate = async () => {
  if (!selectedStarId.value) { antMessage.warning('请先选择星球'); return }
  regenerating.value = true
  try {
    await syncKnowledgeMap(selectedStarId.value)
    antMessage.success('已根据星球笔记重新生成')
    await loadMap()
  } catch (err) {
    antMessage.error(err?.description || '重新生成失败')
  } finally {
    regenerating.value = false
  }
}

// ── 连线操作 ──────────────────────────────────────────────
const deleteEdge = (key) => {
  const [src, tgt] = key.split('-')
  mapData.value.edges = mapData.value.edges.filter(e => !(e.source === src && e.target === tgt))
  if (selectedEdgeKey.value === key) selectedEdgeKey.value = null
  antMessage.success('连线已删除')
}

// ── SVG 交互 ──────────────────────────────────────────────
const svgRef = ref(null)

const getSVGPoint = (e) => {
  const rect = svgRef.value.getBoundingClientRect()
  const scaleX = SVG_W / rect.width
  const scaleY = SVG_H / rect.height
  return {
    x: (e.clientX - rect.left) * scaleX,
    y: (e.clientY - rect.top) * scaleY,
  }
}

const onNodeMouseDown = (e, node) => {
  if (mode.value === 'edge') return
  e.stopPropagation()
  dragging.value = true
  dragNode.value = node
  const pt = getSVGPoint(e)
  dragOffset.value = { x: pt.x - node.x, y: pt.y - node.y }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

const onMouseMove = (e) => {
  if (!dragging.value || !dragNode.value) return
  const pt = getSVGPoint(e)
  const n = mapData.value.nodes.find(n => n.id === dragNode.value.id)
  if (n) {
    n.x = Math.max(30, Math.min(SVG_W - 30, Math.round(pt.x - dragOffset.value.x)))
    n.y = Math.max(30, Math.min(SVG_H - 30, Math.round(pt.y - dragOffset.value.y)))
  }
}

const onMouseUp = () => {
  dragging.value = false
  dragNode.value = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

const onNodeClick = (e, node) => {
  e.stopPropagation()
  if (mode.value === 'edge') {
    if (!firstEdgeNode.value) {
      firstEdgeNode.value = node
      antMessage.info(`已选「${node.label}」，再点击目标节点完成连线`)
    } else {
      if (firstEdgeNode.value.id === node.id) {
        antMessage.warning('不能连接自身')
        firstEdgeNode.value = null
        return
      }
      const exists = mapData.value.edges.some(
        e => (e.source === firstEdgeNode.value.id && e.target === node.id) ||
             (e.source === node.id && e.target === firstEdgeNode.value.id)
      )
      if (exists) { antMessage.warning('连线已存在'); firstEdgeNode.value = null; return }
      mapData.value.edges.push({ source: firstEdgeNode.value.id, target: node.id })
      antMessage.success(`${firstEdgeNode.value.label} → ${node.label}`)
      firstEdgeNode.value = null
      mode.value = 'select'
    }
    return
  }
  selectedNode.value = node
  selectedEdgeKey.value = null
}

const onEdgeClick = (e, key) => {
  e.stopPropagation()
  selectedEdgeKey.value = key
  selectedNode.value = null
}

const onCanvasClick = () => {
  selectedNode.value = null
  selectedEdgeKey.value = null
  if (mode.value === 'edge' && firstEdgeNode.value) firstEdgeNode.value = null
}

onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})

onMounted(async () => {
  await loadStars()
  if (selectedStarId.value) loadMap()
})
</script>

<template>
  <div class="kme-page">
    <!-- 顶部工具栏 -->
    <div class="kme-toolbar">
      <div class="toolbar-left">
        <a-button type="text" @click="router.back()">
          <ArrowLeftOutlined /> 返回
        </a-button>
        <span class="toolbar-title">知识地图编辑器</span>
        <a-select
          v-model:value="selectedStarId"
          :options="starOptions"
          placeholder="选择星球"
          style="width: 200px"
          allow-clear
          @change="handleStarChange"
        />
      </div>
      <div class="toolbar-right">
        <!-- 模式切换 -->
        <a-radio-group v-model:value="mode" button-style="solid" size="small">
          <a-radio-button value="select">
            <DragOutlined /> 选择/拖拽
          </a-radio-button>
          <a-radio-button value="edge">
            <LinkOutlined /> 连线
          </a-radio-button>
        </a-radio-group>
        <a-button :loading="regenerating" @click="handleRegenerate">
          自动生成
        </a-button>
        <a-button type="primary" :loading="saving" @click="handleSave">
          <SaveOutlined /> 保存
        </a-button>
      </div>
    </div>

    <div class="kme-body">
      <!-- 左侧画布 -->
      <div class="kme-canvas-wrap">
        <a-spin :spinning="loading">
          <div class="canvas-hint" v-if="mode === 'edge'">
            <LinkOutlined /> 连线模式：依次点击两个节点创建连线
            <span v-if="firstEdgeNode"> · 已选「{{ firstEdgeNode.label }}」</span>
            <a-button type="link" size="small" @click="() => { mode = 'select'; firstEdgeNode = null }">取消</a-button>
          </div>
          <svg
            ref="svgRef"
            :width="SVG_W"
            :height="SVG_H"
            class="kme-svg"
            :class="{ 'mode-edge': mode === 'edge', 'mode-drag': mode === 'select' }"
            @click="onCanvasClick"
          >
            <defs>
              <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                <path d="M0,0 L0,6 L8,3 z" fill="#b0b8c1" />
              </marker>
              <filter id="glow">
                <feGaussianBlur stdDeviation="3" result="blur" />
                <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
              </filter>
            </defs>

            <!-- 连线 -->
            <g v-for="edge in edgeList" :key="edge.key">
              <!-- 加宽透明区域方便点击 -->
              <line
                :x1="edge.sx" :y1="edge.sy" :x2="edge.tx" :y2="edge.ty"
                stroke="transparent" stroke-width="12" style="cursor:pointer"
                @click="onEdgeClick($event, edge.key)"
              />
              <line
                :x1="edge.sx" :y1="edge.sy" :x2="edge.tx" :y2="edge.ty"
                :stroke="selectedEdgeKey === edge.key ? '#ff4d4f' : '#c8d0da'"
                :stroke-width="selectedEdgeKey === edge.key ? 2.5 : 1.8"
                stroke-dasharray="none"
                marker-end="url(#arrow)"
                style="pointer-events:none"
              />
            </g>

            <!-- 节点 -->
            <g
              v-for="node in mapData.nodes"
              :key="node.id"
              @mousedown="onNodeMouseDown($event, node)"
              @click="onNodeClick($event, node)"
              :style="{ cursor: mode === 'select' ? 'grab' : 'pointer' }"
            >
              <!-- 选中光晕 -->
              <circle
                v-if="selectedNode?.id === node.id || firstEdgeNode?.id === node.id"
                :cx="node.x" :cy="node.y"
                :r="nodeRadius[node.type] + 10"
                :fill="nodeColors[node.type]"
                fill-opacity="0.15"
                style="pointer-events:none"
              />
              <!-- 主圆 -->
              <circle
                :cx="node.x" :cy="node.y"
                :r="nodeRadius[node.type]"
                :fill="nodeColors[node.type]"
                :fill-opacity="selectedNode?.id === node.id ? 1 : 0.82"
                stroke="#fff"
                :stroke-width="selectedNode?.id === node.id ? 3 : 2"
                :filter="selectedNode?.id === node.id ? 'url(#glow)' : ''"
              />
              <!-- 标签 -->
              <text
                :x="node.x" :y="node.y + 5"
                text-anchor="middle"
                fill="#fff"
                :font-size="node.type === 'core' ? 13 : 11"
                font-weight="600"
                style="pointer-events:none;user-select:none"
              >{{ node.label }}</text>
            </g>
          </svg>
        </a-spin>

        <!-- 图例 -->
        <div class="kme-legend">
          <span v-for="(color, type) in nodeColors" :key="type">
            <span class="legend-dot" :style="{ background: color }"></span>
            {{ { core: '核心概念', topic: '主题', concept: '知识点' }[type] }}
          </span>
          <span style="margin-left: auto; color: #999; font-size: 11px">
            拖拽节点调整位置 · 点击连线可删除
          </span>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="kme-panel">
        <!-- 节点操作 -->
        <div class="panel-section">
          <div class="panel-section-header">
            <span>节点 ({{ mapData.nodes.length }})</span>
            <a-button type="primary" size="small" @click="openAddNode">
              <PlusOutlined /> 添加
            </a-button>
          </div>

          <!-- 选中节点详情 -->
          <div v-if="selectedNode" class="selected-node-card">
            <div class="sn-header">
              <span class="sn-badge" :style="{ background: nodeColors[selectedNode.type] }">
                {{ selectedNode.label }}
              </span>
              <a-space size="small">
                <a-button size="small" @click="openEditNode(selectedNode)">编辑</a-button>
                <a-button size="small" @click="toggleNodeHidden(selectedNode)">
                  {{ selectedNode.hidden ? '显示' : '隐藏' }}
                </a-button>
                <a-popconfirm v-if="!selectedNode.auto" title="确认删除该节点及其连线？" @confirm="deleteNode(selectedNode)">
                  <a-button size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </div>
            <div class="sn-meta">
              类型：{{ { core: '核心概念', topic: '主题', concept: '知识点' }[selectedNode.type] }}
              <template v-if="selectedNode.auto"> · 自动节点（关联笔记）</template>
              <template v-if="selectedNode.hidden"> · 已隐藏</template>
              &nbsp;·&nbsp; 坐标：({{ selectedNode.x }}, {{ selectedNode.y }})
            </div>
            <div v-if="selectedNode.noteId" class="sn-note-link">
              <a-button type="link" size="small" @click="router.push(`/user/noteDetail/${selectedNode.noteId}`)">
                查看关联笔记 →
              </a-button>
            </div>
          </div>

          <!-- 节点列表 -->
          <div class="node-list">
            <div
              v-for="node in mapData.nodes"
              :key="node.id"
              class="node-item"
              :class="{ active: selectedNode?.id === node.id, 'node-hidden': node.hidden }"
              @click="selectedNode = node; selectedEdgeKey = null"
            >
              <span class="node-dot" :style="{ background: nodeColors[node.type] }"></span>
              <span class="node-label">{{ node.label }}</span>
              <span class="node-type-tag">{{ { core: '核心', topic: '主题', concept: '知识点' }[node.type] }}</span>
            </div>
            <div v-if="mapData.nodes.length === 0" class="empty-tip">暂无节点，点击"添加"创建</div>
          </div>
        </div>

        <!-- 连线列表 -->
        <div class="panel-section" style="margin-top: 16px">
          <div class="panel-section-header">
            <span>连线 ({{ edgeList.length }})</span>
            <a-button
              size="small"
              :type="mode === 'edge' ? 'primary' : 'default'"
              @click="() => { mode = mode === 'edge' ? 'select' : 'edge'; firstEdgeNode = null }"
            >
              <LinkOutlined /> {{ mode === 'edge' ? '退出连线' : '添加连线' }}
            </a-button>
          </div>
          <div class="edge-list">
            <div
              v-for="edge in edgeList"
              :key="edge.key"
              class="edge-item"
              :class="{ active: selectedEdgeKey === edge.key }"
              @click="selectedEdgeKey = edge.key; selectedNode = null"
            >
              <span class="edge-label">
                {{ mapData.nodes.find(n => n.id === edge.source)?.label }}
                <span class="edge-arrow">→</span>
                {{ mapData.nodes.find(n => n.id === edge.target)?.label }}
              </span>
              <a-popconfirm title="删除该连线？" @confirm="deleteEdge(edge.key)">
                <a-button type="text" size="small" danger style="padding: 0 4px">
                  <DeleteOutlined />
                </a-button>
              </a-popconfirm>
            </div>
            <div v-if="edgeList.length === 0" class="empty-tip">暂无连线</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 节点表单弹窗 -->
    <a-modal
      v-model:open="showNodeForm"
      :title="isEditingNode ? '编辑节点' : '添加节点'"
      @ok="confirmNodeForm"
      ok-text="确认"
      cancel-text="取消"
      width="400px"
    >
      <a-form layout="vertical" style="margin-top: 12px">
        <a-form-item label="节点名称" required>
          <a-input v-model:value="nodeForm.label" placeholder="请输入节点名称" :maxlength="20" show-count />
        </a-form-item>
        <a-form-item label="节点类型">
          <a-radio-group v-model:value="nodeForm.type">
            <a-radio-button v-for="opt in nodeTypeOptions" :key="opt.value" :value="opt.value">
              <span class="type-dot" :style="{ background: nodeColors[opt.value] }"></span>
              {{ opt.label }}
            </a-radio-button>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.kme-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
  min-height: 600px;
  background: #f7f8fa;
  border-radius: 8px;
  overflow: hidden;
}

/* ── 工具栏 ── */
.kme-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #eaecef;
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  white-space: nowrap;
}

/* ── 主体 ── */
.kme-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  gap: 0;
}

/* ── 画布区 ── */
.kme-canvas-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
  background: #fff;
  border-right: 1px solid #eaecef;
}
.canvas-hint {
  background: #e6f4ff;
  border: 1px solid #91caff;
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 13px;
  color: #0958d9;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.kme-svg {
  border: 1px solid #eaecef;
  border-radius: 8px;
  background: #fafbfc;
  width: 100%;
  max-width: 100%;
  display: block;
}
.kme-svg.mode-edge { cursor: crosshair; }
.kme-svg.mode-drag { cursor: default; }
.kme-legend {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: #666;
  flex-wrap: wrap;
}
.legend-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}

/* ── 右侧面板 ── */
.kme-panel {
  width: 280px;
  flex-shrink: 0;
  background: #fff;
  overflow-y: auto;
  padding: 16px;
}
.panel-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 10px;
}
.selected-node-card {
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.sn-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.sn-badge {
  color: #fff;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.sn-meta {
  font-size: 11px;
  color: #666;
}
.node-list, .edge-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 220px;
  overflow-y: auto;
}
.node-item, .edge-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
  border: 1px solid transparent;
}
.node-item:hover, .edge-item:hover { background: #f4f6f9; }
.node-item.active, .edge-item.active { background: #e6f4ff; border-color: #91caff; }
.node-item.node-hidden { opacity: 0.45; }
.sn-note-link { margin-top: 6px; }
.node-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.node-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-type-tag {
  font-size: 10px;
  color: #999;
  background: #f4f4f5;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.edge-label {
  flex: 1;
  font-size: 12px;
  color: #555;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.edge-arrow { color: var(--color-accent); margin: 0 4px; }
.empty-tip {
  text-align: center;
  padding: 16px 0;
  color: #bbb;
  font-size: 12px;
}
.type-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
</style>
