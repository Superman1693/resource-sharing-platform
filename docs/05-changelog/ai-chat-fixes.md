# AI 聊天组件优化修复说明

## 核心修复点

### 1. stream.js - 流式数据处理
**问题**：
- 全局 `replace(/data:\s*/gi)` 误删正文中的 "data:" 字符
- 与 `api.js` 中同名函数 `sendStreamChatMessage` 冲突
- 数据清洗逻辑分散，导致双重处理

**修复**：
- 新增 `parseSSELine()` 统一处理 SSE 行解析，只清理行首 "data:" 前缀
- 移除全局替换，避免误删正文内容
- 正确使用 `TextDecoder({ stream: true })` 处理跨块多字节字符
- `reader.releaseLock()` 移至 `finally` 确保资源释放

### 2. AIFloatWindow.vue - 聊天窗口
**问题**：
- `formatMessageContent()` 重复清洗数据（stream.js 已处理）
- 使用 `renderKey` 强制刷新 Markdown 渲染，性能差
- Markdown 样式未适配项目主题色 `#1b3557`

**修复**：
- 移除 `formatMessageContent()` 中的数据清洗逻辑，只保留 Markdown 渲染
- 删除 `renderKey` 机制，直接通过响应式对象引用更新内容
- 重写 Markdown 样式，使用主题色 `#1b3557`，代码块背景 `#f4f4f5`
- 简化窗口尺寸和布局，默认 760x560，最小 560x420
- 统一字体为 Inter，与 UserLayout.vue 保持一致

### 3. AIFloatBall.vue - 悬浮球
**问题**：
- Touch 事件未从 `e.touches[0]` 取坐标，移动端拖拽失效
- 图标颜色 `#1b3557`（深色）与背景深色冲突，不可见
- 点击和拖拽未区分，拖拽后误触发 toggle

**修复**：
- 新增 `getEventCoords()` 统一处理 mouse/touch 坐标
- 图标颜色改为 `#ffffff`（白色），与深色背景形成对比
- 新增 `dragMoved` 标志，只有未移动时才触发 toggle
- 添加 `{ passive: false }` 到 touchmove 监听器

### 4. chatMessageProcessor.js - 消息处理器
**问题**：
- 步骤 9 的正则 `/(\S)\s+(\S)/g` 会破坏正常文本中的空格
- 过度处理导致内容损坏

**修复**：
- 移除步骤 9 的空格合并正则
- 简化处理流程，只保留必要的清洗步骤
- 行首 "data:" 清理改为 `/^data:\s*/gim`（多行模式）

## 样式优化

### 主题色统一
- 主色：`#1b3557`（深蓝）
- 辅助色：`#2a4a72`、`#8ba7c4`
- 背景：`#f8fafc`、`#fafafa`
- 边框：`#e4e4e7`、`#f4f4f5`
- 文字：`#18181b`（主）、`#71717a`（次）、`#a1a1aa`（辅）

### 字体规范
- 主字体：Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC'
- 代码字体：'JetBrains Mono', 'Fira Code', monospace

### 布局优化
- 窗口默认尺寸：760x560（更宽敞）
- 会话侧栏：200px（原 220px）
- 圆角统一：16px（窗口）、14px（气泡）、8px（按钮）
- 间距统一：16px/12px/8px 递减

## 性能优化
- 使用 `requestAnimationFrame` 优化拖拽/调整大小
- 添加 `will-change` 和 `contain` 提升渲染性能
- 防抖处理窗口 resize 事件
- 移除不必要的 transition，拖拽时禁用过渡

## 测试建议
1. 测试流式消息接收，确认无双重转义
2. 测试移动端拖拽悬浮球
3. 测试 Markdown 渲染（代码块、列表、引用）
4. 测试窗口拖拽和调整大小
