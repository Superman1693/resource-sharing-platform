# 笔记资源分享平台（知识星球）

前后端分离的知识社区平台，支持笔记/资源创作、全文搜索、社区互动、知识图谱与 AI 助手。

## 文档索引

完整文档中心见 **[docs/README.md](docs/README.md)**。

| 文档 | 内容 |
|------|------|
| [docs/01-overview/project-guide.md](docs/01-overview/project-guide.md) | 技术深度解析（架构 / 认证 / 多租户 / 缓存 / 设计模式） |
| [docs/01-overview/feature-explained.md](docs/01-overview/feature-explained.md) | 功能逐行解析（用户操作 → 前端 → 后端 → 数据库） |
| [docs/01-overview/requirements.md](docs/01-overview/requirements.md) | 需求规格与验收标准（P0–P2 共 19 项） |
| [docs/02-architecture/](docs/02-architecture/) | 多级缓存、分布式锁使用指南 |
| [docs/03-api/](docs/03-api/) | REST 接口文档（覆盖范围见文档开头）、AI 聊天接口 |
| [docs/04-guides/vditor-editor-usage.md](docs/04-guides/vditor-editor-usage.md) | Vditor Markdown 编辑器组件指南 |
| [docs/05-changelog/](docs/05-changelog/) | 版本更新日志 |
| [`db/schema.sql`](db/schema.sql) | 数据库完整脚本（唯一权威版本） |

> 接口的权威来源：前端 [`demo1/src/utils/api.js`](demo1/src/utils/api.js)、后端 [`user-center/.../controller/`](user-center/src/main/java/com/example/usercenter/controller/)。

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
数据库采用**双轨制**：

| 角色 | 位置 | 说明 |
|------|------|------|
| **机械权威**（版本化迁移） | `user-center/src/main/resources/db/migration/` | Flyway 管理，应用启动时自动执行；`V1__baseline_schema.sql` 为基线，新增变更须追加 `V{n}__描述.sql`（历史脚本不可修改） |
| **可读全量快照** | [`db/schema.sql`](db/schema.sql) | 26 张表 + 索引 + 初始化数据 + 增量升级附录，供查阅与手工建库 |

> 两者等价：快照 = 全部迁移的顺序累加。结构变更时**必须同时更新两处**。

> ⚠️ **前置条件：MySQL 必须 8.0+**
> Flyway 9.x 社区版对 MySQL 的最低要求是 8.0。若连到 MySQL 5.7，应用会在启动阶段抛
> `FlywayEditionUpgradeRequiredException` 并直接退出（**降级 Flyway 无效**，9.16 / 9.21 / 9.22 判定相同）。
> 本机开发库已指向 **`localhost:3307`**（MySQL 8.0）；`3306` 是本机的 MySQL 5.7，不能用于本项目。
>
> 另：`pom.xml` 的 `<resources><includes>` 是**扩展名白名单**，`**/*.sql` 必须在其中，
> 否则迁移脚本不会被打进 classpath，Flyway 会报 `No migrations found`。

```bash
# 方式一（推荐）：交给 Flyway —— 启动后端即自动完成建表/升级
cd user-center && ./mvnw spring-boot:run

# 方式二：手工建库
mysql -u root -p < db/schema.sql      # 全新库：一条命令建好全部表
```

> `db/schema.sql` 第 13 部分为「已有数据库的增量升级语句」；MySQL 的 `ALTER TABLE` 不支持 `IF NOT EXISTS`，重复执行报「字段已存在」属正常现象。
> **注意（防止重复执行）**：如果已用 `db/schema.sql` 手工建过库，再启动应用前请把 `spring.flyway.baseline-version` 设为 `2`，否则 Flyway 会从 `V1` 判定并重复执行 `V2`。
> 注意：MySQL 的 `ALTER TABLE` 不支持 `IF NOT EXISTS`（那是 MariaDB 语法），旧 `migrate.sql` 中的该写法在 MySQL 下会报语法错误，新脚本已移除。

标签回填（建表后执行一次，把现有 `note.tags` JSON 迁到关联表，需管理员 token）：
```bash
curl -X POST http://localhost:8080/api/tag/migrate -H "Authorization: Bearer <管理员token>"
```

## 目录结构

```
.
├── README.md              # 项目总览（本文件）
├── CLAUDE.md              # AI 编码助手约定
├── docs/                  # 📚 统一文档中心（入口见 docs/README.md）
│   ├── 01-overview/       # 项目说明：技术解析 / 功能链路 / 需求规格
│   ├── 02-architecture/   # 架构与性能：多级缓存 / 分布式锁
│   ├── 03-api/            # 接口文档：REST 接口 / AI 聊天接口
│   ├── 04-guides/         # 组件指南：Vditor 编辑器
│   ├── 05-changelog/      # 更新日志
├── db/                    # 数据库脚本（schema.sql，唯一权威版本）
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
