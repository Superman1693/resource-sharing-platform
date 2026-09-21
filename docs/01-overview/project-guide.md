# 编程知识星球 — 项目技术深度解析

> 完整理解这个项目需要掌握以下核心模块，按从底层到上层的顺序讲解。

---

## 🏗️ 一、整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户浏览器                            │
│  Vue 3 + Vite + Ant Design Vue + Pinia + Vue Router        │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP/WS (localhost:5173 → :8080)
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot 后端 (:8080)                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │Controller│→│Service  │→│Mapper   │→│ MySQL   │           │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │
│       │           │           │           │                 │
│       ▼           ▼           ▼           ▼                 │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │JWT鉴权  │ │Redis缓存│ │ES搜索   │ │布隆过滤器│           │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │
└─────────────────────────────────────────────────────────────┘
```

**数据流向：** 前端 → axios 拦截器（自动注入 Token）→ 后端 Controller → Service（业务逻辑）→ Mapper（数据库）→ 返回 `BaseResponse<T>` → 前端统一处理

---

## 🔐 二、认证与鉴权系统（难点 ⭐⭐⭐）

### 2.1 JWT 无状态认证

**核心流程：**
```
登录 → 后端生成 JWT Token → 前端存到 Pinia (自动持久化到 localStorage)
  → 每次请求 axios 拦截器自动加 Authorization: Bearer xxx
  → 后端 AuthInterceptor 解析 Token → 注入当前用户到 ThreadLocal
```

**关键代码位置：**
- 前端：`demo1/src/store/userLogin.js` — Token 存储与用户状态管理
- 前端：`demo1/src/utils/request.js` — axios 拦截器自动注入 Token
- 后端：`user-center/src/main/java/com/example/usercenter/interceptor/AuthInterceptor.java` — Token 解析

**难点：**
1. **Token 过期处理** — 前端拦截器捕获 401 跳转登录页
2. **JWT 黑名单** — 用户登出后 Token 仍有效，需加入 Redis 黑名单使其失效
3. **多租户隔离** — JWT claims 中携带 `starId`（登录时写入用户的「默认星球」）；前端进入星球页面时附加 `X-Star-Id` 请求头，`AuthInterceptor` 校验成员身份后写入 `UserContext.starScope`，`TenantInterceptor` 据此对白名单表注入 `star_id` 条件（见 §6.1）

### 2.2 注解式权限控制

```java
@LoginRequired                                                    // 必须登录才能访问
@AdminRequired                                                    // 必须是管理员
@RateLimit(key = "xxx", windowSeconds = 60, maxRequests = 100)    // 60 秒窗口内最多 100 次
```

AuthInterceptor 通过**反射**读取方法上的注解决定是否放行。这是 AOP 的实际应用。

---

## 📊 三、笔记模块（核心功能）

### 3.1 笔记生命周期

```
草稿(draft) → 审核中(pending) → 已发布(published) / 已拒绝(rejected)
                                    ↓
                              逻辑删除(is_delete=1)
```

### 3.2 浏览量统计（难点 ⭐⭐⭐）

**问题：** 每次访问都直接 UPDATE 数据库，高并发下数据库扛不住。

**解决方案：Redis 批量刷盘**
```
用户浏览笔记 → Redis INCR note:view:笔记ID (内存操作，极快)
                                    ↓
定时任务(fixedDelay=60000，每 60 秒) → 批量读取所有 note:view:* → 一次性 UPDATE 到数据库
                                    ↓
                              清空 Redis 计数器
```

**关键代码：**
- `NoteController` 的 `POST /note/view/{id}` — 每次浏览 +1 到 Redis（key 前缀 `note:view:`）
- `task/FlushViewCountTask.java` — `@Scheduled(fixedDelay = 60000)` 定时刷盘，批量调用 `incrementViewCountByDelta`
- 布隆过滤器：`config/BloomFilterConfig.java` — 启动时加载已发布笔记 ID，防止缓存穿透

### 3.3 笔记热度排行榜（难点 ⭐⭐⭐）

**算法：** `hotScore = viewCount + likeCount × 3 + commentCount × 2`（见 `event/NoteEventListener.java`，与 `requirements.md` 需求 12 一致）

**实现：** Redis Sorted Set（ZSET）
- `ZADD note:hot 150 note:123` — 更新笔记热度分
- `ZREVRANGEBYSCORE note:hot +inf -inf LIMIT 0 10` — 取 Top 10

**难点：** 使用 Spring Event 异步更新热度分，解决 `@Async` 自调用失效问题：
```java
// NoteService 内部调用 @Async 方法会失效（因为是 this 调用，不走代理）
// 解决方案：发布事件，由 NoteEventListener 异步处理
applicationEventPublisher.publishEvent(new NoteHotScoreEvent(noteId, score));
```

---

## 🗂️ 四、资源管理模块

### 4.1 资源类型与存储

| 类型 | 图标 | 存储方式 |
|------|------|----------|
| 文档 | FilePdfOutlined | 阿里云 OSS |
| 视频 | VideoCameraOutlined | 阿里云 OSS |
| 代码 | CodeOutlined | 阿里云 OSS |
| 其他 | FolderOutlined | 阿里云 OSS |

### 4.2 文件上传流程

```
前端选择文件 → 上传到阿里云 OSS → 拿到文件 URL
  → 提交资源表单（含 URL）→ 后端保存记录到 MySQL
```

---

## 💬 五、评论与互动系统

### 5.1 嵌套评论

```sql
-- 评论表结构
CREATE TABLE comment (
    id BIGINT PRIMARY KEY,
    note_id BIGINT,        -- 属于哪篇笔记
    parent_id BIGINT,      -- 回复哪条评论（NULL = 一级评论）
    user_id BIGINT,        -- 谁发的
    content TEXT,
    is_delete INT DEFAULT 0
);
```

**前端展示：** 一级评论直接展示，二级评论缩进展示（`NoteDetail.vue` 中递归渲染）

### 5.2 点赞系统

**防重复：** Redis Set + 数据库唯一索引双重保障
```
点赞 → SISMEMBER note:liked:userId 检查是否已赞
  → 未赞：SADD + INCR likeCount + INSERT like_record
  → 已赞：忽略
```

---

## ⭐ 六、知识星球（圈子）模块

### 6.1 多租户架构（难点 ⭐⭐⭐⭐）

这是整个项目**设计上最复杂、实现上最需要小心**的部分。

**核心概念：** 每个知识星球是一个「租户」，数据按 `star_id` 做行级隔离。

#### 6.1.1 为什么不是「全局无条件注入」

教科书式的多租户做法是对所有表无条件追加 `tenant_id = ?`。但本平台是**公开的知识社区**——用户即使没加入某星球，也要能浏览其详情、参与搜索与热榜。无条件注入会踩两个坑：

| 坑 | 说明 |
|----|------|
| **① 表根本没有该列** | `resource`、`learning_path`、`message` 等表没有 `star_id`，注入即报 `Unknown column 'star_id' in 'where clause'` |
| **② 等值条件互斥** | `note` 等表已有 `eq("star_id", 正在浏览的星球)`，若再叠加 `star_id = 我的默认星球`，两条件必须同时成立 → 「查看别的星球」一条都查不到 |

因此本项目采用**「请求级作用域 + 表白名单」**方案。

#### 6.1.2 实现机制

```
前端：router.js 的路由守卫
      ├ 进入 UserStarDetail(:id) / UserKnowledgeMap(:starId)
      │    → starScope.setStarScope(id)   （写入 localStorage）
      └ 其他路由 → starScope.clearStarScope()

        request.js 请求拦截器
          → 若作用域存在，附加请求头  X-Star-Id: {starId}
   ↓
后端：AuthInterceptor.preHandle
      ├ 解析 JWT → UserContext.set(LoginUserDTO{userId, userRole, starId})
      └ applyStarScope(request)
           ├ 读取 X-Star-Id
           ├ 校验当前用户确为该星球成员（查 star_member）
           └ 通过 → UserContext.setStarScope(starId)
   ↓
      TenantInterceptor（MyBatis-Plus TenantLineHandler）
        ├ ignoreTable(table)
        │    ├ 未登录                    → true（放行）
        │    ├ 无星球作用域              → true（放行，保持公开可读）
        │    └ 有作用域 → 仅白名单返回 false
        │         （白名单：note / comment / note_column / knowledge_map）
        └ getTenantId() → UserContext.getStarScope()
                          （兜底：LoginUserDTO.starId）
```

- `getTenantIdColumn()` 返回租户列名 `star_id`
- **作用域由请求头声明，而不是用「用户的默认星球」**：`user` 表没有 `star_id`，用户与星球是多对多（`star_member`），不存在「用户唯一的星球」；若用「最早加入的星球」作隐含作用域，用户在浏览 A 星球时插件会注入 B 星球的过滤条件，结果查空

#### 6.1.3 两个 starId 的区别

| 概念 | 来源 | 用途 |
|------|------|------|
| `starId`（JWT claim） | 登录时由 `UserService.resolveCurrentStarId()` 解析：优先 `user.current_star_id`，为空则回退「`star_member` 中 `join_time` 最早的一条」（`StarMemberMapper.selectPrimaryStarId`） | 「我加入了哪些星球」类场景（如 `starFeed`） |
| `starScope`（`UserContext`，请求级） | 请求头 `X-Star-Id`，经成员身份校验后写入 | 决定本次请求是否注入租户条件、注入哪个值 |

#### 6.1.4 三层隔离机制

| 层 | 机制 | 作用 |
|----|------|------|
| ① 显式查询 | `StarController#starDetail`（`eq("star_id", id)`）、`#starFeed`（`in("star_id", starIds)`） | 星球页只展示本星球内容 |
| ② 权限校验 | `canReadStarContent`（付费星球对非成员脱敏） | 付费内容不外泄 |
| ③ 租户作用域注入 | 本节所述的 `X-Star-Id` → `TenantInterceptor` | **纵深防御**：即使某处漏写显式 `eq("star_id")`，作用域内也读不到其他星球的数据 |

> ⚠️ **维护须知**：新增星球相关页面时，必须在 `demo1/src/router/router.js` 的 `ROUTE_STAR_PARAM` 中登记，否则该页面不会携带 `X-Star-Id`，隔离对该页面失效（**不报错，属静默降级**）。
>
> 若要实现「无论前端是否传头部都强制隔离」，仍需三步改造：① 给 `resource`/`learning_path`/`message` 等表补 `star_id` 列并回填；② 给跨星球读取的 Mapper 方法加 `@InterceptorIgnore(tenantLine = "true")`；③ 把「当前星球」做成用户可切换的持久化状态。

**其他难点：**
1. 跨租户查询（如管理后台查看所有星球）不会带 `X-Star-Id`，因此天然不受影响
2. 用户新加入星球后，**已签发的 JWT 里 `starId` 仍是登录时的旧值**；但浏览星球页时以请求头的 `starScope` 为准，因此不影响星球内的隔离效果（`starFeed` 等依赖默认星球的场景需重新登录才更新）


### 6.2 星球成员管理

```
创建星球 → 创建者自动成为 owner
邀请/申请加入 → 审批 → star_member 记录
退出星球 → 删除 star_member 记录
```

---

## 🔍 七、搜索系统（Elasticsearch）

### 7.1 全文搜索流程

```
笔记发布 → 同步到 ES（IK 中文分词）
用户搜索 → ES query → 返回高亮结果 → 前端展示
```

**关键代码：**
- `service/impl/EsSearchServiceImpl.java` — ES 索引创建与查询构建（IK 分词：`ik_max_word` 建索引、`ik_smart` 查询）
- `resources/mapper/NoteMapper.xml` — 本项目**唯一**的 MyBatis XML 映射；ES 数据同步由 `EsSearchService` 负责（不存在 `NoteSearchMapper.xml`）

### 7.2 搜索防抖（前端）

```javascript
// 用户输入时，300ms 内只触发一次搜索
const handleSearch = useDebounceFn(async (keyword) => {
  const res = await searchNotes({ keyword })
  // ...
}, 300)
```

---

## 💡 八、WebSocket 实时通知

### 8.1 通信架构

```
用户点赞/评论 → 后端处理 → SimpMessagingTemplate 发送通知
  → STOMP over SockJS → 前端 WebSocket 连接 → 实时弹窗
```

**端点：** `/ws`（SockJS 兼容）
**推送地址：** `/user/{userId}/queue/notification`（用户私有队列）

### 8.2 前端连接管理

```javascript
// store/notification.js
const connectWebSocket = () => {
  const socket = new SockJS('/ws')
  const stompClient = Stomp.over(socket)
  stompClient.connect({}, () => {
    stompClient.subscribe(`/user/${userId}/queue/notification`, (msg) => {
      notifications.value.push(JSON.parse(msg.body))
    })
  })
}
```

**难点：** 连接断开重连、Token 过期时的连接管理。

---

## 🎨 九、前端状态管理（Pinia）

### 9.1 三个 Store 的职责

| Store | 职责 | 持久化 |
|-------|------|--------|
| `userLogin.js` | 用户认证、Token、用户信息 | ✅ localStorage 自动 |
| `app.js` | 侧栏状态、暗黑模式、全局 Loading | ✅ localStorage |
| `notification.js` | 未读通知数、WebSocket 连接状态 | ❌ 纯内存 |

### 9.2 登录状态持久化

```javascript
// pinia-plugin-persistedstate 自动处理
// 用户关闭浏览器后重新打开，无需重新登录
export const useUserStore = defineStore('userLogin', () => {
  const token = ref('')
  const currentUser = ref(null)
  // ...
}, {
  persist: true  // 自动持久化到 localStorage
})
```

---

## 🛡️ 十、安全防护

### 10.1 XSS 防护

```java
// XssFilter 拦截所有请求，对参数进行 HTML 转义
// 用户输入 <script>alert(1)</script> → 存储为 &lt;script&gt;alert(1)&lt;/script&gt;
```

### 10.2 SQL 注入防护

MyBatis-Plus 默认使用 `#{}` 预编译参数，天然防 SQL 注入。

### 10.3 CSRF 防护

前后端分离架构，使用 JWT Token 认证，不依赖 Cookie，天然免疫 CSRF。

### 10.4 接口限流

```java
@RateLimit(100)  // 基于 Redis + 滑动窗口，每分钟最多 100 次
public BaseResponse<Note> getNote(@PathVariable Long id) { ... }
```

### 10.5 数据脱敏

```java
@MaskSensitive(type = MaskType.PHONE)   // 手机号：138****5678
@MaskSensitive(type = MaskType.EMAIL)   // 邮箱：z***@qq.com
```

Jackson 自定义序列化器 `MaskSensitiveSerializer` 在 JSON 输出时自动脱敏。

---

## ⚡ 十一、性能优化

### 11.1 多级缓存架构

```
请求 → Caffeine L1 (本地内存, 5000条)
         ↓ miss
      Redis L2 (分布式缓存)
         ↓ miss
      MySQL DB
```

**缓存穿透防护：** 布隆过滤器（Redisson RBloomFilter），启动时加载所有笔记 ID。

### 11.2 前端性能优化

| 优化手段 | 实现方式 |
|----------|----------|
| **Web Vitals 监控** | `performance.js` 采集 FCP/LCP/CLS/TTFB，上报到后端 |
| **全局错误捕获** | `errorTracker.js` 使用 `sendBeacon` 批量上报 |
| **图片懒加载** | `loading="lazy"` 原生属性 |
| **组件按需引入** | Ant Design Vue 按需导入 |
| **请求防抖** | 搜索输入 300ms 防抖 |

---

## 📐 十二、前端路由架构

```
/login  /register  /resetPassword  /changeUserPassword  /oauth/callback  → 免登录页

/user/*                → 用户端（浏览免登录，操作需登录）
  home                 → 首页
  notes                → 笔记列表
  noteDetail/:id       → 笔记详情
  publish              → 发布笔记
  resources            → 资源列表
  resourceDetail/:id   → 资源详情
  starList             → 知识星球列表
  starDetail/:id       → 星球详情
  tags  /  tag/:name   → 标签广场 / 按标签检索
  search               → 全局搜索
  hotRank              → 热榜
  myContent            → 我的内容（含我的收藏）
  messages             → 私信
  followList           → 关注 / 粉丝
  knowledgeMap/:starId?      → 知识地图（查看）
  knowledgeMapEdit/:starId?  → 知识地图（编辑）
  learningPath         → 学习路径
  growthTimeline       → 成长轨迹
  userActivity         → 数据看板
  column/:id  /  columnManage → 专栏详情 / 专栏管理
  profile              → 个人中心
  user/:userId         → 他人主页

/main/*                → 管理端（需管理员权限）
  contentManage  /  contentPublish → 内容管理 / 内容发布
  commentManage        → 评论管理
  resourceManage /  resourceAdd    → 资源管理 / 资源新增
  starManage           → 星球管理
  adminUser            → 用户管理
  sensitiveWord        → 敏感词管理
  reportManage         → 举报管理
  dashboard            → 数据看板
```

> 注：AI 聊天**不是独立路由**，而是全局浮窗组件（`components/AIFloatBall.vue` + `AIFloatWindow.vue`，挂载于 `UserLayout`）。

**守卫逻辑：**
```javascript
router.beforeEach((to, from, next) => {
  const token = useUserStore().token
  if (to.meta.requiresAuth && !token) {
    next('/login')  // 未登录 → 登录页
  } else if (to.meta.requiresAdmin && !isAdmin) {
    next('/403')    // 非管理员 → 403
  } else {
    next()
  }
})
```

---

## 🧩 十三、关键设计模式总结

| 模式 | 应用位置 | 解决的问题 |
|------|----------|------------|
| **拦截器链** | Auth → RateLimit → Tenant | 请求统一预处理 |
| **策略模式** | 不同资源类型的图标/颜色映射 | 前端类型扩展 |
| **观察者模式** | Spring Event（热度分更新） | 解决 @Async 自调用失效 |
| **模板方法** | BaseResponse 统一响应 | 接口规范统一 |
| **发布订阅** | WebSocket STOMP | 实时通知推送 |
| **防抖节流** | 搜索输入、滚动监听 | 减少无效请求 |

---

## 🚀 十四、启动顺序建议

```
1. MySQL  — 建库建表 (`mysql -u root -p < db/schema.sql`)
2. Redis  — 用于缓存 + JWT 黑名单
3. 后端   — mvnw spring-boot:run (:8080)
4. 前端   — npm run dev (:5173，自动代理 /api → :8080)
5. (可选) Elasticsearch — 全文搜索功能
```

---

## 📋 十五、核心 API 清单

| 模块 | 接口 | 说明 |
|------|------|------|
| **认证** | `POST /api/user/login` | 登录，返回 JWT |
| | `POST /api/user/register` | 注册 |
| | `POST /api/user/userLogout` | 退出登录（Token 进黑名单） |
| | `GET /api/user/current` | 当前登录用户 |
| **笔记** | `GET /api/note/list` | 分页查询笔记 |
| | `GET /api/note/{id}` | 笔记详情 |
| | `POST /api/note/add` | 创建笔记 |
| | `PUT /api/note/update/{id}` | 更新笔记 |
| | `POST /api/note/delete` | 删除笔记 |
| | `POST /api/note/like/{id}` | 点赞笔记 |
| | `POST /api/note/view/{id}` | 浏览量 +1（写 Redis） |
| | `GET /api/note/hot` | 热榜 |
| **资源** | `GET /api/resource/list` | 分页查询资源 |
| | `POST /api/resource/add` | 新增资源 |
| | `POST /api/resource/upload` | 上传资源文件（OSS） |
| | `POST /api/resource/download/{id}` | 下载计数 |
| **互动** | `POST /api/comment/add` | 发表评论 |
| | `POST /api/comment/reply/{commentId}` | 回复评论 |
| | `POST /api/comment/like/{id}` | 点赞评论 |
| | `POST /api/follow/add` / `POST /api/follow/delete` | 关注 / 取关 |
| | `POST /api/collection/{noteId}` | 收藏笔记（toggle） |
| **搜索** | `GET /api/search/notes` | 笔记全文搜索（ES） |
| | `GET /api/search/users` / `GET /api/search/resources` | 用户 / 资源搜索 |
| | `GET /api/search/hot` | 热搜词云 |
| **星球** | `GET /api/star/list` | 星球列表 |
| | `POST /api/star/join/{id}` / `POST /api/star/exit/{id}` | 加入 / 退出星球 |
| **统计** | `GET /api/stats/personal` | 个人学习看板 |
| | `GET /api/stats/contribution` | 贡献热力图 |
| | `GET /api/stats/overview` | 平台数据概览 |
| **后台** | `GET /api/admin/content/pending` | 待审核内容 |
| | `POST /api/admin/user/ban/{id}` | 封禁用户 |
| **通知** | `GET /api/notification/list` + WebSocket `/ws` | 站内通知 + 实时推送 |
| **AI** | `POST /api/chat/message` | AI 聊天（`/stream` 为流式） |

> 完整接口清单以 `demo1/src/utils/api.js` 为准（约 130 个），上表仅列核心接口。

---

> **理解这个项目的关键：** 先理解数据流（前端 → 后端 → 数据库 → 缓存），再理解权限流（JWT → 拦截器 → 注解），最后理解实时流（WebSocket → 通知推送）。这三条线贯穿整个项目。
