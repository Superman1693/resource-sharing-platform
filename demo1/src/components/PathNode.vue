<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  node: {
    type: Object,
    required: true,
    // { id, title, description, status: 'pending'|'in_progress'|'completed', children?: [] }
  },
})

const emit = defineEmits(['toggleStatus'])

const expanded = ref(false)

const hasChildren = computed(() => {
  return props.node.children && props.node.children.length > 0
})

const statusIcon = computed(() => {
  return props.node.status || 'pending'
})

function handleToggle() {
  if (hasChildren.value) {
    expanded.value = !expanded.value
  }
}

function handleStatusClick() {
  emit('toggleStatus', props.node)
}
</script>

<template>
  <div class="path-node" :class="[`path-node--${statusIcon}`]">
    <!-- 连接线 -->
    <div class="path-node__connector">
      <div class="path-node__line path-node__line--top"></div>
      <div class="path-node__dot" @click="handleStatusClick">
        <!-- 未开始：空心圆 -->
        <svg v-if="statusIcon === 'pending'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="12" r="10" />
        </svg>
        <!-- 进行中：脉冲圆点 -->
        <div v-else-if="statusIcon === 'in_progress'" class="path-node__pulse">
          <div class="path-node__pulse-dot"></div>
        </div>
        <!-- 已完成：绿色勾 -->
        <svg v-else viewBox="0 0 24 24" fill="currentColor">
          <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
        </svg>
      </div>
      <div class="path-node__line path-node__line--bottom" :class="{ 'path-node__line--hidden': !hasChildren || !expanded }"></div>
    </div>

    <!-- 内容 -->
    <div class="path-node__content" @click="handleToggle">
      <div class="path-node__header">
        <h4 class="path-node__title">{{ node.title }}</h4>
        <span class="path-node__badge" :class="[`path-node__badge--${statusIcon}`]">
          {{ statusIcon === 'completed' ? '已完成' : statusIcon === 'in_progress' ? '进行中' : '未开始' }}
        </span>
      </div>
      <p class="path-node__desc" v-if="node.description">{{ node.description }}</p>
      <button v-if="hasChildren" class="path-node__expand">
        <svg :class="{ 'path-node__expand-icon--open': expanded }" viewBox="0 0 16 16" fill="currentColor">
          <path d="M4.646 5.646a.5.5 0 0 1 .708 0L8 8.293l2.646-2.647a.5.5 0 0 1 .708.708l-3 3a.5.5 0 0 1-.708 0l-3-3a.5.5 0 0 1 0-.708z" />
        </svg>
        {{ expanded ? '收起子节点' : '展开子节点' }}
      </button>
    </div>

    <!-- 子节点 -->
    <Transition name="expand">
      <div v-if="hasChildren && expanded" class="path-node__children">
        <PathNode
          v-for="child in node.children"
          :key="child.id"
          :node="child"
          @toggle-status="(n) => emit('toggleStatus', n)"
        />
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.path-node {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: var(--space-md);
  padding-bottom: var(--space-sm);
}

.path-node__connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 32px;
  flex-shrink: 0;
}

.path-node__line {
  width: 2px;
  flex: 1;
  min-height: 12px;
  background: var(--color-border);
  transition: background var(--duration-fast) var(--ease-in-out);
}

.path-node__line--hidden {
  visibility: hidden;
}

.path-node--completed .path-node__line {
  background: #16a34a;
}

.path-node--in_progress .path-node__line--top {
  background: var(--color-accent);
}

.path-node__dot {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  transition: transform var(--duration-fast) var(--ease-out);
}

.path-node__dot:hover {
  transform: scale(1.15);
}

.path-node__dot svg {
  width: 28px;
  height: 28px;
}

.path-node--pending .path-node__dot svg {
  color: var(--color-text-muted);
}

.path-node--in_progress .path-node__dot {
  color: var(--color-accent);
}

.path-node--completed .path-node__dot svg {
  color: #16a34a;
}

/* 脉冲动画 */
.path-node__pulse {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.path-node__pulse-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--color-accent);
  position: relative;
}

.path-node__pulse-dot::before {
  content: '';
  position: absolute;
  inset: -6px;
  border-radius: 50%;
  background: var(--color-accent);
  opacity: 0;
  animation: pulse-ring 1.8s var(--ease-out) infinite;
}

@keyframes pulse-ring {
  0% {
    opacity: 0.6;
    transform: scale(0.5);
  }
  100% {
    opacity: 0;
    transform: scale(1.2);
  }
}

.path-node__content {
  flex: 1;
  min-width: 0;
  padding: var(--space-md);
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-in-out);
}

.path-node__content:hover {
  box-shadow: var(--shadow-sm);
  border-color: var(--color-border);
}

.path-node__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.path-node__title {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--color-text);
  margin: 0;
}

.path-node__badge {
  font-size: 0.6875rem;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.02em;
}

.path-node__badge--pending {
  background: var(--color-border-light);
  color: var(--color-text-muted);
}

.path-node__badge--in_progress {
  background: var(--color-accent-glow);
  color: var(--color-accent);
}

.path-node__badge--completed {
  background: rgba(22, 163, 74, 0.1);
  color: #16a34a;
}

.path-node__desc {
  font-size: 0.8125rem;
  color: var(--color-text-secondary);
  margin-top: var(--space-xs);
  line-height: 1.5;
}

.path-node__expand {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: var(--space-sm);
  padding: 4px 8px;
  background: none;
  border: none;
  font-size: 0.75rem;
  color: var(--color-accent);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background var(--duration-fast) var(--ease-in-out);
}

.path-node__expand:hover {
  background: var(--color-accent-glow);
}

.path-node__expand svg {
  width: 14px;
  height: 14px;
  transition: transform var(--duration-fast) var(--ease-out);
}

.path-node__expand-icon--open {
  transform: rotate(180deg);
}

.path-node__children {
  width: 100%;
  padding-left: 48px;
  border-left: 2px dashed var(--color-border);
  margin-left: 16px;
}

/* 展开/收起过渡 */
.expand-enter-active {
  animation: expandIn var(--duration-normal) var(--ease-out);
  overflow: hidden;
}

.expand-leave-active {
  animation: expandIn var(--duration-fast) var(--ease-in-out) reverse;
  overflow: hidden;
}

@keyframes expandIn {
  from {
    opacity: 0;
    max-height: 0;
  }
  to {
    opacity: 1;
    max-height: 800px;
  }
}
</style>
