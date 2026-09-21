# VditorEditor 编辑器使用指南

## 一、组件概述

`VditorEditor` 是基于 [Vditor](https://b3log.org/vditor/) 封装的 Vue 3 组件，提供专业的 Markdown 编辑体验。

### 特性

- ✅ **分屏预览** - 左侧 Markdown，右侧实时预览
- ✅ **即时渲染** - 所见即所得模式
- ✅ **代码高亮** - 内置 highlight.js
- ✅ **图片上传** - 粘贴、拖拽、工具栏上传
- ✅ **快捷键** - Ctrl+B/I/K/D 等
- ✅ **TOC 目录** - 自动生成目录导航
- ✅ **全屏编辑** - 专注写作模式
- ✅ **数学公式** - KaTeX/LaTeX 支持
- ✅ **主题切换** - 亮/暗主题
- ✅ **本地缓存** - 自动保存编辑内容

---

## 二、基本使用

### 2.1 引入组件

```vue
<script setup>
import VditorEditor from '@/components/VditorEditor.vue'
</script>
```

### 2.2 使用组件

```vue
<template>
  <VditorEditor
    v-model="content"
    :height="500"
    placeholder="请输入内容..."
    @html-changed="handleHtmlChanged"
    @word-count="handleWordCount"
  />
</template>
```

---

## 三、Props 属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `modelValue` | String | `''` | 编辑器内容（双向绑定） |
| `placeholder` | String | `'支持 Markdown 格式...'` | 占位符文本 |
| `height` | Number | `500` | 编辑器高度（px） |

---

## 四、Events 事件

| 事件 | 参数 | 说明 |
|------|------|------|
| `update:modelValue` | `(value: string)` | 内容变化时触发 |
| `htmlChanged` | `(html: string)` | HTML 预览变化时触发 |
| `wordCount` | `(count: number)` | 字数统计变化时触发 |

---

## 五、Methods 方法（通过 ref 调用）

```javascript
const editorRef = ref(null)

// 获取 Markdown 内容
const content = editorRef.value.getValue()

// 获取 HTML
const html = editorRef.value.getHTML()

// 插入内容
editorRef.value.insertValue('**粗体**')

// 聚焦
editorRef.value.focus()

// 失焦
editorRef.value.blur()
```

---

## 六、图片上传

### 6.1 自动上传

编辑器内置图片上传功能，支持：

1. **粘贴图片** - 从剪贴板粘贴图片
2. **拖拽图片** - 拖拽图片文件到编辑器
3. **工具栏上传** - 点击工具栏图片按钮

### 6.2 上传配置

在 `VditorEditor.vue` 中配置上传接口：

```javascript
upload: {
  url: '/api/user/upload',     // 上传接口
  fieldName: 'file',           // 文件字段名
  max: 5 * 1024 * 1024,       // 最大 5MB
  accept: 'image/*',           // 仅图片
  headers: {
    Authorization: `Bearer ${token}`  // 认证头
  }
}
```

---

## 七、快捷键

| 快捷键 | 功能 |
|--------|------|
| `Ctrl + B` | 粗体 |
| `Ctrl + I` | 斜体 |
| `Ctrl + K` | 链接 |
| `Ctrl + D` | 删除线 |
| `Ctrl + Shift + I` | 图片 |
| `Ctrl + Shift + C` | 代码块 |
| `Ctrl + Shift + L` | 列表 |
| `Ctrl + Shift + O` | 有序列表 |
| `Ctrl + Shift + Q` | 引用 |
| `Ctrl + Shift + T` | 任务列表 |
| `Ctrl + Z` | 撤销 |
| `Ctrl + Y` | 重做 |

---

## 八、工具栏功能

工具栏包含以下功能按钮：

| 按钮 | 功能 |
|------|------|
| 😀 | 表情 |
| H1-H6 | 标题 |
| **B** | 粗体 |
| *I* | 斜体 |
| ~~S~~ | 删除线 |
| - | 分割线 |
| " | 引用 |
| ☰ | 列表 |
| 1. | 有序列表 |
| ☑ | 任务列表 |
| </> | 代码 |
| ` | 行内代码 |
| 📊 | 表格 |
| 🔗 | 链接 |
| 🖼️ | 图片 |
| ↩️ | 撤销 |
| ↪️ | 重做 |
| ⛶ | 全屏 |
| 📝 | 编辑模式 |

---

## 九、切换编辑模式

Vditor 支持三种编辑模式：

### 9.1 分屏预览（默认）
```javascript
// 左侧输入 Markdown，右侧实时预览
mode: 'sv'
```

### 9.2 即时渲染
```javascript
// 输入 Markdown，立即渲染为富文本
mode: 'ir'
```

### 9.3 所见即所得
```javascript
// 类似 Word 的富文本编辑
mode: 'wysiwyg'
```

---

## 十、主题配置

### 10.1 编辑器主题

```javascript
// 亮色主题（默认）
theme: 'classic'

// 暗色主题
theme: 'dark'
```

### 10.2 内容主题

```javascript
preview: {
  theme: {
    current: 'light'  // 或 'dark'
  }
}
```

---

## 十一、代码高亮

Vditor 内置了 100+ 种编程语言的代码高亮支持：

```markdown
```javascript
console.log('Hello, Vditor!')
```

```python
print('Hello, Vditor!')
```

```java
System.out.println("Hello, Vditor!");
```
```

---

## 十二、数学公式

支持 LaTeX 数学公式：

```markdown
行内公式：$E = mc^2$

块级公式：
$$
\sum_{i=1}^{n} i = \frac{n(n+1)}{2}
$$
```

---

## 十三、常见问题

### Q1: 编辑器不显示？

检查：
1. 是否正确引入 `vditor/dist/index.css`
2. 组件是否在 `onMounted` 后初始化

### Q2: 图片上传失败？

检查：
1. 上传接口是否正确
2. Token 是否有效
3. 文件大小是否超限

### Q3: 如何自定义工具栏？

修改 `VditorEditor.vue` 中的 `toolbar` 配置：

```javascript
toolbar: [
  'emoji',
  'headings',
  'bold',
  'italic',
  // ... 自定义按钮
]
```

### Q4: 如何禁用某些功能？

```javascript
// 禁用上传
upload: {
  url: ''  // 留空即禁用
}

// 禁用缓存
cache: {
  enable: false
}
```

---

## 十四、性能优化

### 14.1 防抖处理

内容变化时已内置 100ms 防抖，避免频繁触发回调。

### 14.2 图片压缩

建议在上传前压缩图片，减少传输大小。

### 14.3 懒加载

编辑器在 `onMounted` 时初始化，在 `onBeforeUnmount` 时销毁，避免内存泄漏。
