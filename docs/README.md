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
| **接口文档** | [api-reference.md](03-api/api-reference.md) | RESTful 接口文档：第一~十二章为 35 个接口的请求/响应详解，**第十三章为全量 142 个接口清单**（分 23 个模块） |
| | [chat-api.md](03-api/chat-api.md) | AI 聊天接口文档（JWT 认证版） |
| **组件指南** | [vditor-editor-usage.md](04-guides/vditor-editor-usage.md) | Vditor Markdown 编辑器组件使用指南 |
| **更新日志** | [ai-chat-fixes.md](05-changelog/ai-chat-fixes.md) | AI 聊天组件流式渲染 / 拖拽修复说明 |
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
