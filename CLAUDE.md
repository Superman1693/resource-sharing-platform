# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

笔记资源分享平台（知识星球），采用前后端分离架构：
- **前端** (`demo1/`)：Vue 3 + Vite + Ant Design Vue + Pinia + Vue Router
- **后端** (`user-center/`)：Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis + Elasticsearch + Spring AI

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
- `interceptor/` — `AuthInterceptor`（JWT 鉴权，基于 `@LoginRequired`/`@AdminRequired` 注解）、`RateLimitInterceptor`（限流）、`TenantInterceptor`（多租户行级隔离）
- `filter/XssFilter` — XSS 过滤
- `annotation/` — `@LoginRequired`、`@AdminRequired`、`@RateLimit`、`@MaskSensitive`（数据脱敏）、`@PreventDuplicate`（防重复提交）
- `serializer/` — Jackson 自定义序列化器（`MaskSensitiveSerializer` 数据脱敏）
- `event/` — Spring 事件（`NoteHotScoreEvent` + `NoteEventListener`，解决 @Async 自调用失效）
- `task/` — 定时任务（浏览量刷盘、会话清理）
- `config/` — Spring 配置类（见下方「配置类清单」）

### 前端结构 (demo1/src)
- `views/` — 按业务模块组织：Notes、Resources、Content、Comments、Stars、Knowledge、User、Stats、Growth 等
- `layouts/` — `BasicLayout`（管理端）和 `UserLayout`（用户端）两套布局
- `router/router.js` — 路由定义，分管理端（`/main/*`，需管理员）和用户端（`/user/*`，浏览免登录、操作需登录）
- `store/` — Pinia 状态管理：
  - `userLogin.js` — 用户认证状态，使用 `pinia-plugin-persistedstate` 自动持久化到 localStorage
  - `app.js` — 应用级状态（侧栏、暗黑模式、全局 loading）
  - `notification.js` — 通知状态（未读数、WebSocket 连接状态）
- `utils/request.js` — axios 实例，自动注入 token，统一处理业务异常（code !== 0 抛错）
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

### 关键技术点
- MyBatis-Plus 逻辑删除字段：`is_delete`（0=未删除，1=已删除）
- 数据库 ID 策略：自增（`id-type: AUTO`）
- **连接池**：Druid（含监控面板 `/druid/index.html`，慢 SQL 阈值 2000ms）
- **多级缓存**：Caffeine L1（本地，maxSize=5000）→ Redis L2 → DB，`CacheConfig` 管理
- **布隆过滤器**：Redisson RBloomFilter，防缓存穿透，启动时加载所有已发布笔记 ID
- **分布式锁**：Redisson RLock，`@PreventDuplicate` AOP 注解防重复提交
- **WebSocket**：STOMP over SockJS，端点 `/ws`，`SimpMessagingTemplate` 推送通知到 `/user/{userId}/queue/notification`
- **数据脱敏**：Jackson `@MaskSensitive` 注解，手机号 `138****5678`、邮箱 `z***@qq.com`，序列化时自动生效
- **多租户**：MyBatis-Plus `TenantLineInnerInterceptor`，基于 `star_id` 行级隔离，忽略表见 `TenantInterceptor.IGNORE_TABLES`
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
| `SecurityConfig` | Spring Security（禁用 CSRF/Session，CORS 配置）|
| `WebMvcConfig` | 拦截器注册（Auth + RateLimit）|
| `WebSocketConfig` | STOMP 消息代理 + SockJS 端点 |
| `BloomFilterConfig` | Redisson 布隆过滤器（启动时加载笔记 ID）|
| `AsyncConfig` | @Async 线程池（core=5, max=20）+ @EnableScheduling |
| `ElasticsearchConfig` | ES 8.x Java Client |
| `SwaggerConfig` | SpringDoc OpenAPI 文档 |
| `CommonConfiguration` | Spring AI ChatClient 配置 |

## 数据库

建表 SQL 位于 `demo1/database.sql`，迁移脚本位于 `user-center/src/main/resources/db/migration/`。核心表：user、note、comment、resource、star、star_member、like_record、view_record、knowledge_map、knowledge_node、learning_path、message、notification、follow、admin_log 等。

## 注意事项

- `pom.xml` 中已移除 FastJSON，统一使用 Jackson（`jackson-datatype-jsr310` 处理 Java 8 日期）
- `User.userPassword` 标注 `@JsonProperty(access = WRITE_ONLY)`，仅接受写入不序列化输出
- `LoginUserDTO` 包含 `starId` 字段用于多租户隔离，JWT claims 中需包含 `starId`
- 前端 `userLogin` store 使用 `pinia-plugin-persistedstate` 自动持久化，不再手动操作 localStorage
- 前端错误通过 `errorTracker.js` 批量上报到后端 `/api/error/report`，使用 `sendBeacon` 优先
