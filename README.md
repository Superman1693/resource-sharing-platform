# 笔记资源分享平台（知识星球）

前后端分离的知识社区平台，支持笔记/资源创作、全文搜索、社区互动、知识图谱与 AI 助手。

## 技术栈

- **前端** (`demo1/`)：Vue 3 + Vite + Ant Design Vue + Pinia + Vue Router
- **后端** (`user-center/`)：Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis + Elasticsearch + Spring AI

## 功能模块

### 用户体系
注册登录 / JWT 鉴权 / OAuth 第三方登录（GitHub、QQ）/ 图形验证码 / 账号封禁

### 内容创作
- 笔记发布（Markdown，Vditor 编辑器）
- 笔记详情**内联编辑**：双击进入 contenteditable 富文本编辑 + turndown 回写 Markdown，左右浮动工具栏（格式/颜色/快捷键）
- 资源上传与管理（阿里云 OSS）

### 内容消费
笔记详情 / 资源详情 / Elasticsearch 全文搜索 / 热榜 / 推荐阅读

### 社区互动
评论 / 回复 / **@提及**（输入 @ 弹用户联想，触发通知）/ 点赞 / 关注 / 私信（双向会话）/ WebSocket 实时通知

### 知识体系
知识地图（SVG 拖拽编辑 + 笔记自动生成节点与连线）/ 学习路径

### 个人中心
我的内容（发布 / **收藏** / 评论 / 获赞）/ 数据看板 / 成长轨迹 / 贡献热力图 / 简历展示

### 后台管理
内容管理 / 评论审核 / 星球管理 / 敏感词管理 / 用户管理 / 管理员日志

### AI
AI 聊天助手（Spring AI 集成 OpenAI 兼容接口，流式输出）

### 第一档新增：内容社区闭环
| 功能 | 说明 |
|------|------|
| 收藏夹 | 笔记详情一键收藏（toggle）+「我的内容」加「我的收藏」tab |
| 全局搜索 | 独立搜索结果页（笔记 / 用户 / 资源 三 tab）+ Redis ZSET 热搜词云 |
| @提及 | 评论框输入 `@` 弹用户联想浮层（键盘导航），选中触发 `mention` 站内通知 |
| 标签体系 | 标签广场（按使用次数字号云）+ 按标签检索笔记 + 发布时标签联想；关联表双写，兼容原 `tags` JSON |

## 架构要点

- **多级缓存**：Caffeine L1（本地）→ Redis L2 → DB，`CacheConfig` 统一管理
- **布隆过滤器**：Redisson RBloomFilter 防缓存穿透，启动加载已发布笔记 ID
- **分布式锁**：Redisson RLock + `@PreventDuplicate` AOP 防重复提交
- **WebSocket**：STOMP over SockJS，`SimpMessagingTemplate` 推送通知
- **多租户**：MyBatis-Plus `TenantLineInnerInterceptor`，基于 `star_id` 行级隔离
- **数据脱敏**：Jackson `@MaskSensitive` 注解，手机号/邮箱序列化时自动脱敏
- **Elasticsearch**：`EsSearchService` 全文检索（IK 中文分词）
- **统一响应**：`BaseResponse<T>` + `ResultUtils`，前端 `request.js` 校验 `code`
- **限流 / XSS 过滤 / 全局错误上报 / Web Vitals 采集**

## 快速开始

### 前端
```bash
cd demo1
npm install
npm run dev      # 开发服务器 5173，代理 /api → localhost:8080
npm run build    # 生产构建
```

### 后端
```bash
cd user-center
./mvnw spring-boot:run                              # 8080 端口
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # dev 配置
./mvnw clean package -DskipTests                    # 打包
```

### 数据库
建表 SQL：`demo1/database.sql`，迁移脚本：`user-center/src/main/resources/db/migration/`。

> Flyway 未启用，迁移需手动执行：`mysql -h localhost -P 3306 -u<user> -p<pwd> user_center < V*.sql`

迁移清单：`V2` 关注/私信/学习路径 → `V3` 星球公告 → `V4` 资源上传者 → **`V5` 收藏夹** → **`V6` 标签体系**。

标签回填（建表后执行一次，把现有 `note.tags` JSON 迁到关联表，需管理员 token）：
```bash
curl -X POST http://localhost:8080/api/tag/migrate -H "Authorization: Bearer <管理员token>"
```

## 目录结构

```
.
├── demo1/                 # 前端 Vue 3
│   └── src/
│       ├── views/         # 按模块组织：Notes/Resources/Content/Comments/Stars/Knowledge/User/Tags/Search...
│       ├── layouts/        # BasicLayout（管理端）/ UserLayout（用户端）
│       ├── store/         # Pinia：userLogin / app / notification
│       ├── utils/         # request / api / apiContract / markdown / errorTracker / performance
│       └── components/     # AI 聊天 / 点赞 / 关注 / 搜索 / 热力图 ...
└── user-center/           # 后端 Spring Boot
    └── src/main/java/com/example/usercenter/
        ├── controller/    # REST 接口（统一 BaseResponse）
        ├── service/       # 业务逻辑（接口 + impl）
        ├── mapper/        # MyBatis-Plus Mapper
        ├── model/          # domain 实体 / dto / request
        ├── common/        # BaseResponse / ErrorCode / ResultUtils / PageResult
        ├── interceptor/   # Auth / RateLimit / Tenant
        ├── config/        # Jackson / Cache / Druid / Redis / Redisson / Security / WebSocket ...
        └── ...
```

## 配置类清单（后端 config/）

| 配置类 | 职责 |
|--------|------|
| `JacksonConfig` | Jackson 全局配置（日期格式、忽略未知属性） |
| `CacheConfig` | 多级缓存：Caffeine L1 + Redis L2 |
| `DruidConfig` | Druid 连接池 + SQL 防火墙 + 监控 |
| `MybatisPlusConfig` | 分页插件 + 多租户插件 |
| `RedisConfig` | RedisTemplate 序列化 |
| `RedissonConfig` | Redisson 客户端 |
| `SecurityConfig` | Spring Security（禁 CSRF/Session，CORS） |
| `WebMvcConfig` | 拦截器注册（Auth + RateLimit） |
| `WebSocketConfig` | STOMP 消息代理 + SockJS |
| `BloomFilterConfig` | Redisson 布隆过滤器 |
| `AsyncConfig` | @Async 线程池 + @EnableScheduling |
| `ElasticsearchConfig` | ES 8.x Java Client |
| `SwaggerConfig` | SpringDoc OpenAPI |
| `CommonConfiguration` | Spring AI ChatClient |

## 关键约定

- 后端统一返回 `{ code: 0, data, description }`，`code=0` 表示成功
- MyBatis-Plus 逻辑删除字段 `is_delete`（0=未删除，1=已删除）
- 数据库 ID 策略：自增
- 认证：JWT Bearer Token，`Authorization` header
- 前端开发代理：`/api` → `localhost:8080`，后端 context-path 为 `/api`
- Redis 用途：JWT 黑名单 / 缓存 / AI 会话 / 浏览量批量刷盘 / 热搜 ZSET
- Elasticsearch：全文搜索
- 阿里云 OSS：文件存储
