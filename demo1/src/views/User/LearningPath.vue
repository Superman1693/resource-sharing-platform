<script setup>
import { ref, computed, onMounted } from 'vue'
import PathNode from '../../components/PathNode.vue'

// ===== Mock 数据 =====
const paths = ref([])

const selectedPathId = ref(null)

const selectedPath = computed(() => {
  return paths.value.find((p) => p.id === selectedPathId.value) || null
})

// 整体进度
const progress = computed(() => {
  if (!selectedPath.value) return 0
  const nodes = flattenNodes(selectedPath.value.nodes)
  if (nodes.length === 0) return 0
  const completed = nodes.filter((n) => n.status === 'completed').length
  return Math.round((completed / nodes.length) * 100)
})

const totalNodes = computed(() => {
  if (!selectedPath.value) return 0
  return flattenNodes(selectedPath.value.nodes).length
})

const completedNodes = computed(() => {
  if (!selectedPath.value) return 0
  return flattenNodes(selectedPath.value.nodes).filter((n) => n.status === 'completed').length
})

function flattenNodes(nodes) {
  const result = []
  function walk(list) {
    list.forEach((n) => {
      result.push(n)
      if (n.children && n.children.length > 0) {
        walk(n.children)
      }
    })
  }
  walk(nodes)
  return result
}

// 切换节点状态
function handleToggleStatus(node) {
  if (node.status === 'completed') {
    node.status = 'pending'
  } else if (node.status === 'pending') {
    node.status = 'in_progress'
  } else {
    node.status = 'completed'
  }
}

onMounted(() => {
  // 模拟学习路径数据
  paths.value = [
    {
      id: 1,
      title: 'Vue 3 全栈开发之路',
      description: '从零到一掌握 Vue 3 生态，构建现代化 Web 应用',
      nodes: [
        {
          id: 'n1',
          title: 'JavaScript 基础巩固',
          description: 'ES6+ 语法、异步编程、模块化',
          status: 'completed',
          children: [
            { id: 'n1-1', title: 'ES6 核心特性', description: '解构、箭头函数、Promise', status: 'completed' },
            { id: 'n1-2', title: '异步编程', description: 'async/await、事件循环', status: 'completed' },
          ],
        },
        {
          id: 'n2',
          title: 'Vue 3 核心',
          description: 'Composition API、响应式系统、组件化',
          status: 'completed',
          children: [
            { id: 'n2-1', title: '响应式原理', description: 'ref、reactive、computed', status: 'completed' },
            { id: 'n2-2', title: '组件通信', description: 'props、emit、provide/inject', status: 'completed' },
          ],
        },
        {
          id: 'n3',
          title: 'Vue Router & Pinia',
          description: '路由管理和状态管理',
          status: 'in_progress',
          children: [
            { id: 'n3-1', title: '路由配置与守卫', description: '动态路由、权限控制', status: 'completed' },
            { id: 'n3-2', title: 'Pinia 状态管理', description: 'Store 定义、模块化', status: 'in_progress' },
          ],
        },
        {
          id: 'n4',
          title: 'UI 框架与工程化',
          description: 'Ant Design Vue、Vite、TypeScript',
          status: 'pending',
          children: [
            { id: 'n4-1', title: 'Ant Design Vue 深入', description: '主题定制、组件扩展', status: 'pending' },
            { id: 'n4-2', title: 'TypeScript 集成', description: '类型定义、泛型组件', status: 'pending' },
          ],
        },
        {
          id: 'n5',
          title: '后端与全栈',
          description: 'Node.js、数据库、部署',
          status: 'pending',
          children: [
            { id: 'n5-1', title: 'Node.js & Express', description: 'RESTful API、中间件', status: 'pending' },
            { id: 'n5-2', title: '数据库设计', description: 'MySQL、MongoDB', status: 'pending' },
            { id: 'n5-3', title: 'Docker 部署', description: '容器化、CI/CD', status: 'pending' },
          ],
        },
      ],
    },
    {
      id: 2,
      title: '算法与数据结构',
      description: '系统学习常见算法和数据结构，提升编程能力',
      nodes: [
        {
          id: 'a1',
          title: '基础数据结构',
          description: '数组、链表、栈、队列',
          status: 'completed',
        },
        {
          id: 'a2',
          title: '树与图',
          description: '二叉树、BFS、DFS',
          status: 'in_progress',
        },
        {
          id: 'a3',
          title: '排序与搜索',
          description: '快速排序、二分查找',
          status: 'pending',
        },
        {
          id: 'a4',
          title: '动态规划',
          description: '背包问题、最优子结构',
          status: 'pending',
        },
      ],
    },
  ]

  // 默认选中第一条路径
  if (paths.value.length > 0) {
    selectedPathId.value = paths.value[0].id
  }
})
</script>

<template>
  <div class="learning-path">
    <!-- 页面标题 -->
    <div class="learning-path__header">
      <h2 class="learning-path__title">学习路径规划</h2>
      <p class="learning-path__subtitle">规划你的学习旅程，一步步达成目标</p>
    </div>

    <!-- 路径选择 -->
    <div class="learning-path__tabs">
      <button
        v-for="path in paths"
        :key="path.id"
        class="learning-path__tab"
        :class="{ 'learning-path__tab--active': path.id === selectedPathId }"
        @click="selectedPathId = path.id"
      >
        {{ path.title }}
      </button>
    </div>

    <!-- 选中路径的概览 -->
    <div v-if="selectedPath" class="learning-path__overview card">
      <div class="learning-path__overview-header">
        <div>
          <h3 class="learning-path__overview-title">{{ selectedPath.title }}</h3>
          <p class="learning-path__overview-desc">{{ selectedPath.description }}</p>
        </div>
        <div class="learning-path__progress-ring">
          <svg viewBox="0 0 80 80">
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              stroke="var(--color-border-light)"
              stroke-width="6"
            />
            <circle
              cx="40" cy="40" r="34"
              fill="none"
              stroke="var(--color-accent)"
              stroke-width="6"
              stroke-linecap="round"
              :stroke-dasharray="2 * Math.PI * 34"
              :stroke-dashoffset="2 * Math.PI * 34 * (1 - progress / 100)"
              transform="rotate(-90 40 40)"
              style="transition: stroke-dashoffset 0.6s var(--ease-out)"
            />
          </svg>
          <span class="learning-path__progress-text">{{ progress }}%</span>
        </div>
      </div>

      <!-- 进度条 -->
      <div class="learning-path__progress-bar">
        <div
          class="learning-path__progress-fill"
          :style="{ width: progress + '%' }"
        ></div>
      </div>
      <div class="learning-path__progress-info">
        <span>已完成 {{ completedNodes }} / {{ totalNodes }} 个节点</span>
        <span>{{ progress >= 100 ? '恭喜完成全部学习!' : '继续加油' }}</span>
      </div>
    </div>

    <!-- 路径时间线 -->
    <div v-if="selectedPath" class="learning-path__timeline">
      <PathNode
        v-for="node in selectedPath.nodes"
        :key="node.id"
        :node="node"
        @toggle-status="handleToggleStatus"
      />
    </div>

    <!-- 空状态 -->
    <div v-if="paths.length === 0" class="learning-path__empty">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="learning-path__empty-icon">
        <path d="M9 20l-5.447-2.724A1 1 0 0 1 3 16.382V5.618a1 1 0 0 1 1.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0 0 21 18.382V7.618a1 1 0 0 0-.553-.894L15 4m0 13V4m0 0L9 7" />
      </svg>
      <p>暂无学习路径</p>
    </div>
  </div>
</template>

<style scoped>
.learning-path {
  max-width: 900px;
  margin: 0 auto;
  padding: var(--space-2xl) var(--space-lg);
  animation: slideUpFade 0.5s var(--ease-out) both;
}

.learning-path__header {
  margin-bottom: var(--space-2xl);
}

.learning-path__title {
  font-family: var(--font-display);
  font-size: 2rem;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: var(--space-xs);
}

.learning-path__subtitle {
  font-size: 0.9375rem;
  color: var(--color-text-secondary);
}

/* 路径选择标签 */
.learning-path__tabs {
  display: flex;
  gap: var(--space-sm);
  margin-bottom: var(--space-lg);
  flex-wrap: wrap;
}

.learning-path__tab {
  padding: var(--space-sm) var(--space-lg);
  border-radius: 999px;
  font-size: 0.875rem;
  font-weight: 500;
  background: var(--color-bg-card);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.learning-path__tab:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.learning-path__tab--active {
  background: var(--color-accent);
  color: #fff;
  border-color: var(--color-accent);
}

.learning-path__tab--active:hover {
  background: #1d4ed8;
  color: #fff;
}

/* 概览卡片 */
.learning-path__overview {
  padding: var(--space-xl);
  margin-bottom: var(--space-2xl);
}

.learning-path__overview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.learning-path__overview-title {
  font-family: var(--font-body);
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--color-primary);
  margin-bottom: var(--space-xs);
}

.learning-path__overview-desc {
  font-size: 0.875rem;
  color: var(--color-text-secondary);
}

/* 进度环 */
.learning-path__progress-ring {
  position: relative;
  width: 80px;
  height: 80px;
  flex-shrink: 0;
}

.learning-path__progress-ring svg {
  width: 100%;
  height: 100%;
}

.learning-path__progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-family: var(--font-display);
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--color-accent);
}

/* 进度条 */
.learning-path__progress-bar {
  width: 100%;
  height: 8px;
  background: var(--color-border-light);
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: var(--space-sm);
}

.learning-path__progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-accent), var(--color-accent-light));
  border-radius: 999px;
  transition: width 0.6s var(--ease-out);
}

.learning-path__progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
}

/* 时间线 */
.learning-path__timeline {
  position: relative;
  padding-left: var(--space-xs);
}

/* 空状态 */
.learning-path__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--space-3xl) var(--space-lg);
  color: var(--color-text-muted);
}

.learning-path__empty-icon {
  width: 64px;
  height: 64px;
  margin-bottom: var(--space-md);
  opacity: 0.4;
}

.learning-path__empty p {
  font-size: 1rem;
  font-weight: 500;
}

/* 响应式 */
@media (max-width: 640px) {
  .learning-path__overview-header {
    flex-direction: column-reverse;
    align-items: center;
    text-align: center;
  }
}
</style>
