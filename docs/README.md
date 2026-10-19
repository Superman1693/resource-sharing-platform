# 文档中心

本目录是「编程知识星球」项目的**唯一文档中心**。所有项目文档按主题分类归入此处，根目录只保留 `README.md`（项目入口）与 `CLAUDE.md`（AI 编码约定）。

---

## 一、快速导航

| 分类 | 文档 | 说明 |
|------|------|------|
| **毕设文档** | [06-thesis/项目说明书.md](06-thesis/项目说明书.md) | **项目说明书**：背景与目标 / 架构与模块 / 技术选型 / 数据模型 / 核心流程 / 已实现与待完善 / 扩展方向 / 创新点（毕业设计论文与答辩主材料） |
| **项目说明** | [project-guide.md](01-overview/project-guide.md) | 技术深度解析：架构、认证、多租户、缓存、设计模式 |
| | [feature-explained.md](01-overview/feature-explained.md) | 功能逐行解析：从用户操作 → 前端 → 后端 → 数据库 |
| | [requirements.md](01-overview/requirements.md) | 需求规格与验收标准（P0–P2 共 19 项） |
| **架构与性能** | [multi-level-cache.md](02-architecture/multi-level-cache.md) | 多级缓存（Caffeine L1 + Redis L2）使用指南 |
| | [distributed-lock-usage.md](02-architecture/distributed-lock-usage.md) | Redisson 分布式锁 / 防重复提交使用指南 |
| | [points-system-design.md](02-architecture/points-system-design.md) | **积分体系使用方案**：现状盘点、5 个消耗出口（解锁/打赏/置顶/资源/星球）、等级体系与防滥用 |
| **接口文档** | [api-reference.md](03-api/api-reference.md) | RESTful 接口文档：第一~十二章为 35 个接口的请求/响应详解，**第十三章为全量 142 个接口清单**（分 23 个模块） |
| | [chat-api.md](03-api/chat-api.md) | AI 聊天接口文档（JWT 认证版） |
| **组件指南** | [vditor-editor-usage.md](04-guides/vditor-editor-usage.md) | Vditor Markdown 编辑器组件使用指南 |
| **更新日志** | [ai-chat-fixes.md](05-changelog/ai-chat-fixes.md) | AI 聊天组件流式渲染 / 拖拽修复说明 |
| | [2026-10-16-bug-audit.md](05-changelog/2026-10-16-bug-audit.md) | **全项目潜在 Bug 审计报告**：P0/P1/P2 共 9 项缺陷的成因、影响范围与最小修复方案（含端到端验证） |
| **数据库** | [`db/schema.sql`](../db/schema.sql) | 数据库完整快照（26 张表 + 索引 + 初始化数据 + 增量升级），供查阅与手工建库 |
| | [`user-center/.../db/migration/`](../user-center/src/main/resources/db/migration/) | **Flyway 版本化迁移**（`V1` 基线 + `V2` 多租户作用域/错误日志），应用的机械权威 |

> 接口的**权威来源**始终是代码：前端调用见 `demo1/src/utils/api.js`，后端定义见
> `user-center/src/main/java/com/example/usercenter/controller/`。

---

## 二、文档依赖关系

```
README.md（项目入口 / 功能清单）
   │
   ├── requirements.md ──► 定义「做什么」（需求与验收标准）
   │        │
   │        ▼
   ├── project-guide.md ─► 解释「怎么设计」（架构与关键决策）
   │        │
   │        ▼
   ├── feature-explained.md ─► 解释「怎么实现」（逐行走查链路）
   │
   ├── 03-api/  ──────────► 前后端之间的契约（接口层）
   │     ├── api-reference.md
   │     └── chat-api.md
   │
   ├── 02-architecture/ ──► 横切能力（缓存 / 锁），被 feature-explained 引用
   │     ├── multi-level-cache.md
   │     └── distributed-lock-usage.md
   │
   └── 05-changelog/ ─────► 迭代记录（时间维度）
```

**推荐阅读顺序：** `README.md` → `01-overview/` → `03-api/` → `02-architecture/` → `04-guides/`。

---

## 三、整理记录（2026-09-21）

### 3.1 目录迁移对照表

| 新位置 | 原位置 |
|--------|--------|
| `docs/01-overview/project-guide.md` | `PROJECT_GUIDE.md`（根） |
| `docs/01-overview/feature-explained.md` | `FEATURE_EXPLAINED.md`（根） |
| `docs/01-overview/requirements.md` | `requirements.md`（根） |
| `docs/02-architecture/multi-level-cache.md` | `user-center/docs/multi-level-cache.md` |
| `docs/02-architecture/distributed-lock-usage.md` | `user-center/docs/distributed-lock-usage.md` |
| `docs/03-api/api-reference.md` | `demo1/API.generated.md` |
| `docs/03-api/chat-api.md` | `demo1/chat-api-documentation.md` |
| `docs/04-guides/vditor-editor-usage.md` | `demo1/docs/vditor-editor-usage.md` |
| `docs/05-changelog/ai-chat-fixes.md` | `demo1/AI-CHAT-FIXES.md` |
| `db/schema.sql` | 整合自 `demo1/database.sql` + `demo1/migrate.sql` + `V2`~`V9` |

### 3.2 已删除文件

**① 空文件 / 重复副本 / 样板文 / 临时产物**

| 文件 | 删除原因 |
|------|----------|
| `DEPLOYMENT.md` | 0 字节空文件 |
| `user-center/API.generated.md` | 与 `demo1/API.generated.md` 内容完全重复（MD5 相同） |
| `demo1/README.md` | Vite 官方模板样板文，与本项目无关 |
| `user-center/HELP.md` | Spring Initializr 生成样板文 |
| `user-center/chat-api-documentation.md` | Session 认证旧版，已被 JWT 版取代 |
| `user-center/backend.log` / `nohup.out` | 启动失败日志，无保留价值 |
| `user-center/Promote.java` / `Promote.class` | 一次性提权脚本，**内含数据库明文口令**，属安全隐患 |

**② 含本项目不存在的电商模块**

| 文件 | 删除原因 |
|------|----------|
| `demo1/API.md`（曾迁至 `docs/99-archive/legacy-api-design.md`） | 早期设计稿，含**商品/订单/售后/推广运营**等本项目不存在的电商模块；经全仓检索确认前端与后端零引用 |

**③ 数据库脚本（10 个，内容已全部并入 `db/schema.sql`）**

| 文件 | 说明 |
|------|------|
| `demo1/database.sql` | 基础建表，已被完整包含 |
| `demo1/migrate.sql` | 增量语句，已并入**并修正**了 MySQL 不兼容语法 |
| `user-center/src/main/resources/db/migration/V2`~`V9`（8 个） | 迁移脚本，已并入 |
| `user-center/database_init.sql`（曾迁至 `docs/99-archive/database-init.legacy.sql`） | 早期驼峰命名脚本，早已被取代 |

> 全部删除前已备份至：
> - 文档类 → `.workbuddy/backup/docs-cleanup-20260921/`
> - SQL 类 → `.workbuddy/backup/sql-cleanup-20260921/`
>
> 被 git 跟踪的文件亦可通过 git 历史找回。

### 3.3 文档内容修正

**第一轮**

| 文档 | 修正内容 |
|------|----------|
| `docs/03-api/api-reference.md` | 删除「11.3 获取销售统计数据（`GET /stats/sales`）」整节及接口一览表中的对应行——该接口前端未调用、后端未实现，属电商模板残留 |
| `README.md`（根） | 迁移清单由 `V2–V6` 修正为 `V2–V9`；数据库章节改指向 `db/schema.sql` |
| `user-center/README.md` | 打包产物版本号 `1.0.1-SNAPSHOT` → `2.0.1-SNAPSHOT` |
| `CLAUDE.md` | 新增「文档索引」章节；数据库章节改指向 `db/schema.sql` |

**第二轮（与代码逐项核对后修正的过期/错误内容）**

| 文档 | 修正内容 |
|------|----------|
| `project-guide.md` | 目录名 `UserCenter/` → `user-center/`；定时任务类名 `ViewCountFlushTask` → `FlushViewCountTask`；浏览量刷盘周期 5 分钟 → **60 秒**；热度公式评论权重 `×5` → **`×2`**；删除不存在的 `NoteSearchMapper.xml`；订正多租户忽略表清单与「note 会自动加 star_id」的错误表述；订正限流注解写法；订正前端路由与核心 API 路径；WebSocket 代理前缀 `/queue` → **`/topic`** |
| `feature-explained.md` | 限流注解参数名 `timeWindowSeconds` → **`windowSeconds`**；订正多租户忽略表清单；浏览量刷盘周期 5 分钟 → **60 秒**；点赞防重复实现由「Redis Set」订正为**唯一索引 + like_record**；AI 聊天章节由虚构的 `CommonConfiguration`/`AiChatRequest`/`aiService` 订正为实际 `ChatController`/`ChatRequest`/`ChatService`（`POST /chat/message`）；JWT 有效期由「7 天 / 1 天」订正为 **记住我 30 天 / 否则取配置值 `spring.jwt.expiration`（默认 86400 秒 = 1 天）**；WebSocket 代理前缀 `/queue` → **`/topic`** |
| `multi-level-cache.md` | Caffeine 参数 `initialCapacity=100` / `maximumSize=2000` → 实际 **`200` / `5000`**；缓存清单补全 `hotRank`(L2 2min)、`userInfo`(L2 30min)、`starList`(L2 15min) 并订正「最大条数」为全局 `maximumSize=5000` |
| `distributed-lock-usage.md` | 补入遗漏的锁：`CollectionController` 收藏、`StarController` 创建星球（实际共 16 处 `@PreventDuplicate`）；订正锁 Key 生成格式与示例 |
| `api-reference.md` | 顶部补充「覆盖范围」说明——原文档仅覆盖约 35 个接口，之后新增的收藏/标签/专栏/积分/举报/搜索/关注/私信/通知/星球/学习路径/后台管理等模块未收录，权威来源指向 `demo1/src/utils/api.js` |
| `user-center/README.md` | 数据库脚本路径 `demo1/database.sql` → `db/schema.sql`；删除已不存在的 `db/` 迁移目录与 Flyway 相关描述；删除不存在的 Docker 部署章节；删除不存在的 `LICENSE` 许可章节 |
| `CLAUDE.md` | 订正租户忽略表清单（原文误将 `user_follow`、`message` 等列为忽略表） |
| `README.md`（根） | 目录结构删去已移除的 `99-archive/` |

**第三轮（OAuth 回调排查 + 多租户链路修复）**

| 项 | 内容 |
|------|------|
| OAuth 回调 `localhost:5173` 拒绝连接 | **根因**：回调地址写死为前端 dev server 的 5173，而 `demo1/vite.config.js` 未固定端口（5173 被占用时 Vite 会静默切到 5174），且 `application-prod.yml` 也错误地指向 `localhost:5173`。**修**：`vite.config.js` 增加 `port: 5173` + `strictPort: true`；prod 的 `github.oauth.redirect-uri` 改为 `https://www.e-ren.icu/oauth/callback`；GitHub 授权地址的 `redirect_uri` 补 URL 编码 |
| `starId` claim 缺失 | `JwtUtils.generateToken` 增加 `starId` 参数（非 null 才写入 claim）；新增 `StarMemberMapper.selectPrimaryStarId`（取最早加入的星球作为「默认星球」）；`UserController#userLogin` 与 `OAuthController`（GitHub/QQ）共 3 处调用点同步传入 |
| 忽略表表名写错 | `TenantInterceptor.IGNORE_TABLES` 中 `follow` → `user_follow` |
| note/comment/resource 被忽略 | **保持忽略**，但补上「为什么不能过滤」：① 无 star_id 列（comment/resource 注入即报错）；② 需跨星球公开读取（note/star_member 叠加等值条件会互斥）。清单按此两类重组为 27 张，并在类注释写明全局强制隔离所需的 3 步改造 |
| OAuth CSRF（附带修复） | 前端 GitHub 授权地址补随机 `state` 并做校验（原先 `generateState` 是从未被调用的死代码）；QQ 分支跳过校验（其 state 为固定值 `qq_login`，仅用于识别来源） |
| `db/schema.sql` | 删除误加的 `user.star_id` 列（`user` 表原本没有该列，多租户列只存在于 5 张表） |

**第四轮（落地 6.3 的 9 项已知问题，逐项改造并编译验证）**

> 背景：`docs/06-thesis/项目说明书.md` 第 6.3 节列了 10 项已知问题，本轮除「测试覆盖不足」（第 6 项）外全部处理。

| 编号 | 原问题 | 处理结果 |
|:---:|--------|----------|
| 1 | 多租户隔离未生效 | **作用域化改造**：`user` 新增 `current_star_id`、`comment` 新增 `star_id`；`TenantInterceptor` 由「全局忽略表」改为「请求级作用域 + 白名单（note/comment/note_column/knowledge_map）」；`UserContext` 新增 `starScope`；`AuthInterceptor` 解析并校验 `X-Star-Id` 成员身份；前端新增 `utils/starScope.js`，`router.js` 按页面写入作用域、`request.js` 自动附加请求头；`JwtUtils` 补 `starId` claim，新增 `UserService.resolveCurrentStarId()` |
| 2 | 接口文档只覆盖 35 个 | `api-reference.md` 新增**第十三章「完整接口清单（142 个）」**（脚本从 `api.js` 提取，分 23 个模块，逐条标注后端匹配情况）；经静态比对，前端 142 个调用与后端 178 条路由 **142/142 全部匹配** |
| 3 | 第三方登录依赖人工配置 | `OAuthController` 新增 `@PostConstruct` 启动校验（打印生效回调地址；prod 仍指向 localhost 则报错）；补 GitHub `state` 随机串与回调校验 |
| 4 | 构建依赖 IDE | 修复 `mvnw` 的 MSYS 路径兼容问题——**根因**：发行版 `bin/mvn` 把 `MAVEN_HOME` 归一化成 `/c/...`，Windows 版 `java.exe` 无法识别；补丁在检测到 `cygpath` 时直接用 Windows 路径启动 classworlds launcher（*.踩坑*：脚本第 32 行 `set -euf` 的 `-f` 关闭通配符展开，须用 `find` 而非 glob 定位 jar） |
| 5 | 数据库迁移手工执行 | 引入 `flyway-core` + `flyway-mysql`；新建基线 `V1__baseline_schema.sql`（25 张表）与 `V2__multi_tenant_scope_and_error_log.sql`；`application.yml` 开启 `baseline-on-migrate` |
| 7 | 错误上报未闭环 | 新增 `error_log` 表 + `ErrorLog` + `ErrorLogMapper`；重写 `ErrorReportController`（上报入口 + 管理员分页查询） |
| 8 | 搜索热度无衰减 | 新增 `SearchHotDecayTask`：每日 04:00 按 `0.9` 衰减重建 `search:hot`（读→临时 key 重建→`RENAME` 原子替换），低于 `0.5` 淘汰 |
| 9 | OAuth 用户密码为空串 | `UserConstant.OAUTH_PASSWORD_PLACEHOLDER = "!oauth-no-password!"`；登录逻辑显式拒绝该占位符；`V2` 迁移改写存量空密码 |
| 10 | 仓库存在构建/IDE 产物 | 清理 `.history/`（3 处 291 个文件）、`user-center/target/`（105 MB）、`demo1/dist/`（24 MB）；`node_modules/` 保留 |

**同步更新的文档**：`docs/06-thesis/项目说明书.md`（第 2/3/4/5/6/7/8 章与附录，含 6.3 重写为「已修复 9 项 + 未解决 1 项 + 遗留边界」）、`CLAUDE.md`、`docs/01-overview/project-guide.md`、`docs/01-overview/feature-explained.md`、`docs/03-api/api-reference.md`、`README.md`（根）、`user-center/README.md`、`db/schema.sql`。

**验证方式**：后端 `./mvnw compile`（202 个源文件）通过；前端 `vite build`（3660 个模块）通过。

**第五轮（修复 Flyway 启动失败：MySQL 版本兼容 + 迁移到 8.0）**

> 现象：启动即报 `FlywayEditionUpgradeRequiredException: MySQL 5.7 is no longer supported by Flyway Community Edition`。

| 项 | 内容 |
|------|------|
| **根因 1：MySQL 版本** | Spring Boot 3.2.12 锁定 Flyway 9.22.3，而该版本 `MySQLDatabase.ensureSupported()` 对 MySQL 的最低判定是 **8.0**（反编译确认）。实测下载 9.21.2 / 9.16.3 反编译，判定同样是 8.0 → **降级 Flyway 无法解决** |
| **根因 2：`yiya` 在 5.7 实例上** | 本机 3306 是 MySQL 5.7.44（`MySQL-57` 服务），3307 是 MySQL 8.0.42（`MySQL80` 服务），二者均为**开机自启服务**；项目的 `yiya` 库原本在 3306 |
| **修复 1：数据迁移** | `mysqldump`（`--column-statistics=0` 兼容 5.7 服务端）导出 3306 的 `yiya`（25 张表 / 148 行）→ 在 3307 建同名库（`utf8mb4_unicode_ci`）→ 导入。**逐表比对行数：25/25 完全一致，零错误**。迁移前已检查：无视图/触发器/存储过程、无 8.0 保留字冲突 |
| **修复 2：切换数据源** | `application-dev.yml` 的 `center.datasource.port` 由 `3306` 改为 `3307`（该文件被 gitignore，故同步更新了版本库中的 `application-dev.yml.example`）；`application-prod.yml` 加注「部署环境需 MySQL 8.0+」 |
| **修复 3（隐藏缺陷）** | `pom.xml` 的 `<resources><includes>` 是扩展名白名单且漏了 `**/*.sql` → 迁移脚本从不进入 classpath，Flyway 报 `No migrations found`。已补 `<include>**/*.sql</include>` |
| **验证结果** | Flyway：`Current version: 1` → `Migrating to version "2 - multi tenant scope and error log"` → `now at version v2`；应用 `Started UserCenterApplication in 23.157 seconds`，`Tomcat started on port 8080`，**无 ERROR**；`GET /api/note/list`、`/api/user/current`、`/api/star/list` 均返回 200；库结构 25 → 27 张表（含 `error_log` 与 `flyway_schema_history`），`comment.star_id` 回填 3 条、2 个第三方账号写入密码占位符 |
| **备份位置** | `.workbuddy/backup/mysql-migration-20260921/yiya-from-3306.sql`（3306 的完整导出，可随时回滚） |

**第六轮（修复线上登录/注册 403：CORS 白名单漏了顶级域）**

> 现象：云服务器上登录/注册返回 `403 Forbidden`，响应体 `Invalid CORS request`，而本地完全正常。

| 项 | 内容 |
|------|------|
| **根因** | `SecurityConfig` 的 CORS 白名单被**硬编码**为 `localhost:5173 / localhost:5174 / https://www.e-ren.icu`，而线上实际访问的是**顶级域** `https://e-ren.icu`。顶级域与 www 是两个不同的来源 → 未登记 → 403 |
| **为什么同源也会触发** | 前端用相对路径 `/api`（无 `.env` 覆盖 `VITE_API_BASE_URL`），线上页面与接口同源。但浏览器对 `POST + application/json` 这类**非简单请求**，即使同源也会发送 `Origin` 头；Spring 见到 `Origin` 即校验白名单。**「同源不用配 CORS」是错误直觉** |
| **为什么本地正常** | 本地走 Vite dev server 代理（`/api` → `localhost:8080`），`Origin` 是 `http://localhost:5173`，恰好白名单里有 |
| **修复** | ①白名单从硬编码改为配置项 `center.cors.allowed-origins`（`application.yml`，逗号分隔），新增 `https://e-ren.icu`、`http://127.0.0.1:5173`；②`SecurityConfig` 新增 `@PostConstruct` 打印生效白名单；③自动纠正末尾多余 `/` 并告警；④允许的方法补 `PATCH` |
| **对照实测** | 修复前实例：`Origin: https://e-ren.icu` → **403 Invalid CORS request**；同一实例换 `https://www.e-ren.icu` → 200。修复后实例：`https://e-ren.icu` / `www` / `localhost:5173` / `127.0.0.1:5173` 全部 **200**，而 `https://evil.example.com` 仍 **403**（白名单未被放开）；注册接口 200；`OPTIONS` 预检返回 `Access-Control-Allow-Origin: https://e-ren.icu` |

**第七轮（隐藏 QQ 登录入口 + 新增邮箱登录）**

| 项 | 内容 |
|------|------|
| **需求 1：隐藏 QQ 登录按钮** | 按用户澄清「只隐藏按钮，不要删代码」，实现为开关式：`Login.vue` 新增 `const SHOW_QQ_LOGIN = false`，QQ 按钮加 `v-if="SHOW_QQ_LOGIN"`（含样式），**其余 QQ 代码全部保留**（`oauth.js` 的 `redirectToQQ`、`api.js` 的 `qqLogin`、`OAuthCallback.vue` 的 QQ 分支、后端 `/oauth/qq/**`）。改回 `true` 即恢复 |
| **需求 2：手机号登录 → 邮箱登录** | 登录页第 2 个页签由「手机号登录（即将开放）」占位替换为**可用的「邮箱登录」**表单（邮箱格式校验 + 密码 + 记住我）；后端 `userLogin` 支持「用户名或邮箱」登录 |
| **后端实现要点** | ①按「是否含 `@`」分流校验（原账号规则会把 `@`/`.` 判为非法，不分流则邮箱永远登不进）；②查询条件 `WHERE (user_account = ? OR email = ?)`；③**用「密码匹配」而非「唯一命中」确定用户**——`user.email` 无唯一索引且存在多账号共用邮箱的历史数据，`selectOne` 会抛 `TooManyResultsException`，取第一条又会误登他人账号；④抽出 `matchesPassword()` 复用 BCrypt/MD5 兼容与自动升级逻辑 |
| **关键坑** | 给入参 `loginId` 重新赋值后再在 lambda 中使用 → 编译报「从 lambda 表达式引用的本地变量必须是最终变量」。改用新局部变量 `loginKey` 承载 `trim()` 结果 |
| **实测（2026-09-21）** | 邮箱登录 ✅ / 用户名登录无回归 ✅ / 未注册邮箱返回统一错误（不泄露账号是否存在）✅ / 重复邮箱 + 错误密码返回业务错误而非 500 ✅ / 非法账号与非法邮箱格式均被拦下 ✅；**两账号共用同一邮箱、密码不同 → 各自密码分别登进正确账号（id15 / id16）** ✅；前端 `vite build` EXIT=0，后端 `./mvnw compile` EXIT=0；测试账号已清理（用户数回到 14） |

**第八轮（修复举报功能的三处缺陷）**

> 现象：①用户举报时没有地方填写理由，管理端却显示「（未填写）」；②管理端点「查看」报「笔记不存在」。

| 项 | 内容 |
|------|------|
| **缺陷 1：举报理由无处填写** | `NoteDetail.vue` 的举报笔记用 `Modal.confirm`（只有一个确认按钮、不传 `reason`）→ 落库为 NULL；举报评论更是**硬编码** `reason: '用户举报'` → 管理端显示假理由 |
| **缺陷 2：「查看」报笔记不存在** | `NoteController` 的公开详情端点已经算好了 `userId` / `isAdmin`，却**弃之不用**，只调 `getNoteDetailPublished(id)`（published-only）。而举报会把笔记 `markNotePending` 成 `pending` → **管理员恰恰打不开自己最需要看的被举报内容**，`getNoteDetail(id,userId,isAdmin)` 成了死代码 |
| **缺陷 3：管理端无法辨识目标** | 列表只有 `targetId` / `reporterId`，看不出被举报的是什么；评论类型点「查看」还提示「暂不支持跳转查看」 |
| **修复 1** | 新增举报弹窗：6 个预设理由 + 选填补充说明（选「其他」时说明必填），笔记与评论共用同一弹窗；拼接规则 `预设项：补充说明`，限长 200 字 |
| **修复 2** | `GET /note/{id}` 改为「published 缓存命中不到时，对作者/管理员回退到 `getNoteDetail(id, userId, isAdmin)`」；**匿名访客与其他登录用户维持原行为**，不泄露 draft/pending/rejected 的存在 |
| **修复 3** | `Report` 实体加三个 `@TableField(exist = false)` 派生字段（`reporterName` / `targetTitle` / `targetNoteId`），`ReportServiceImpl.list` 按类型聚合 ID 后 `selectBatchIds` 批量填充（避免 N+1）；管理端表格补「举报人」「被举报内容」两列，评论类型改为跳转其所属笔记 |
| **端到端实测** | 匿名访问 pending 笔记 → 仍「笔记不存在」✅；普通登录用户（非作者非管理员）→ 仍「笔记不存在」✅；**管理员 → 正常打开（状态 pending）** ✅；带理由举报笔记与评论后，`/report/list` 正确显示 `原因=垃圾广告或推广内容：…`、`举报人=举报测试号`、`被举报内容=…`、评论型还带 `评论所属笔记=25` ✅ |
| **验证** | 后端 `./mvnw -q compile` EXIT=0；前端 `vite build` EXIT=0。测试数据（笔记/评论/举报/通知/积分/标签/测试账号）已全部清理，库状态与测试前一致 |

**第九轮（全项目潜在 Bug 审计与修复）**

> 范围：五路并行审计（认证授权与多租户 / 内容域 / 资源上传 / AI·通知·积分 / 前端）。
> 完整报告见 [05-changelog/2026-10-16-bug-audit.md](05-changelog/2026-10-16-bug-audit.md)。

| 项 | 内容 |
|------|------|
| **P0 认证绕过** | `AuthInterceptor.preHandle` 中 `return false` 时**不会**触发本拦截器自身的 `afterCompletion`（Spring `applyPreHandle` 的 `interceptorIndex` 只回溯到上一个成功的拦截器）→ `UserContext` 身份残留，被下一个复用该线程的请求（可能匿名）继承。修复：`preHandle` 开头无条件 `clear()` + 两处 `return false` 前显式 `clear()` |
| **P0 多租户写入越权** | `createNote` 不校验请求体 `starId` 的成员归属，任意登录用户可向任意星球写入内容 |
| **P0 审核绕过** | `addResource` 原为 `isBlank(status) ? "pending" : status`，客户端可自传 `enabled` 直接上架。修复：强制 `pending` |
| **P0 AI 会话越权** | `resolveSessionId` 直接返回客户端传入的 `sessionId` 不校验归属（读路径有校验，写路径漏了）→ 可写入并借 AI 记忆读取他人对话 |
| **P1 状态机 + 刷分** | `updateNote` 只拦 `rejected → published`，**漏拦 `pending → published`**（举报置 pending 后作者可自行恢复，绕过审核）；`publishSideEffects` 注释称"仅首次发布"但**无幂等判断**，`published ↔ draft` 可无限重复发 +20 积分、重复累加星球内容计数。修复：状态拦截 + 以 `points_log` 是否已有该笔记 `publish` 记录做幂等 |
| **P1 评论计数失真** | `replyComment` 不计入、`deleteComment` 不递减 `note.comment_count`（全仓无递减逻辑）。修复：补计入 + 新增 `NoteMapper.decrementCommentCount`（`GREATEST` 防负） |
| **P1 举报忽略后内容消失** | `ReportServiceImpl.handle` 只改 `report` 行、不联动笔记 → 被误举报的内容永久停留 `pending`。修复：`ignored` 时调 `noteService.approveNote()` |
| **P1 前端登录态不一致** | 401 只清 localStorage，Pinia `isLogin` 内存态未重置 → 守卫把用户从 `/login` 弹回首页并反复 401。修复：登录态以本地凭证为准 |
| **P2 blob 下载必失败** | `request.js` 对所有响应跑 `normalizeBaseResponse`（要求 `typeof code === 'number'`），blob 无 `.code` 必抛错。修复：blob 直通 |
| **验证** | `mvnw compile` EXIT=0；`vite build` EXIT=0；8081 独立实例端到端 6 组用例全部通过（30 次匿名请求 0 泄漏、审核绕过被堵、积分恰好 1 条、评论计数 1→0） |

**第十轮（积分闭环 + 移除评论审核）**

| 项 | 内容 |
|------|------|
| **积分闭环** | 此前积分**只增不减**（签到/发布/被赞/评论都有获取，没有任何消耗出口）。本轮落地两条消耗链路：①**付费笔记解锁**——新增 `note.price` 列与 `note_purchase` 表（唯一键 `user_id+note_id` 幂等），`PointsService#unlockNote` 原子扣减、作者得 90%；②**打赏作者**——新增 `note_tip` 表，单次 1~500、禁止自赏，作者得 90%；③**等级体系**——前端按 `total_earned` 定级（消费不掉级），个人主页展示 `Lv.N` 与升级进度；④**防滥用**——`likeNote` 排除作者本人（自赞不再 +2 积分） |
| **移除评论审核** | 评论原先前台发布即 `pending`，须管理员逐条放行；且回复走 `approved`、主评论走 `pending`，两处本身就不一致。现改为**只做敏感词过滤**：命中即拒绝发布（复用 `sensitiveWordChecker.check`），通过即可见。删除 `approveComment` 接口与实现、`resolveModerationStatus`、硬编码 `SENSITIVE_WORDS`；`CommentStatus` 精简为 `APPROVED`/`HIDDEN`；管理端「通过」按钮改为「屏蔽 / 恢复」（`POST /comment/status` 替代 `/comment/approve`）。历史 `pending`/`rejected` 数据由 V3 统一置为 `approved` |
| **数据迁移** | `V3__remove_comment_moderation_and_add_points_spend.sql`：新增 `note.price`、`note_purchase`、`note_tip`，并调整 `comment.status` 的默认值与注释、放行历史待审数据 |
| **验证** | Flyway `v2 → v3` 成功执行；`mvnw compile` / `vite build` 均 EXIT=0；8081 端到端：付费笔记「未解锁遮蔽 → 积分不足拒绝 → 解锁后可见」、解锁扣 5 作者得 4、打赏扣 10 作者得 9、自赏与越界被拒、**评论发布后匿名立即可见**、含敏感词评论**直接拒绝**（不再转 hidden） |

**第十一轮（积分消耗 P2 / P3）**

| 项 | 内容 |
|------|------|
| **P2-1 自助置顶** | 新增 `note.top_until`；`POST /note/boost/{id}` 消耗 **50 积分**置顶 **24 小时**，仅作者本人可置顶自己的已发布笔记；新增定时任务 `ExpireTopNoteTask`（每 10 分钟）批量清理到期置顶并清 `noteList` 缓存。与管理员置顶的区别：管理员置顶不带 `top_until`，不会自动过期 |
| **P2-2 资源付费下载** | 新增 `resource.price` 与 `resource_download`（唯一键 `user_id+resource_id`）。`PointsService#unlockResource` 统一处理免费/付费：付费先扣分（上传者得 90%），**首次产生记录时才 `download_count +1`** —— 顺带修掉「刷新下载接口即可无限刷计数」的既有缺陷。下载接口改为需登录（匿名仅可下免费资源且不计数） |
| **P3 创建星球门槛** | `POST /star/create` 增加积分门槛：消耗 **500 积分**（`PointsService#CREATE_STAR_COST`）；配合方法上已有的 `@Transactional`，余额不足不会留下半成品星球 |
| **前端** | 笔记详情页新增「置顶 / 续期置顶」按钮（作者可见，二次确认）；发布页与资源上传页新增售价输入；资源详情页显示「需 N 积分 / 免费」标签，付费下载前二次确认；创建星球入口提示门槛 |
| **验证** | Flyway `v3 → v4` 成功；`mvnw compile` / `vite build` 均 EXIT=0；8081 端到端：置顶后 `is_top=1` 且 `top_until` 为 24 小时后、「模拟到期 + 执行同款 UPDATE」归零成功（`is_top=0, top_until=NULL`）、资源下载两次**仅计 1 次**且扣 10/上传者得 9、余额 90 时创建星球被拒（还差 410 分）、充值 600 后创建成功并扣 500 |

---

**第十二轮（积分额度与平台对账：每日上限 / 售价上限随等级 / 对账视图）**

| 项 | 内容 |
|------|------|
| **每日获取上限** | `PointsService.DAILY_EARN_LIMIT = 200`，在 `addPointsInternal` 统一拦截：仅约束**平台发放**类型（`sign`/`publish`/`like`/`comment`），超出按剩余额度**截断**并在 `remark` 标注「今日获取上限，实发 N 分」；额度用尽则**不写流水**。**用户间转移（`unlock_income`/`tip_income`/`resource_income`）不计入、不受限** —— 那是别人花掉的积分转过来，限流会让作者收不到报酬 |
| **售价上限随等级** | 新增后端权威枚举 `model/enums/PointsLevel.java`（L1 新手 0 / L2 活跃 50 / L3 贡献 100 / L4 资深 200 / L5 核心 500，按 `total_earned` 定级）。`NoteServiceImpl#normalizeNotePrice` 与 `ResourceServiceImpl#normalizePrice` 改为按 `pointsService.getPriceLimit(userId)` 校验；资源由原来的静默 `Math.min(price, 200)` **改为直接报错**（填 200 实际只生效 50 却无提示）。前端 `pointsLevel.js` 阈值与枚举对齐，发布页按 `GET /points/quota` 渲染提示 |
| **对账视图** | `GET /points/reconcile?days=30`（`@AdminRequired`）：总览（账户数 / 未消费存量 / 累计发放 / 累计消耗 / 平台抽成 / 成交单数 / 今日已发放）、抽成**分渠道**、近 N 天趋势（发放·消耗·抽成合并到同一日期轴）、收入榜、消费榜。管理端新页 `Admin/PointsReconcile.vue`，图表用**纯 CSS 条形图**（未引入 ECharts）。抽成由三张成交表反推 `SUM(成交额 − 收款方实得)` |
| **额度接口** | `GET /points/quota` 返回 `level / levelName / priceLimit / totalEarned / balance / nextLevel... / dailyEarnLimit / todayEarned / todayRemain`，发布页与个人主页共用 |
| **迁移 V5** | 补 4 个 `idx_create_time`（`points_log` / `note_purchase` / `note_tip` / `resource_download`）—— 对账只按时间过滤，原索引均以 `user_id` / `note_id` 为前缀命不中 |
| **踩坑（两个）** | ① **`change` 是 MySQL 保留字**：`QueryWrapper.gt("change", 0)` 拼出 `change > 0` 直接语法错误，而它发生在 `addPoints` 的 `try/catch` 里 → **所有积分发放静默失效**（笔记照发、积分不发，日志只有一行 `WARN 加分失败`）。已改用 `` gt("`change`", 0) ``，并把 catch 改为传 Throwable 打完整堆栈。② **`only_full_group_by`**：SELECT 与 GROUP BY 的分组表达式必须完全一致，`DATE_FORMAT(create_time,...)` 不能配 `GROUP BY DATE(create_time)` |
| **验证** | Flyway `v4 → v5` 成功；`mvnw compile` / `vite build` 均 EXIT=0；8081 端到端：L1 定价被拒 → 提到 L2 后上限 50（50 通过 / 51 拒绝）→ 资源同规则；解锁+打赏+下载后**抽成 11、成交 3 单**且分渠道 5/1/5 正确；剩余 1 分时签到（本应发 6）**实发 1 分**、额度满后发评论**不写流水**；A 的 `todayEarned=40` 而 `balance=139` → **income 不占额度** |

---

## 四、维护约定

1. **新增文档**必须归入对应分类目录，并同步在上方「快速导航」登记。
2. **修改架构相关代码**时，同步更新 `01-overview/project-guide.md`。
3. **新增/变更接口**时，同步更新 `03-api/` 下对应文档。
4. **数据库结构变更**时，必须①在 `user-center/src/main/resources/db/migration/` 新增版本化迁移脚本（`V{n}__描述.sql`，历史脚本不可修改），②同步更新 `db/schema.sql` 快照，③更新 `docs/06-thesis/项目说明书.md` 第 4 章的表清单。**Flyway 迁移脚本是机械权威，`db/schema.sql` 是可读快照。**
   - ⚠️ 运行环境必须是 **MySQL 8.0+**（Flyway 9.x 社区版不支持 5.7），开发库在 `localhost:3307`。
   - ⚠️ `pom.xml` 的 `<resources><includes>` 是**扩展名白名单**，新增资源类型（如 `.json`）时必须补进去，否则不会被打进 classpath。
5. **新增星球相关页面**时，必须在 `demo1/src/router/router.js` 的 `ROUTE_STAR_PARAM` 中登记，否则该页面不会携带 `X-Star-Id`，多租户过滤将退化为无效（不报错，属静默降级）。
6. **修改本机环境相关配置**（数据库端口、第三方回调地址、前端端口）时，注意 `application-dev.yml` / `application-prod.yml` 被 `.gitignore` 忽略，必须同步更新版本库中的 `application-dev.yml.example`，否则改动不会进版本控制。
7. **过时但仍有追溯价值的文档**，先与维护者确认再决定归档或删除，**不要**擅自删除。
8. 文档内引用代码路径请使用**仓库根目录起算的相对路径**（如 `demo1/src/utils/request.js`）。
