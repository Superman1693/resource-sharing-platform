# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

笔记资源分享平台（知识星球），采用前后端分离架构：
- **前端** (`demo1/`)：Vue 3 + Vite + Ant Design Vue + Pinia + Vue Router
- **后端** (`user-center/`)：Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis + Elasticsearch + Spring AI

## 文档索引

全部项目文档统一存放于 `docs/`，入口为 `docs/README.md`：

| 路径 | 内容 |
|------|------|
| `docs/01-overview/project-guide.md` | 技术深度解析（架构 / 认证 / 多租户 / 缓存 / 设计模式） |
| `docs/01-overview/feature-explained.md` | 功能逐行解析（用户操作 → 前端 → 后端 → 数据库） |
| `docs/01-overview/requirements.md` | 需求规格与验收标准（P0–P2 共 19 项） |
| `docs/02-architecture/multi-level-cache.md` | 多级缓存（Caffeine L1 + Redis L2）使用指南 |
| `docs/02-architecture/distributed-lock-usage.md` | Redisson 分布式锁 / 防重复提交指南 |
| `docs/03-api/api-reference.md` | REST 接口文档：第一~十二章为 35 个接口详解，第十三章为**全量 142 个接口清单** |
| `docs/03-api/chat-api.md` | AI 聊天接口文档（JWT 认证版） |
| `docs/04-guides/vditor-editor-usage.md` | Vditor Markdown 编辑器组件指南 |
| `docs/06-thesis/项目说明书.md` | 毕业设计项目说明书（背景 / 架构 / 选型 / 数据模型 / 流程 / 已实现与待完善 / 扩展方向） |
| `db/schema.sql` | 数据库**可读全量快照**（26 张表 + 索引 + 初始化 + 增量升级） |
| `user-center/src/main/resources/db/migration/` | **Flyway 版本化迁移**（机械权威）：`V1` 基线、`V2` 多租户作用域 + 错误日志 |

> 约定：新增文档须归入 `docs/` 对应分类并登记到 `docs/README.md`。
> **接口的权威来源始终是代码**——前端 `demo1/src/utils/api.js`、后端 `user-center/src/main/java/com/example/usercenter/controller/`。

## 常用命令

### 前端 (demo1/)
```bash
cd demo1
npm install          # 安装依赖
npm run dev          # 开发服务器 (默认 5173 端口，代理 /api → localhost:8080)
npm run build        # 生产构建
npm run preview      # 预览构建产物
```

### 后端 (user-center/)

> 前置：`JAVA_HOME` 指向 **JDK 17+**（本项目 `release 17`；本机 JDK 21 位于 `D://develop//jdk-21.0.9`）。
> `mvnw` 已修复 MSYS/Git Bash 下的路径兼容问题，无需 IDE 内置 Maven。

```bash
cd user-center
./mvnw spring-boot:run                              # 启动开发服务器 (8080 端口)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # 指定 dev 配置启动
./mvnw clean package -DskipTests                    # 打包（跳过测试）
./mvnw test                                         # 运行所有测试
./mvnw test -Dtest=UserServiceTest#methodName       # 运行单个测试方法
```

## 架构要点

### 后端分层结构 (com.example.usercenter)
- `controller/` — REST 接口，所有方法返回 `BaseResponse<T>`，通过 `ResultUtils` 构建
- `service/` + `service/impl/` — 业务逻辑层，接口与实现分离
- `mapper/` — MyBatis-Plus Mapper 接口，对应 `resources/mapper/*.xml`
- `model/domain/` — 实体类；`model/domain/request/` — 请求 DTO；`model/dto/` — 传输对象
- `common/` — `BaseResponse`（统一响应）、`ErrorCode`（错误码枚举）、`ResultUtils`（响应构建工具）、`PageResult`（分页）
- `interceptor/` — `AuthInterceptor`（JWT 鉴权 + 黑名单 + `@LoginRequired`/`@AdminRequired` 判定 + 解析 `X-Star-Id` 星球作用域）、`RateLimitInterceptor`（限流）、`TenantInterceptor`（多租户行级隔离：作用域 + 表白名单）
- `filter/XssFilter` — XSS 过滤
- `annotation/` — `@LoginRequired`、`@AdminRequired`、`@RateLimit`、`@MaskSensitive`（数据脱敏）、`@PreventDuplicate`（防重复提交）
- `serializer/` — Jackson 自定义序列化器（`MaskSensitiveSerializer` 数据脱敏）
- `event/` — Spring 事件（`NoteHotScoreEvent` + `NoteEventListener`，解决 @Async 自调用失效）
- `task/` — 定时任务（浏览量刷盘、会话清理、定时发布、**热搜衰减**、启动预热）
- `config/` — Spring 配置类（见下方「配置类清单」）

### 前端结构 (demo1/src)
- `views/` — 按业务模块组织：Notes、Resources、Content、Comments、Stars、Knowledge、User、Stats、Growth 等
- `layouts/` — `BasicLayout`（管理端）和 `UserLayout`（用户端）两套布局
- `router/router.js` — 路由定义，分管理端（`/main/*`，需管理员）和用户端（`/user/*`，浏览免登录、操作需登录）
- `store/` — Pinia 状态管理：
  - `userLogin.js` — 用户认证状态，使用 `pinia-plugin-persistedstate` 自动持久化到 localStorage
  - `app.js` — 应用级状态（侧栏、暗黑模式、全局 loading）
  - `notification.js` — 通知状态（未读数、WebSocket 连接状态）
- `utils/request.js` — axios 实例，自动注入 `Authorization` 与 **`X-Star-Id`**（星球作用域），统一处理业务异常（code !== 0 抛错）
- `utils/starScope.js` — 星球作用域读写（localStorage 持久化；由 `router.js` 按页面写入/清除）
- `utils/api.js` — 所有后端 API 调用封装
- `utils/apiContract.js` — `BaseResponse` 契约定义和错误码常量
- `utils/errorTracker.js` — 全局错误捕获（JS 错误、Promise 异常、Vue 组件错误），批量上报到 `/api/error/report`
- `utils/performance.js` — Web Vitals 采集（FCP/LCP/CLS/TTFB），轻量实现无第三方依赖
- `components/` — 通用组件（AI 聊天、点赞、关注、搜索、热力图等）

### 前后端通信约定
- 后端统一返回 `{ code: 0, data: ..., description: "..." }`，`code=0` 表示成功
- 前端 `request.js` 响应拦截器自动校验 `code`，非 0 抛出业务异常
- 错误码定义在 `ErrorCode.java`（后端）和 `apiContract.js`（前端）
- 认证方式：JWT Bearer Token，通过 `Authorization` header 传递
- **星球作用域**：前端处于星球相关页面时自动附加 `X-Star-Id` 请求头，后端据此注入租户条件（见「关键技术点 · 多租户」）

### 关键技术点
- MyBatis-Plus 逻辑删除字段：`is_delete`（0=未删除，1=已删除）
- 数据库 ID 策略：自增（`id-type: AUTO`）
- **连接池**：Druid（含监控面板 `/druid/index.html`，慢 SQL 阈值 2000ms）
- **多级缓存**：Caffeine L1（本地，maxSize=5000）→ Redis L2 → DB，`CacheConfig` 管理
- **布隆过滤器**：Redisson RBloomFilter，防缓存穿透，启动时加载所有已发布笔记 ID
- **分布式锁**：Redisson RLock，`@PreventDuplicate` AOP 注解防重复提交
- **WebSocket**：STOMP over SockJS，端点 `/ws`，`SimpMessagingTemplate` 推送通知到 `/user/{userId}/queue/notification`
- **数据脱敏**：Jackson `@MaskSensitive` 注解，手机号 `138****5678`、邮箱 `z***@qq.com`，序列化时自动生效
- **多租户（请求级作用域方案）**：MyBatis-Plus `TenantLineInnerInterceptor` 已注册，`TenantInterceptor` 采用「**作用域 + 白名单**」而非「全局 + 忽略表」：
  - **写入作用域**：前端 `router.js` 的 `ROUTE_STAR_PARAM` 按路由把星球 ID 写入 `utils/starScope.js` → `request.js` 附加 `X-Star-Id` 头 → `AuthInterceptor.applyStarScope()` 校验当前用户确为该星球成员后，写入 `UserContext.starScope`
  - **生效范围**：仅当作用域存在时，才对白名单表 `note` / `comment` / `note_column` / `knowledge_map` 注入 `star_id = starScope`
  - **未设置作用域时**（首页 / 搜索 / 热榜 / 未登录）：不注入任何条件，保持跨星球公开可读
  - **`starId` 与 `starScope` 的区别**：前者是 JWT claim（登录时由 `UserService.resolveCurrentStarId()` 解析，优先 `user.current_star_id`，为空回退「最早加入的星球」），用于「我加入了哪些星球」类场景；后者是请求级上下文，决定本次查询是否收窄
  - **为什么不能无条件注入**：`resource`/`learning_path`/`message` 等表**没有 `star_id` 列**（注入即 `Unknown column`）；而 `note` 需跨星球公开读取，叠加「我的星球」等值条件会互斥导致查空
  - ⚠️ **新增星球相关页面时必须同步登记到 `router.js` 的 `ROUTE_STAR_PARAM`**，否则该页面静默退化为无作用域
  - 隔离能力：纵深防御——即使后端某处漏写 `eq("star_id")`，作用域内也不会读到其他星球数据
- Redis 用于：JWT 黑名单、缓存、AI 聊天会话、浏览量批量刷盘
- Elasticsearch 用于全文搜索（`EsSearchService`，IK 中文分词）
- Spring AI 集成 OpenAI 兼容接口实现 AI 聊天
- 阿里云 OSS 用于文件存储
- 前端开发代理：`/api` 前缀请求代理到 `localhost:8080`，后端 context-path 为 `/api`

### 配置类清单 (config/)
| 配置类 | 职责 |
|--------|------|
| `JacksonConfig` | Jackson 全局配置（日期格式、忽略未知属性）|
| `CacheConfig` | 多级缓存：Caffeine L1 + Redis L2 + CompositeCacheManager |
| `DruidConfig` | Druid 连接池 + SQL 防火墙 + 监控 Servlet |
| `MybatisPlusConfig` | 分页插件 + 多租户插件 |
| `RedisConfig` | RedisTemplate 序列化配置 |
| `RedissonConfig` | Redisson 客户端（单机模式）|
| `SecurityConfig` | Spring Security（禁用 CSRF/Session）+ **CORS 来源白名单**，读 `center.cors.allowed-origins` |
| `WebMvcConfig` | 拦截器注册（Auth + RateLimit）|
| `WebSocketConfig` | STOMP 消息代理 + SockJS 端点 |
| `BloomFilterConfig` | Redisson 布隆过滤器（启动时加载笔记 ID）|
| `AsyncConfig` | @Async 线程池（core=5, max=20）+ @EnableScheduling |
| `ElasticsearchConfig` | ES 8.x Java Client |
| `SwaggerConfig` | SpringDoc OpenAPI 文档 |
| `CommonConfiguration` | Spring AI ChatClient 配置 |

## 数据库

**双轨**：

| 角色 | 位置 | 说明 |
|------|------|------|
| **机械权威**（版本化） | `user-center/src/main/resources/db/migration/` | Flyway 管理：`V1__baseline_schema.sql`（基线 25 张表）、`V2__multi_tenant_scope_and_error_log.sql`（多租户作用域 + 错误日志）。**历史脚本不可修改**，新增变更须追加 `V{n}__描述.sql` |
| **可读快照** | `db/schema.sql` | 26 张表 + 索引 + 初始化数据 + 增量升级，供查阅与手工建库 |

**运行前置条件（重要）**：
- 数据库必须是 **MySQL 8.0+**。Flyway 9.x 社区版对 MySQL 的最低要求是 8.0，连 5.7 会直接抛
  `FlywayEditionUpgradeRequiredException` 导致应用启动失败（降级 Flyway 无用，9.16/9.21/9.22 判定相同）。
- 本地开发库在 **`localhost:3307`**（MySQL 8.0）；`3306` 是本机的 MySQL 5.7，**不能用于本项目**。
- `pom.xml` 的 `<resources><includes>` 是**扩展名白名单**，当前含 `yml / properties / xml / txt / sql`。
  新增其它类型的资源文件（如 `.json`）时，必须同步加进白名单，否则不会被打进 classpath。

`application.yml` 已开启 `spring.flyway.baseline-on-migrate=true`：已有数据库首次接入会被标记为基线版本 1，只执行 `V2` 及之后的脚本，不会重复执行 `V1`。

> ⚠️ **若已用 `db/schema.sql` 手工建过库**（该快照已含 V2 的改动），再启动应用前须把 `spring.flyway.baseline-version` 改为 `2`，否则 Flyway 会重复执行 `V2` 并报「字段已存在」。

核心表（26）：user、user_follow、star、star_member、note、note_column、comment、tag、note_tag、resource、like_record、view_record、note_collection、report、message_conversation、message、notification、knowledge_map、knowledge_node、learning_path、learning_path_node、points_account、points_log、sign_in_record、admin_log、error_log。

- 命名一律 snake_case；逻辑删除字段 `is_delete`
- 租户列 `star_id` 存在于 5 张表：`note`、`star_member`、`note_column`、`comment`、`knowledge_map`；另有 `user.current_star_id` 记录「当前所在星球」
- `user` 表**没有** `star_id`：用户与星球是多对多，归属记录在 `star_member`
- 参与自动租户过滤的表见 `TenantInterceptor.TENANT_SCOPED_TABLES`（白名单 4 张）；其余表要么没有 `star_id` 列，要么本身按 `user_id`/`note_id` 隔离

## 注意事项

- `pom.xml` 中已移除 FastJSON，统一使用 Jackson（`jackson-datatype-jsr310` 处理 Java 8 日期）
- `User.userPassword` 标注 `@JsonProperty(access = WRITE_ONLY)`，仅接受写入不序列化输出
- `LoginUserDTO` 含 `starId` 字段，`AuthInterceptor` 从 JWT claims 读取；签发端 `JwtUtils.generateToken(userId, userRole, starId, expirationSeconds)` 已写入该 claim（`starId` 为 null 时不写，避免序列化出空 claim）
- 第三方登录回调地址由后端配置决定：`github.oauth.redirect-uri` 在 dev 下为 `http://localhost:5173/oauth/callback`，因此 **`demo1/vite.config.js` 已固定 `port: 5173` + `strictPort: true`**，改端口时两处必须同改
- **登录标识可以是用户名或邮箱**：`POST /api/user/login` 的 `userAccount` 字段按「是否含 `@`」自动分流，
  邮箱走邮箱格式校验、其余走「禁止特殊字符」的账号规则。实现上查询条件为
  `WHERE (user_account = ? OR email = ?)`，再用**密码匹配**从候选中确定用户——
  因为 `user.email` 没有唯一索引，存在多账号共用同一邮箱的历史数据，不能依赖唯一命中。
- **QQ 登录入口当前隐藏**：`demo1/src/views/Login.vue` 的 `SHOW_QQ_LOGIN = false` 控制按钮是否渲染。
  `utils/oauth.js` 的 `redirectToQQ`、`utils/api.js` 的 `qqLogin`、`OAuthCallback.vue` 的 QQ 分支
  以及后端 `/oauth/qq/**` 接口**全部保留未动**，改回 `true` 即可恢复入口。
- 前端 `userLogin` store 使用 `pinia-plugin-persistedstate` 自动持久化，不再手动操作 localStorage
- 前端错误通过 `errorTracker.js` 批量上报到后端 `/api/error/report`，使用 `sendBeacon` 优先
- **CORS 白名单**：配置项 `center.cors.allowed-origins`（`application.yml`，逗号分隔），由 `SecurityConfig` 读取。
  三个易踩的坑：
  1. **同源请求也会带 `Origin`**——浏览器对 `POST + application/json` 这类非简单请求，即使前后端同源也会发送
     `Origin` 头；Spring 一旦看到 `Origin` 就会校验白名单，未登记直接返回 `403 Invalid CORS request`。
     所以「同源不需要配 CORS」是**错误**的直觉。
  2. **顶级域与 www 是两个来源**：`https://e-ren.icu` 与 `https://www.e-ren.icu` 必须分别列出。
  3. **不能带末尾斜杠**：Origin 头不含路径，写成 `https://e-ren.icu/` 永远匹配不上（`SecurityConfig` 会自动纠正并告警）。
  启动日志会打印生效白名单：`[CORS] 允许的前端来源：[...]`；线上出现 403 且响应体是 `Invalid CORS request` 时，
  第一时间对照这行日志与浏览器实际 Origin。
