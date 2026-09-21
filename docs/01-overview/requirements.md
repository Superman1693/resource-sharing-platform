# 需求文档：笔记资源分享平台全面优化

## 介绍

本文档描述对 SpringBoot + Vue 3 笔记资源分享平台的全面代码审查与优化需求。
平台涵盖用户管理、笔记发布、资源下载、评论点赞、浏览记录、AI 聊天、知识图谱、统计数据等模块。
优化目标涵盖代码规范、安全、性能、数据库、功能完善、BUG 修复、可扩展性七大方向。

优先级定义：
- **P0（紧急）**：线上 BUG、安全漏洞、数据一致性问题
- **P1（重要）**：性能瓶颈、核心功能缺失、架构缺陷
- **P2（一般）**：代码规范、体验优化、可扩展性改进

---

## 词汇表

- **Platform**：笔记资源分享平台整体系统
- **NoteService**：笔记业务服务，负责笔记的增删改查、点赞、浏览量等操作
- **CommentService**：评论业务服务，负责评论的增删改查、审核、点赞等操作
- **UserService**：用户业务服务，负责注册、登录、权限管理等操作
- **AuthInterceptor**：统一权限拦截器，负责接口鉴权
- **XssFilter**：XSS 过滤器，负责对用户输入内容进行安全过滤
- **RedisCache**：Redis 缓存层，负责热点数据缓存
- **SearchService**：全文搜索服务，负责笔记/内容的关键词检索
- **HotRankService**：热榜服务，负责计算和返回热度排行榜
- **AdminService**：后台管理服务，负责用户封禁、内容审核工作流
- **UserStore**：前端 Pinia 用户状态管理 Store
- **RequestInterceptor**：前端 axios 请求/响应拦截器

---

## 需求


### 需求 1：统一接口响应格式 [P0]

**用户故事：** 作为前端开发者，我希望所有后端接口都返回统一的 `BaseResponse` 格式，以便前端拦截器能够统一处理业务异常。

#### 验收标准

1. THE Platform SHALL ensure all controller methods return `BaseResponse<T>` as the response type.
2. WHEN `UserController.closeAccount()` is called, THE UserService SHALL return `BaseResponse<Boolean>` instead of `Map<String, Object>`.
3. IF any controller method returns a raw `Map` or primitive type, THEN THE Platform SHALL be considered non-compliant with this requirement.
4. THE Platform SHALL define a single `ResultUtils` utility class as the only entry point for constructing `BaseResponse` objects.

#### 正确性属性

- **示例测试**：调用 `POST /user/closeAccount`，验证响应体包含 `code`、`data`、`description` 三个字段。
- **示例测试**：对所有 Controller 方法的返回类型进行静态扫描，确认无 `Map` 或裸类型返回。

---

### 需求 2：笔记列表后端分页 [P0]

**用户故事：** 作为平台用户，我希望笔记列表支持后端分页，以便在数据量大时不会导致页面卡顿或服务器 OOM。

#### 验收标准

1. WHEN `GET /note/list` is called with `page` and `pageSize` parameters, THE NoteService SHALL return only the records for the requested page.
2. THE NoteService SHALL accept `page`（默认 1）and `pageSize`（默认 10，最大 50）as query parameters.
3. THE NoteService SHALL return a paginated response containing `total`、`current`、`pageSize`、`records` fields.
4. IF `pageSize` exceeds 50, THEN THE NoteService SHALL reject the request with `PARAMS_ERROR`.
5. THE NoteService SHALL perform sorting（热度/最新）on the database side, not in application memory.
6. WHEN the frontend `NoteList.vue` calls the list API, THE Frontend SHALL pass `page` and `pageSize` parameters and render a pagination component.

#### 正确性属性

- **属性测试（不变量）**：对任意合法的 `pageSize=N` 请求，返回的 `records` 数组长度 ≤ N。
- **属性测试（不变量）**：`total` 字段的值等于满足筛选条件的数据库记录总数，与分页参数无关。
- **属性测试（变形）**：`page=1` 和 `page=2` 的返回结果中不存在重复的笔记 ID。

---

### 需求 3：点赞与浏览量原子操作 [P0]

**用户故事：** 作为平台运营者，我希望笔记的点赞数和浏览量在高并发下保持准确，以便统计数据真实可信。

#### 验收标准

1. WHEN `NoteService.likeNote()` is called, THE NoteService SHALL use a database atomic increment (`UPDATE note SET like_count = like_count + 1 WHERE id = ?`) instead of read-then-write.
2. WHEN `NoteService.increaseViewCount()` is called, THE NoteService SHALL use a database atomic increment for `view_count`.
3. WHERE Redis is available, THE RedisCache SHALL buffer view count increments and flush to the database periodically to reduce write pressure.
4. IF a user has already liked a note, THEN THE NoteService SHALL reject duplicate like requests with a `PARAMS_ERROR` and return the current like count.
5. THE NoteService SHALL use a `like_record` table to track per-user like status, preventing duplicate likes.

#### 正确性属性

- **属性测试（并发不变量）**：并发执行 N 次 `likeNote` 请求后，数据库中 `like_count` 的增量等于实际成功的请求数（去重后）。
- **属性测试（幂等性）**：同一用户对同一笔记重复点赞，`like_count` 只增加 1。
- **属性测试（并发不变量）**：并发执行 N 次 `increaseViewCount` 后，`view_count` 增量等于 N。

---

### 需求 4：评论状态逻辑一致性修复 [P0]

**用户故事：** 作为内容审核员，我希望评论的状态流转逻辑清晰一致，以便审核工作流正常运转。

#### 验收标准

1. WHEN a new comment is added via `CommentService.addComment()`, THE CommentService SHALL set the initial status to `"pending"` and the code comment SHALL accurately describe this as "待审核".
2. THE Platform SHALL define a single authoritative set of valid comment statuses: `pending`（待审核）、`approved`（已通过）、`rejected`（已拒绝）、`hidden`（已隐藏）.
3. WHEN `CommentController.approveComment()` validates the status parameter, THE CommentController SHALL accept `"approved"` and `"rejected"` as valid values.
4. WHEN `CommentServiceImpl.approveComment()` validates the status parameter, THE CommentServiceImpl SHALL accept the same set of values as the Controller layer.
5. IF a status value not in the defined set is passed to any comment status method, THEN THE CommentService SHALL throw `PARAMS_ERROR`.

#### 正确性属性

- **示例测试**：调用 `addComment` 后查询该评论，验证 `status` 字段值为 `"pending"`。
- **示例测试**：以 `status="rejected"` 调用 `approveComment`，验证 Service 层不抛出异常且数据库记录更新为 `"rejected"`。
- **示例测试**：以 `status="hidden"` 调用 Controller 层 `approveComment`，验证返回 `PARAMS_ERROR`（Controller 不接受 hidden，hidden 由管理员通过独立接口操作）。

---


### 需求 5：统一权限拦截器 [P1]

**用户故事：** 作为后端架构师，我希望权限校验逻辑集中在拦截器中，而不是分散在每个 Controller 方法里，以便降低遗漏鉴权的风险。

#### 验收标准

1. THE Platform SHALL implement a single `AuthInterceptor` (Spring HandlerInterceptor or AOP) that handles login state verification for all protected endpoints.
2. THE Platform SHALL define a `@LoginRequired` annotation to mark endpoints that require authentication.
3. THE Platform SHALL define a `@AdminRequired` annotation to mark endpoints that require admin role.
4. WHEN an unauthenticated request reaches a `@LoginRequired` endpoint, THE AuthInterceptor SHALL return `ErrorCode.NOT_LOGIN` (code 40100) without invoking the controller method.
5. WHEN a non-admin request reaches an `@AdminRequired` endpoint, THE AuthInterceptor SHALL return `ErrorCode.NO_AUTH` (code 40101) without invoking the controller method.
6. THE Platform SHALL remove all duplicated `isAdmin()` and `isLogin()` private methods from individual Controller classes after the interceptor is in place.

#### 正确性属性

- **示例测试**：未登录状态下访问 `GET /note/list`，验证响应 code 为 40100。
- **示例测试**：普通用户访问 `POST /comment/approve`，验证响应 code 为 40101。
- **属性测试（不变量）**：对所有标注 `@LoginRequired` 的接口，未携带有效 session 的请求均返回 40100，不存在例外。

---

### 需求 6：密码加密升级为 BCrypt [P1]

**用户故事：** 作为安全负责人，我希望用户密码使用 BCrypt 算法存储，以便即使数据库泄露也无法通过彩虹表破解密码。

#### 验收标准

1. WHEN a user registers, THE UserService SHALL hash the password using BCrypt (`BCryptPasswordEncoder`) instead of MD5+salt.
2. WHEN a user logs in, THE UserService SHALL verify the password using `BCryptPasswordEncoder.matches()`.
3. WHEN a user changes their password, THE UserService SHALL hash the new password using BCrypt before storing.
4. THE Platform SHALL provide a one-time migration script that re-encrypts existing MD5 passwords to BCrypt upon first login.
5. THE UserService SHALL NOT store plaintext passwords or reversible encrypted passwords at any point.

#### 正确性属性

- **示例测试（轮回属性）**：注册用户后，数据库中存储的密码字段以 `$2a$` 开头（BCrypt 格式标识）。
- **示例测试（轮回属性）**：`BCryptPasswordEncoder.matches(rawPassword, storedHash)` 对注册时使用的密码返回 `true`，对任意其他字符串返回 `false`。
- **属性测试（错误条件）**：对任意长度 < 8 的密码，注册接口返回 `PARAMS_ERROR`。

---

### 需求 7：XSS 防护 [P1]

**用户故事：** 作为平台用户，我希望评论和笔记内容中的恶意脚本被过滤，以便我的浏览器不会执行他人注入的 JavaScript 代码。

#### 验收标准

1. THE XssFilter SHALL sanitize all user-submitted text fields (comment content, note title, note summary, note content) before persistence.
2. WHEN content containing `<script>` tags or JavaScript event handlers (e.g., `onerror`, `onload`) is submitted, THE XssFilter SHALL strip or escape the malicious markup.
3. THE Platform SHALL use a whitelist-based HTML sanitizer (e.g., jsoup `Whitelist`) for note content that supports Markdown-rendered HTML.
4. THE Platform SHALL apply XSS filtering as a global `HttpServletRequestWrapper` or Spring `@ControllerAdvice`, not per-field in each service.
5. WHERE note content is Markdown, THE Platform SHALL sanitize the rendered HTML output, not the raw Markdown source.

#### 正确性属性

- **示例测试**：提交评论内容 `<script>alert(1)</script>`，查询返回的内容中不包含 `<script>` 标签。
- **属性测试（不变量）**：对任意包含 HTML 标签的评论内容，存储和返回的内容中不包含可执行的 JavaScript 片段。
- **属性测试（轮回属性）**：纯文本内容（不含 HTML 标签）经过 XSS 过滤后内容不变。

---

### 需求 8：登录状态持久化（Session 替换为 JWT）[P1]

**用户故事：** 作为平台用户，我希望在前后端分离部署场景下登录状态不会因跨域或服务重启而丢失，以便获得稳定的使用体验。

#### 验收标准

1. WHEN a user successfully logs in, THE UserService SHALL generate a JWT token and return it in the response body.
2. THE UserService SHALL set JWT expiration to 7 days for normal login and 30 days when "记住我" is selected.
3. WHEN a request carries a valid JWT token in the `Authorization: Bearer <token>` header, THE AuthInterceptor SHALL authenticate the user without relying on HTTP session.
4. WHEN a JWT token is expired or invalid, THE AuthInterceptor SHALL return `ErrorCode.NOT_LOGIN` (code 40100).
5. THE Frontend RequestInterceptor SHALL attach the JWT token from `localStorage` to every request's `Authorization` header.
6. THE Frontend UserStore SHALL store user info and token exclusively in `localStorage`, removing all `sessionStorage` usage for auth state.
7. WHEN a user logs out, THE Frontend UserStore SHALL clear the token from `localStorage` and the backend SHALL add the token to a Redis blacklist until expiration.

#### 正确性属性

- **示例测试（轮回属性）**：登录获取 token → 携带 token 访问 `/user/current` → 返回正确的用户信息。
- **示例测试**：token 过期后访问受保护接口，返回 code 40100。
- **示例测试**：登出后使用原 token 访问受保护接口，返回 code 40100（黑名单生效）。
- **属性测试（不变量）**：前端 `UserStore` 中不存在任何对 `sessionStorage` 的读写操作。

---


### 需求 9：Redis 缓存优化 [P1]

**用户故事：** 作为平台运营者，我希望热点数据（笔记列表、热榜、用户信息）从 Redis 缓存读取，以便减少数据库压力并提升接口响应速度。

#### 验收标准

1. WHEN `GET /note/list` is called with the same parameters within 5 minutes, THE RedisCache SHALL return the cached result without querying the database.
2. THE RedisCache SHALL set a TTL of 5 minutes for note list cache and 1 minute for hot rank cache.
3. WHEN a note is created, updated, or deleted, THE RedisCache SHALL invalidate all note list cache entries.
4. WHEN `GET /user/current` is called, THE RedisCache SHALL cache the user info with a TTL of 10 minutes, keyed by user ID.
5. THE Platform SHALL use `@Cacheable` and `@CacheEvict` annotations (Spring Cache + Redis) for cache management.
6. IF Redis is unavailable, THEN THE Platform SHALL fall back to direct database queries without throwing an exception.

#### 正确性属性

- **属性测试（幂等性）**：对同一参数的笔记列表接口连续调用 N 次，每次返回结果相同。
- **属性测试（不变量）**：笔记更新后，下一次列表查询返回的数据反映最新状态（缓存已失效）。
- **示例测试**：Redis 不可用时，接口仍正常返回数据（降级验证）。

---

### 需求 10：全文搜索功能 [P1]

**用户故事：** 作为平台用户，我希望能通过关键词搜索笔记的标题和正文内容，以便快速找到所需知识。

#### 验收标准

1. THE SearchService SHALL support keyword search across note `title`, `summary`, and `content` fields.
2. WHEN a search keyword is provided, THE SearchService SHALL return results ranked by relevance score (title match > summary match > content match).
3. THE SearchService SHALL support pagination with the same `page`/`pageSize` parameters as the note list API.
4. WHEN the keyword is empty or blank, THE SearchService SHALL return the default note list (equivalent to no filter).
5. THE SearchService SHALL respond within 500ms for keyword searches on datasets up to 100,000 records.
6. THE Frontend NoteList.vue SHALL trigger a backend search API call on keyword input (debounced 300ms), replacing the current client-side filter.

#### 正确性属性

- **属性测试（不变量）**：对任意非空关键词，返回结果中每条记录的 `title`、`summary` 或 `content` 至少有一个字段包含该关键词（或其分词结果）。
- **属性测试（变形）**：搜索结果数量 ≤ 全量笔记数量。
- **属性测试（不变量）**：空关键词搜索的结果集与无关键词的列表查询结果集相同。

---

### 需求 11：笔记置顶功能 [P2]

**用户故事：** 作为管理员，我希望能将重要笔记置顶显示，以便用户第一时间看到精选内容。

#### 验收标准

1. THE Platform SHALL add an `is_top` field (tinyint, default 0) to the `note` table.
2. WHEN an admin calls `POST /note/top/{id}`, THE NoteService SHALL set `is_top = 1` for the specified note.
3. WHEN `GET /note/list` is called, THE NoteService SHALL return pinned notes (`is_top = 1`) before non-pinned notes, regardless of sort type.
4. IF a non-admin user calls the top/unpin API, THEN THE AuthInterceptor SHALL return `ErrorCode.NO_AUTH`.
5. THE Platform SHALL support at most 10 simultaneously pinned notes; exceeding this limit SHALL return `PARAMS_ERROR`.

#### 正确性属性

- **属性测试（不变量）**：笔记列表中，所有 `is_top=1` 的笔记的索引位置均小于所有 `is_top=0` 的笔记的索引位置。
- **示例测试**：置顶超过 10 条时，第 11 次置顶操作返回 `PARAMS_ERROR`。

---

### 需求 12：热榜功能 [P2]

**用户故事：** 作为平台用户，我希望看到当日/本周热度最高的笔记榜单，以便发现优质内容。

#### 验收标准

1. THE HotRankService SHALL calculate a hot score for each note using the formula: `score = view_count + like_count * 3 + comment_count * 2`.
2. THE HotRankService SHALL provide `GET /note/hot` endpoint returning the top 20 notes by hot score.
3. THE RedisCache SHALL cache the hot rank list with a TTL of 10 minutes.
4. THE HotRankService SHALL support `period` parameter: `day`（今日）and `week`（本周），defaulting to `day`.
5. WHEN note stats change, THE HotRankService SHALL update the hot score in Redis using sorted set (`ZADD`).

#### 正确性属性

- **属性测试（不变量）**：热榜列表中，相邻两条记录满足 `score[i] >= score[i+1]`（降序排列）。
- **属性测试（不变量）**：热榜返回的记录数 ≤ 20。
- **属性测试（变形）**：热榜中的所有笔记 ID 在笔记表中均存在且状态为 `published`。

---

### 需求 13：后台用户封禁与内容审核工作流 [P1]

**用户故事：** 作为管理员，我希望能封禁违规用户并对待审核内容进行审批，以便维护平台内容质量。

#### 验收标准

1. THE AdminService SHALL provide `POST /admin/user/ban/{id}` to set `user_status = 1` (banned) for a user.
2. WHEN a banned user attempts to log in, THE UserService SHALL return `ErrorCode.FORBIDDEN` with message "账号已被封禁".
3. THE AdminService SHALL provide `GET /admin/content/pending` to list all notes and comments with `status = "pending"`.
4. WHEN an admin approves a note via `POST /admin/note/approve/{id}`, THE AdminService SHALL set `status = "published"`.
5. WHEN an admin rejects a note via `POST /admin/note/reject/{id}`, THE AdminService SHALL set `status = "rejected"` and notify the author.
6. THE Platform SHALL record all admin operations (ban, approve, reject) in an `admin_log` table with operator ID, target ID, action, and timestamp.
7. IF a non-admin user accesses any `/admin/**` endpoint, THEN THE AuthInterceptor SHALL return `ErrorCode.NO_AUTH`.

#### 正确性属性

- **示例测试**：封禁用户后，该用户登录返回 `FORBIDDEN` 错误码。
- **属性测试（不变量）**：`status = "pending"` 的笔记不出现在公开的 `GET /note/list` 返回结果中。
- **示例测试**：审核通过笔记后，该笔记出现在公开列表中。

---


### 需求 14：前端 Store 与请求拦截器一致性修复 [P0]

**用户故事：** 作为前端开发者，我希望登录状态存储和请求拦截器使用一致的存储策略，以便避免登录状态读取不一致导致的 Bug。

#### 验收标准

1. THE UserStore SHALL use `localStorage` exclusively for persisting user info and token; all `sessionStorage` references SHALL be removed.
2. THE RequestInterceptor SHALL read the token from `localStorage.getItem('userInfo')` consistently with how `UserStore` writes it.
3. WHEN `UserStore.login()` is called, THE UserStore SHALL NOT check `res.code === 0` because the `RequestInterceptor` already throws on non-zero codes; the login action SHALL only handle the success path and the `catch` block for errors.
4. THE Frontend SHALL remove the `openAllNotifications` debug function and the "你是猪吗" notification text from `Login.vue`.
5. THE Frontend NoteList.vue SHALL remove all client-side filter and sort logic, delegating these operations to the backend API.

#### 正确性属性

- **示例测试**：登录成功后刷新页面，`UserStore.isLogin` 仍为 `true`（localStorage 持久化验证）。
- **示例测试**：在 `Login.vue` 的源代码中搜索 `"你是猪吗"` 字符串，结果为空。
- **示例测试**：模拟后端返回 `code=40100`，验证 `UserStore.login()` 进入 `catch` 分支而非 `else` 分支。

---

### 需求 15：代码规范与结构分层优化 [P2]

**用户故事：** 作为团队开发者，我希望代码遵循统一的命名、注释和分层规范，以便降低维护成本和新人上手难度。

#### 验收标准

1. THE Platform SHALL extract all duplicated `isAdmin()` and `isLogin()` methods from Controller classes into a shared `BaseController` abstract class or `AuthUtils` utility class.
2. THE Platform SHALL add Javadoc comments to all public Service interface methods describing parameters, return values, and thrown exceptions.
3. THE Platform SHALL use `@Valid` and JSR-303 annotations on request DTOs instead of manual null checks in Controller methods.
4. THE Platform SHALL follow a consistent naming convention: entity classes use PascalCase, database columns use snake_case, Java fields use camelCase.
5. THE Platform SHALL separate request/response DTOs from domain entity classes; domain entities SHALL NOT be used directly as request body parameters in Controller methods.
6. THE NoteService SHALL NOT call `userMapper` directly; user information retrieval SHALL go through `UserService`.

#### 正确性属性

- **示例测试（代码扫描）**：在所有 Controller 类中搜索 `private boolean isAdmin` 和 `private boolean isLogin`，结果为空（已迁移到基类）。
- **示例测试**：提交缺少必填字段的注册请求，验证返回 `PARAMS_ERROR` 且错误信息明确指出缺失字段。

---

### 需求 16：数据库索引与约束优化 [P1]

**用户故事：** 作为 DBA，我希望关键查询字段有合适的索引，外键约束完整，以便保证查询性能和数据一致性。

#### 验收标准

1. THE Platform SHALL add a composite index on `note(status, publish_time)` to optimize the default list query.
2. THE Platform SHALL add an index on `note(author_id)` to optimize author-based queries.
3. THE Platform SHALL add a unique index on `like_record(user_id, note_id)` to prevent duplicate likes at the database level.
4. THE Platform SHALL add an index on `comment(note_id, status)` to optimize comment list queries.
5. THE Platform SHALL add an index on `view_record(user_id, note_id)` to optimize browse history queries.
6. THE Platform SHALL define foreign key constraints (or application-level equivalent checks) between `comment.note_id → note.id` and `comment.user_id → user.id`.
7. THE Platform SHALL add `NOT NULL` constraints on all fields that are logically required (e.g., `note.title`, `note.author_id`, `user.user_account`).

#### 正确性属性

- **示例测试**：执行 `EXPLAIN SELECT * FROM note WHERE status='published' ORDER BY publish_time DESC LIMIT 10`，验证 `key` 列使用了 `idx_status_publish_time` 索引。
- **示例测试**：尝试插入重复的 `(user_id, note_id)` 到 `like_record` 表，验证数据库返回唯一约束错误。

---

### 需求 17：异步处理优化 [P2]

**用户故事：** 作为平台用户，我希望发布笔记、上传资源等操作响应迅速，不因后台任务（如发送通知、更新统计）而阻塞。

#### 验收标准

1. WHEN a note is published, THE Platform SHALL process notification sending and statistics update asynchronously using `@Async`.
2. WHEN a comment is added, THE Platform SHALL update `note.comment_count` asynchronously.
3. THE Platform SHALL configure a dedicated thread pool for async tasks with core size 5, max size 20, queue capacity 100.
4. IF an async task fails, THE Platform SHALL log the error with sufficient context (task type, entity ID, error message) without affecting the main request response.

#### 正确性属性

- **示例测试**：发布笔记接口的响应时间 < 200ms（不含异步任务执行时间）。
- **属性测试（不变量）**：异步任务失败不影响主接口返回成功响应。

---

### 需求 18：线上 BUG 修复——笔记查不到 [P0]

**用户故事：** 作为平台用户，我希望已发布的笔记能在列表中正常显示，以便我能找到并阅读内容。

#### 验收标准

1. WHEN `GET /note/list` is called without a `status` parameter, THE NoteService SHALL default to querying only `status = "published"` notes.
2. THE Platform SHALL verify that the `note` table's `is_delete` field is correctly handled by MyBatis-Plus `@TableLogic`, ensuring logically deleted notes are excluded from all queries.
3. WHEN a note is created with `status = "published"`, THE NoteService SHALL set `publish_time` to the current timestamp.
4. THE Platform SHALL add an integration test that creates a note, publishes it, and verifies it appears in the list API response.

#### 正确性属性

- **示例测试（轮回属性）**：创建笔记（status=published）→ 调用列表接口 → 返回结果中包含该笔记 ID。
- **属性测试（不变量）**：列表接口返回的所有笔记的 `status` 字段均为 `"published"`，不存在 `"draft"` 或 `"deleted"` 状态的记录。

---

### 需求 19：可扩展性——模块化与低耦合 [P2]

**用户故事：** 作为架构师，我希望各业务模块之间通过接口而非直接依赖实现类交互，以便后续迭代时能独立修改各模块。

#### 验收标准

1. THE Platform SHALL define Service interfaces for all business services; Controller classes SHALL only depend on Service interfaces, not implementation classes.
2. THE Platform SHALL use Spring Events (`ApplicationEventPublisher`) for cross-module communication (e.g., note published → update stats, send notification).
3. THE Platform SHALL extract common constants (status values, role values, cache key prefixes) into dedicated constant classes, eliminating magic strings scattered across the codebase.
4. THE Platform SHALL ensure no circular dependencies between service implementation classes.
5. WHERE configuration values (e.g., JWT secret, OSS bucket name, Redis TTL) are used, THE Platform SHALL read them from `application.yml` via `@Value` or `@ConfigurationProperties`, not hardcode them.

#### 正确性属性

- **示例测试（代码扫描）**：在 Controller 类中搜索 `@Autowired.*ServiceImpl`，结果为空（Controller 只依赖接口）。
- **示例测试（代码扫描）**：在代码中搜索硬编码的 JWT secret 或 OSS access key，结果为空。

---

## 优先级汇总

| 优先级 | 需求编号 | 需求名称 |
|--------|----------|----------|
| P0 | 需求 1 | 统一接口响应格式 |
| P0 | 需求 2 | 笔记列表后端分页 |
| P0 | 需求 3 | 点赞与浏览量原子操作 |
| P0 | 需求 4 | 评论状态逻辑一致性修复 |
| P0 | 需求 14 | 前端 Store 与请求拦截器一致性修复 |
| P0 | 需求 18 | 线上 BUG 修复——笔记查不到 |
| P1 | 需求 5 | 统一权限拦截器 |
| P1 | 需求 6 | 密码加密升级为 BCrypt |
| P1 | 需求 7 | XSS 防护 |
| P1 | 需求 8 | 登录状态持久化（JWT） |
| P1 | 需求 9 | Redis 缓存优化 |
| P1 | 需求 10 | 全文搜索功能 |
| P1 | 需求 13 | 后台用户封禁与内容审核工作流 |
| P1 | 需求 16 | 数据库索引与约束优化 |
| P2 | 需求 11 | 笔记置顶功能 |
| P2 | 需求 12 | 热榜功能 |
| P2 | 需求 15 | 代码规范与结构分层优化 |
| P2 | 需求 17 | 异步处理优化 |
| P2 | 需求 19 | 可扩展性——模块化与低耦合 |
