# 编程知识星球 — 功能实现逐行解析

> 本文档按"用户操作 → 前端代码 → 后端代码 → 数据库"的顺序，逐个讲解每个功能的完整实现链路。

---

## 目录
1. [登录功能（最核心，必须先懂）](#1-登录功能)
2. [路由守卫（权限控制）](#2-路由守卫)
3. [请求拦截器（前后端通信桥梁）](#3-请求拦截器)
4. [注册功能](#4-注册功能)
5. [笔记列表与详情](#5-笔记列表与详情)
6. [评论系统](#6-评论系统)
7. [点赞功能](#7-点赞功能)
8. [关注系统](#8-关注系统)
9. [搜索功能](#9-搜索功能)
10. [资源管理](#10-资源管理)
11. [知识星球（多租户）](#11-知识星球)
12. [实时通知（WebSocket）](#12-实时通知)
13. [AI 聊天](#13-ai-聊天)

---

## 1. 登录功能

### 用户点击「登录」按钮后，发生了什么？

#### 第一步：前端表单验证

```vue
<!-- Login.vue 第 165-175 行 -->
<a-button
  :disabled="disabled"     ← 账号或密码为空时按钮禁用
  type="primary"
  @click.prevent="sendMsg" ← 点击触发 sendMsg 函数
>
  登录
</a-button
```

`disabled` 的计算逻辑（第 77-79 行）：
```javascript
const disabled = computed(() => {
  return !(formState.userAccount && formState.userPassword)
  // 账号和密码都有值 → false → 按钮可点击
})
```

#### 第二步：sendMsg 函数执行（第 40-69 行）

```javascript
const sendMsg = async () => {
  // 1. 先做表单校验（Ant Design Vue 内置的 rules 规则）
  await loginFormRef.value.validate()
  // 如果校验失败，会抛出异常，后面的代码不执行

  // 2. 调用 store 的 login 方法
  const loginSuccess = await store.login(
    formState.userAccount,   // 用户输入的账号
    formState.userPassword,  // 用户输入的密码
    formState.remember       // 是否记住我
  )

  // 3. 登录成功后的跳转逻辑
  if (loginSuccess) {
    showFireworks.value = true  // 显示烟花动画
    const userRole = Number(store.userRole)
    const defaultPath = userRole === 1 ? '/main/contentManage' : '/user/home'
    // 管理员 → 管理后台，普通用户 → 用户首页

    // 检查是否有 redirect 参数（从哪来的？见路由守卫讲解）
    const redirectPath = route.query.redirect
    const targetPath = redirectPath && redirectPath !== '/login'
      ? String(redirectPath)  // 跳回之前想访问的页面
      : defaultPath           // 否则去默认页面

    setTimeout(() => router.replace(targetPath), 100)
  }
}
```

#### 第三步：store.login 做了什么（store/userLogin.js 第 36-51 行）

```javascript
async login(userAccount, userPassword, rememberMe = false) {
  // 1. 调用后端 API
  const res = await apiLogin({ userAccount, userPassword, rememberMe })
  // 这里实际发送的是：POST /api/user/login
  // 请求体：{ userAccount: "xxx", userPassword: "xxx", rememberMe: true }

  // 2. 取出返回数据
  const userInfo = { ...res.data }
  // res.data 的结构是后端返回的 User 对象，包含：
  // { id, userAccount, userRole, userStatus, avatarUrl, username, phone, token, ... }

  // 3. 检查 token 是否存在
  if (!userInfo.token) {
    throw new Error('登录成功但未获取到 token')
  }

  // 4. 把用户信息存到 Pinia store
  this.setUserInfo(userInfo)
  // 这一步会把 token 存到 this.token

  // 5. 因为配置了 persist，store 的数据自动同步到 localStorage
  //    persist 配置在第 67-73 行：
  //    persist: { key: 'userLogin', storage: localStorage, pick: [...] }
  //    这意味着关闭浏览器后重新打开，token 还在，不需要重新登录

  message.success('登录成功')
  return true
}
```

#### 第四步：后端收到请求后做了什么

后端 `UserController` 的 `/user/login` 接口：

```java
@PostMapping("/login")
@RateLimit(key = "login", windowSeconds = 60, maxRequests = 10)  // 60 秒窗口内最多 10 次，防暴力破解
public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest request) {
    // 1. 参数校验
    if (request == null) throw new BusinessException(ErrorCode.PARAMS_ERROR);

    String userAccount = request.getUserAccount();
    String userPassword = request.getUserPassword();

    // 2. 调用 Service 层
    LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request.isRememberMe());

    // 3. 返回统一格式
    return ResultUtils.success(loginUserVO);
}
```

#### 第五步：Service 层的登录逻辑（UserServiceImpl.userLogin）

```java
public LoginUserVO userLogin(String userAccount, String userPassword, boolean rememberMe) {
    // 1. 参数校验
    if (StringUtils.isBlank(userAccount) || userPassword == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
    }
    if (userAccount.length() < 4) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4");
    }

    // 2. 查询数据库
    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("userAccount", userAccount);
    User user = userMapper.selectOne(queryWrapper);
    // SQL: SELECT * FROM user WHERE userAccount = 'xxx' AND is_delete = 0
    // 注意：is_delete = 0 是 MyBatis-Plus 逻辑删除自动加的条件

    if (user == null) {
        throw new BusinessException(ErrorCode.LOGIN_FAILED, "账号不存在");
    }

    // 3. 验证密码（支持两种加密方式）
    boolean passwordMatch;
    if (user.getUserPassword().startsWith("$2a$") || user.getUserPassword().startsWith("$2b$")) {
        // BCrypt 加密（新方式，安全）
        passwordMatch = BCrypt.checkpw(userPassword, user.getUserPassword());
    } else {
        // MD5 加密（旧方式，兼容旧数据）
        passwordMatch = DigestUtils.md5Hex(userPassword).equals(user.getUserPassword());
        if (passwordMatch) {
            // 旧密码验证通过后，自动升级为 BCrypt
            user.setUserPassword(BCrypt.hashpw(userPassword, BCrypt.gensalt()));
            userMapper.updateById(user);
        }
    }

    if (!passwordMatch) {
        throw new BusinessException(ErrorCode.LOGIN_FAILED, "密码错误");
    }

    // 4. 检查账号状态
    if (user.getUserStatus() == 1) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁");
    }

    // 5. 生成 JWT Token（实际逻辑位于 UserController.userLogin）
    //    记住我 = 30 天；否则取配置值 spring.jwt.expiration（application-dev.yml 默认 86400 秒 = 1 天）
    long expireTime = rememberMe ? 30L * 24 * 3600 : jwtUtils.getExpiration();
    // 多租户：把用户的「当前星球」ID 一起写进 claim（未加入任何星球时为 null）
    // resolveCurrentStarId：优先 user.current_star_id；为空则回退「最早加入的星球」并回写
    Long starId = userService.resolveCurrentStarId(user.getId());
    String token = jwtUtils.generateToken(user.getId(), user.getUserRole(), starId, expireTime);

    // 6. 构造返回对象（脱敏后的用户信息）
    LoginUserVO loginUserVO = new LoginUserVO();
    loginUserVO.setId(user.getId());
    loginUserVO.setUserAccount(user.getUserAccount());
    loginUserVO.setUserRole(user.getUserRole());
    loginUserVO.setToken(token);
    // ... 其他字段

    return loginUserVO;
}
```

#### 第六步：JWT Token 是什么？

```java
// JwtUtils.generateToken 内部做了什么（4 个参数，包含 starId）
public String generateToken(Long userId, Integer userRole, Long starId, long expirationSeconds) {
    // JWT = Header.Payload.Signature
    // Payload 包含：{ "userId": 123, "userRole": 1, "starId": 456, "iat": ..., "exp": ... }
    // starId 为 null（用户未加入任何星球）时不写该 claim
    JwtBuilder builder = Jwts.builder()
        .claim("userId", userId)          // 把用户ID塞进Token
        .claim("userRole", userRole)      // 把角色塞进Token
        .issuedAt(new Date())             // 签发时间
        .expiration(expireDate)           // 过期时间
        .signWith(getSigningKey());       // 用密钥签名

    if (starId != null) {
        builder.claim("starId", starId);  // 多租户标识：用户的「默认星球」
    }
    return builder.compact();
}
```

**关键理解：** JWT Token 里包含了用户信息，后端不需要查数据库就知道"这是谁"。Token 由密钥签名，前端无法篡改。

#### 第七步：完整流程图

```
用户输入账号密码 → 点击登录
        ↓
前端 Login.vue sendMsg()
        ↓
store.login() 调用 apiLogin()
        ↓
axios POST /api/user/login  ← request.js 拦截器自动加 Authorization header
        ↓
后端 UserController.userLogin()
        ↓
UserServiceImpl.userLogin()
  → 查数据库验证账号密码
  → 生成 JWT Token（claim 仅含 userId、userRole）
  → 返回脱敏用户信息 + Token
        ↓
前端收到响应 → response 拦截器检查 code === 0
        ↓
store.setUserInfo() 保存用户信息 + Token
  → pinia-plugin-persistedstate 自动写入 localStorage
        ↓
根据 userRole 跳转：管理员→管理后台，普通用户→首页
```

---

## 2. 路由守卫

### 为什么有些页面不用登录就能看，有些必须登录？

#### 路由定义中的权限标记（router.js）

```javascript
// 免登录页面 —— 没有 meta.requiresAuth
{ path: 'home', name: 'UserHome', component: UserHome },
{ path: 'notes', name: 'UserNoteList', component: NoteList },
{ path: 'noteDetail/:id', name: 'UserNoteDetail', component: NoteDetail },

// 需要登录的页面 —— 有 meta.requiresAuth: true
{ path: 'publish', name: 'UserNotePublish', component: NotePublish,
  meta: { requiresAuth: true } },

// 需要管理员权限的路由 —— 有 meta.requiresAdmin: true
{
  path: '/main',
  component: BasicLayout,
  meta: { requiresAuth: true, requiresAdmin: true },
  children: [...]
}
```

#### beforeEach 守卫（router.js 第 115-162 行）

```javascript
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // 从 store 读取用户状态
  let isAuthenticated = !!userStore.isLogin   // 是否已登录
  let isAdmin = Number(userStore.userRole) === 1  // 是否管理员
  let isBanned = Number(userStore.userStatus) === 1  // 是否被封禁

  // 规则1：已登录但被封禁 → 强制退出
  if (isAuthenticated && isBanned && to.path !== '/login') {
    userStore.logout()
    next({ path: '/login', query: { msg: 'banned' } })
    return
  }

  // 规则2：页面需要登录但未登录 → 跳转登录页，带上 redirect 参数
  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    // 例如：想访问 /user/publish → 跳到 /login?redirect=/user/publish
    // 登录成功后会读取这个 redirect 参数，跳回原来想去的页面
    return
  }

  // 规则3：已登录用户访问登录页 → 按角色跳转到对应首页
  if (to.path === '/login' && isAuthenticated) {
    const redirectTarget = isAdmin ? '/main/contentManage' : '/user/home'
    next(redirectTarget)
    return
  }

  // 规则4：普通用户访问管理端 → 拦截
  if ((to.meta.requiresAdmin || to.path.startsWith('/main')) && !isAdmin) {
    next(isAuthenticated ? '/user/home' : '/login')
    return
  }

  // 其他情况放行
  next()
})
```

#### 浏览器刷新后登录状态为什么还在？

```
浏览器刷新 → Vue 重新初始化 → useUserStore() 读取初始状态
        ↓
pinia-plugin-persistedstate 自动从 localStorage 读取 'userLogin' key
        ↓
store 恢复为上次保存的状态（包含 token、isLogin、userRole 等）
        ↓
路由守卫检查 userStore.isLogin → true → 放行
```

---

## 3. 请求拦截器

### 前端怎么自动给每个请求加 Token？

#### request.js 的请求拦截器（第 87-98 行）

```javascript
request.interceptors.request.use(
  (config) => {
    const token = getToken()  // 从 localStorage 读取 token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
      // 在每个请求的 header 里加上：Authorization: Bearer eyJhbGciOiJI...
    }
    return config
  }
)
```

**所以前端每次发请求，后端都能通过这个 header 知道"是谁在请求"。**

#### getToken 函数怎么找 Token？（第 7-21 行）

```javascript
const getToken = () => {
  // 优先从 pinia-plugin-persistedstate 的存储位置读
  try {
    const userLogin = JSON.parse(localStorage.getItem('userLogin') || '{}')
    if (userLogin.token) return userLogin.token  // 找到了
  } catch {}
  // 兼容旧版存储方式
  const localToken = localStorage.getItem('token')
  if (localToken) return localToken
  return ''
}
```

#### 响应拦截器怎么处理后端返回？（第 39-84 行）

```javascript
request.interceptors.response.use(
  (response) => {
    const res = normalizeBaseResponse(response.data)
    // 后端返回：{ code: 0, data: {...}, message: "ok", description: "ok" }

    if (res.code !== API_ERROR_CODES.SUCCESS) {
      // code !== 0 说明业务出错
      const error = new Error(res.description || '请求失败')
      error.isBusinessError = true
      error.code = res.code
      throw error  // 抛出异常，被 catch 捕获
    }
    return res  // code === 0，返回数据
  },
  (error) => {
    // HTTP 状态码异常处理
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        message.warning('未登录或登录已过期，请重新登录')
        clearAuthStorage()        // 清除 localStorage
        router.replace('/login')  // 跳转登录页
      } else if (status === 403) {
        message.error('无权限执行该操作')
      }
    }
  }
)
```

---

## 4. 注册功能

### 前端调用

```javascript
// api.js
export const userRegister = (body) =>
  request.post('/user/register', body)
  // body: { userAccount, userPassword, checkPassword, phone, email }
```

### 后端 Service 校验逻辑

```java
public long userRegister(String userAccount, String userPassword, String checkPassword) {
    // 1. 参数非空校验
    if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
    }

    // 2. 账号长度校验
    if (userAccount.length() < 4) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4");
    }

    // 3. 密码长度校验
    if (userPassword.length() < 8 || checkPassword.length() < 8) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8");
    }

    // 4. 账号不能包含特殊字符
    String validPattern = "[`~!@#$%^&*()+=|{}':;',\\\\[\\\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。，、？]";
    Matcher matcher = Pattern.compile(validPattern).matcher(userAccount);
    if (matcher.find()) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号包含特殊字符");
    }

    // 5. 两次密码必须一致
    if (!userPassword.equals(checkPassword)) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
    }

    // 6. 账号不能重复
    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("userAccount", userAccount);
    long count = userMapper.selectCount(queryWrapper);
    if (count > 0) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
    }

    // 7. 密码加密（BCrypt，每次加密结果不同，安全）
    String encryptPassword = BCrypt.hashpw(userPassword, BCrypt.gensalt());

    // 8. 插入数据库
    User user = new User();
    user.setUserAccount(userAccount);
    user.setUserPassword(encryptPassword);
    user.setUserRole(0);  // 默认普通用户
    userMapper.insert(user);
    // SQL: INSERT INTO user (userAccount, userPassword, userRole) VALUES (?, ?, 0)

    return user.getId();
}
```

---

## 5. 笔记列表与详情

### 笔记列表 — 分页查询

#### 前端调用

```javascript
// api.js
export const getNoteList = (params) =>
  request.get('/note/list', { params })
  // params: { page: 1, pageSize: 10, category: "frontend", keyword: "Vue" }
```

#### 后端 Controller

```java
@GetMapping("/list")
public BaseResponse<PageResult<NoteVO>> listNotes(NoteQueryRequest request) {
    // request 里包含：page, pageSize, category, keyword, sortOrder 等
    PageResult<NoteVO> result = noteService.listNotes(request);
    return ResultUtils.success(result);
}
```

#### Service 分页逻辑

```java
public PageResult<NoteVO> listNotes(NoteQueryRequest request) {
    // 1. 构建 MyBatis-Plus 分页对象
    Page<Note> page = new Page<>(request.getPage(), request.getPageSize());

    // 2. 构建查询条件
    QueryWrapper<Note> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("status", "published");  // 只查已发布的

    // 动态条件：按分类筛选
    if (StringUtils.isNotBlank(request.getCategory())) {
        queryWrapper.eq("category", request.getCategory());
    }

    // 动态条件：关键词搜索
    if (StringUtils.isNotBlank(request.getKeyword())) {
        queryWrapper.and(wrapper ->
            wrapper.like("title", request.getKeyword())
                   .or()
                   .like("content", request.getKeyword())
        );
        // SQL: WHERE (title LIKE '%Vue%' OR content LIKE '%Vue%')
    }

    // 排序
    if ("hot".equals(request.getSortOrder())) {
        queryWrapper.orderByDesc("like_count", "view_count");  // 热度排序
    } else {
        queryWrapper.orderByDesc("create_time");  // 默认最新排序
    }

    // 3. 执行查询
    Page<Note> notePage = noteMapper.selectPage(page, queryWrapper);
    // SQL: SELECT * FROM note WHERE status='published' AND is_delete=0
    //      ORDER BY create_time DESC LIMIT 10 OFFSET 0

    // 4. 转换为 VO（包含作者信息、点赞数等）
    List<NoteVO> noteVOList = notePage.getRecords().stream()
        .map(this::convertToVO)
        .collect(Collectors.toList());

    // 5. 封装分页结果
    return new PageResult<>(noteVOList, notePage.getTotal());
}
```

### 笔记详情 — 包含浏览量统计

```java
@GetMapping("/{id}")
@LoginRequired(false)  // 不强制登录也能看
public BaseResponse<NoteVO> getNoteById(@PathVariable Long id) {
    NoteVO noteVO = noteService.getNoteById(id);
    return ResultUtils.success(noteVO);
}
```

#### 浏览量怎么统计的？

```java
public NoteVO getNoteById(Long id) {
    // 1. 查笔记
    Note note = noteMapper.selectById(id);
    if (note == null || note.getIsDelete() == 1) {
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
    }

    // 2. 浏览量 +1（写 Redis，不写数据库）
    String viewKey = "note:view:" + id;
    redisTemplate.opsForValue().increment(viewKey);
    // 这一步极快，不访问数据库

    // 3. 转换为 VO 返回
    return convertToVO(note);
}
```

#### 定时任务把浏览量刷到数据库

```java
@Scheduled(fixedDelay = 60000)  // 距上次执行结束 60 秒后再跑一次
public void flushViewCount() {
    // 1. 找到所有 note:view:* 的 key
    Set<String> keys = redisTemplate.keys("note:view:*");

    for (String key : keys) {
        // 2. 读取并删除计数
        String noteIdStr = key.replace("note:view:", "");
        Object value = redisTemplate.opsForValue().getAndDelete(key);
        // getAndDelete: 原子操作，读完就删，不会重复计数

        if (value != null) {
            int count = Integer.parseInt(value.toString());
            // 3. 批量更新数据库
            noteMapper.incrementViewCount(Long.parseLong(noteIdStr), count);
            // SQL: UPDATE note SET view_count = view_count + 10 WHERE id = 123
        }
    }
}
```

**为什么用 Redis 而不是直接更新数据库？**
- 假设 1000 人同时看一篇笔记
- 直接 UPDATE：1000 次数据库写操作，数据库扛不住
- Redis 方案：1000 次内存 INCR（极快） + 1 次批量 UPDATE（每 60 秒）

---

## 6. 评论系统

### 发表评论

#### 前端调用

```javascript
// api.js 第 85-92 行
export const addComment = (body) => {
  const requestBody = {
    ...body,
    noteId: Number(body.noteId)  // 确保 noteId 是数字
  }
  return request.post('/comment/add', requestBody)
  // body: { noteId: 123, content: "写得真好！" }
}
```

### 回复评论（嵌套评论）

```javascript
// api.js 第 95-102 行
export const replyComment = (commentId, body) => {
  const requestBody = {
    ...body,
    noteId: Number(body.noteId)
  }
  return request.post(`/comment/reply/${Number(commentId)}`, requestBody)
  // commentId: 被回复的评论ID
  // body: { noteId: 123, content: "同意你的看法" }
}
```

### 后端评论数据结构

```sql
-- comment 表关键字段
CREATE TABLE comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT NOT NULL,        -- 属于哪篇笔记
    parent_id BIGINT DEFAULT NULL,  -- NULL = 一级评论，非NULL = 回复某条评论
    user_id BIGINT NOT NULL,        -- 谁发的
    content TEXT NOT NULL,          -- 评论内容
    is_delete INT DEFAULT 0,        -- 逻辑删除
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 前端展示嵌套评论

```vue
<!-- NoteDetail.vue 中评论列表的渲染逻辑 -->
<div v-for="comment in comments" :key="comment.id">
  <!-- 一级评论 -->
  <div class="comment-item">
    <a-avatar :src="comment.userAvatar" />
    <span>{{ comment.userName }}</span>
    <p>{{ comment.content }}</p>
    <button @click="replyTo(comment)">回复</button>
  </div>

  <!-- 二级评论（回复）缩进显示 -->
  <div v-for="reply in comment.replies" :key="reply.id"
       class="comment-reply" style="margin-left: 40px;">
    <span>{{ reply.userName }} 回复 {{ reply.replyToName }}</span>
    <p>{{ reply.content }}</p>
  </div>
</div>
```

---

## 7. 点赞功能

### 点赞/取消点赞的完整流程

#### 前端调用

```javascript
// api.js
export const likeNote = (noteId) =>
  request.post(`/like/note/${noteId}`)

export const unlikeNote = (noteId) =>
  request.post(`/unlike/note/${noteId}`)

export const checkLikeStatus = (noteId) =>
  request.get(`/like/status/note/${noteId}`)
```

#### 后端点赞逻辑（防重复设计）

```java
@PostMapping("/like/{id}")
@LoginRequired
@PreventDuplicate(waitTime = 0, leaseTime = 3, message = "点赞过于频繁，请稍后再试")
public BaseResponse<Map<String, Object>> likeNote(@PathVariable Long id, HttpServletRequest request) {
    boolean result = noteService.likeNote(id, request);
    if (result) {
        Note note = noteService.getById(id);
        // 返回最新的点赞数等数据
    }
    ...
}
```

```java
@Caching(evict = {
    @CacheEvict(value = "noteDetail", key = "#id"),
    @CacheEvict(value = "noteList", allEntries = true)
})
public boolean likeNote(Long id, HttpServletRequest request) {
    User loginUser = getLoginUser(request);

    Note note = this.getById(id);
    if (note == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记不存在");
    }

    // 1. 直接插入点赞记录，靠唯一索引 uk_user_target 保证幂等
    //    （不先 SELECT 再 INSERT，避免 TOCTOU 竞态窗口）
    LikeRecord likeRecord = new LikeRecord();
    likeRecord.setUserId(loginUser.getId());
    likeRecord.setTargetType("note");
    likeRecord.setTargetId(id);
    try {
        likeRecordMapper.insert(likeRecord);
    } catch (DuplicateKeyException e) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "已点赞，请勿重复操作");
    }

    // 2. INSERT 成功后再原子递增计数，保证一致性
    noteMapper.incrementLikeCount(id);

    // 3. 异步更新热度分（发布事件，避免 @Async 自调用失效）
    eventPublisher.publishEvent(new NoteHotScoreEvent(this, id));

    // 4. 给笔记作者 +2 积分
    pointsService.addPoints(note.getAuthorId(), 2, "like", "note", id, "笔记被点赞");

    return true;
}
```

**防重复的保障（本项目实际做法）：**
1. **数据库唯一索引** — `like_record` 表的 `uk_user_target(user_id, target_type, target_id)` 唯一约束是**唯一**的防重手段：直接 INSERT，撞约束即视为已点赞
2. **接口级分布式锁** — `@PreventDuplicate(leaseTime = 3)` 在 Controller 层拦截高频重复请求
3. **缓存驱逐** — 点赞后同步失效 `noteDetail` 与 `noteList` 缓存

> 注：本项目**没有**使用「Redis Set 判重」方案。

---

## 8. 关注系统

### 关注/取关 API

```javascript
// api.js
export const followUser = (followingId) =>
  request.post(`/follow/${followingId}`)

export const unfollowUser = (followingId) =>
  request.post(`/unfollow/${followingId}`)

export const checkFollowStatus = (followingId) =>
  request.get(`/follow/status/${followingId}`)

export const getFollowList = (params) =>
  request.get('/follow/list', { params })
  // params: { page: 1, pageSize: 10, type: 'following' | 'followers' }
```

### 前端关注按钮组件（FollowButton.vue）

```vue
<template>
  <button
    :class="['follow-btn', { 'is-following': isFollowing }]"
    @click="toggleFollow"
  >
    {{ isFollowing ? '已关注' : '关注' }}
  </button>
</template>

<script setup>
const props = defineProps({
  userId: Number,
  initialFollowing: Boolean
})

const isFollowing = ref(props.initialFollowing)

const toggleFollow = async () => {
  try {
    if (isFollowing.value) {
      await unfollowUser(props.userId)
      isFollowing.value = false
    } else {
      await followUser(props.userId)
      isFollowing.value = true
    }
  } catch (error) {
    message.error(error.description || '操作失败')
  }
}
</script>
```

---

## 9. 搜索功能

### Elasticsearch 全文搜索流程

#### 前端搜索（带防抖）

```javascript
// 搜索输入时，300ms 内只触发一次
const handleSearch = useDebounceFn(async (keyword) => {
  if (!keyword.trim()) {
    notes.value = defaultNotes
    return
  }
  const res = await searchNotes({ keyword, page: 1, pageSize: 20 })
  notes.value = res.data.records
}, 300)
// 用户快速输入 "V-u-e-3" → 只在输入停止 300ms 后才发一次请求搜索 "Vue3"
```

#### 后端 ES 搜索

```java
public PageResult<NoteVO> searchNotes(String keyword, int page, int pageSize) {
    // 1. 构建 ES 查询
    SearchRequest request = SearchRequest.of(s -> s
        .index("notes")
        .query(q -> q.multiMatch(m -> m
            .query(keyword)
            .fields("title^2", "content", "tags")  // title 权重 × 2
            .type(TextQueryType.BestFields)
            .analyzer("ik_max_word")  // IK 中文分词
        ))
        .highlight(h -> h
            .fields("title", f -> f.preTags("<em>").postTags("</em>"))
            .fields("content", f -> f.preTags("<em>").postTags("</em>"))
        )
        .from((page - 1) * pageSize)
        .size(pageSize)
    );

    // 2. 执行查询
    SearchResponse<NoteDoc> response = esClient.search(request, NoteDoc.class);

    // 3. 解析结果（带高亮）
    List<NoteVO> results = response.hits().hits().stream()
        .map(hit -> {
            NoteDoc doc = hit.source();
            NoteVO vo = convertDocToVO(doc);
            // 高亮显示匹配的文字
            if (hit.highlight().containsKey("title")) {
                vo.setTitle(hit.highlight().get("title").get(0));
                // 例如：<em>Vue</em>3 入门教程
            }
            return vo;
        })
        .collect(Collectors.toList());

    return new PageResult<>(results, response.hits().total().value());
}
```

---

## 10. 资源管理

### 资源上传流程

#### 前端

```javascript
// 1. 选择文件后上传到阿里云 OSS
const handleUpload = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await uploadFile(formData)  // POST /api/user/upload
  return res.data  // 返回文件 URL
}

// 2. 提交资源表单
const handleSubmit = async () => {
  const fileUrl = await handleUpload(formState.file)
  await addResource({
    title: formState.title,
    description: formState.description,
    resourceType: formState.resourceType,  // document/video/code/other
    category: formState.category,
    fileUrl: fileUrl,  // OSS 文件地址
    coverUrl: formState.coverUrl
  })
}
```

### 资源列表展示（带类型图标映射）

```javascript
// UserHome.vue 中的资源类型映射
const resourceTypeIcon = {
  document: FilePdfOutlined,   // 文档 → PDF 图标
  video: VideoCameraOutlined,  // 视频 → 摄像头图标
  code: CodeOutlined,          // 代码 → 代码图标
  other: FolderOutlined        // 其他 → 文件夹图标
}

const resourceTypeColor = {
  document: '#e74c3c',  // 红色
  video: '#9b59b6',     // 紫色
  code: '#2ecc71',      // 绿色
  other: '#95a5a6'      // 灰色
}
```

---

## 11. 知识星球（多租户）

### 什么是多租户？

```
知识星球A（starId=1）的数据：笔记1, 笔记2, 笔记3
知识星球B（starId=2）的数据：笔记4, 笔记5

用户进入星球A页面 → 该页面内的查询只返回笔记1,2,3（由租户作用域强制收窄）
用户离开星球页面   → 恢复全平台可读（首页 / 搜索 / 热榜需要跨星球）
```

> 关键差异：本项目**不是**「用户只能看自己星球」，而是「**在星球作用域内**只看本星球」，
> 因为平台本身是公开社区——未加入的用户也应能浏览星球详情。

### 后端怎么实现数据隔离？

#### 第一步：前端声明「星球作用域」

```js
// demo1/src/router/router.js —— 路由守卫按页面写入/清除作用域
const ROUTE_STAR_PARAM = {
  UserStarDetail: 'id',        // /user/starDetail/:id
  UserKnowledgeMap: 'starId'   // /user/knowledgeMap/:starId?
}

router.beforeEach((to, from, next) => {
  syncStarScopeFromRoute(to)   // 进入星球页 → setStarScope(id)；其他页 → clearStarScope()
  // ...原有的登录 / 权限判断
})

// demo1/src/utils/request.js —— 请求拦截器附加请求头
const starScope = getStarScope()
if (starScope) {
  config.headers['X-Star-Id'] = String(starScope)
}
```

#### 第二步：后端校验成员身份并写入上下文

```java
// interceptor/AuthInterceptor.java
public static final String STAR_SCOPE_HEADER = "X-Star-Id";

private void applyStarScope(HttpServletRequest request) {
    String raw = request.getHeader(STAR_SCOPE_HEADER);
    if (StringUtils.isBlank(raw)) return;               // 没带 → 不收窄
    LoginUserDTO loginUser = UserContext.get();
    if (loginUser == null) return;                      // 未登录 → 不接受作用域
    Long scopeStarId = Long.valueOf(raw.trim());
    // 关键：必须确实是该星球成员，否则忽略（只忽略不报错，避免残留上下文导致 500）
    QueryWrapper<StarMember> w = new QueryWrapper<>();
    w.eq("star_id", scopeStarId).eq("user_id", loginUser.getUserId());
    if (starMemberMapper.selectCount(w) > 0) {
        UserContext.setStarScope(scopeStarId);
    }
}
```

#### 第三步：租户插件按「作用域 + 白名单」注入条件

```java
// interceptor/TenantInterceptor.java（注册在 MybatisPlusConfig 的 TenantLineInnerInterceptor 上）
private static final List<String> TENANT_SCOPED_TABLES = Arrays.asList(
    "note",           // 笔记（星球内容的核心载体）
    "comment",        // 评论（star_id 由所属笔记派生）
    "note_column",    // 专栏（星球内合集）
    "knowledge_map"   // 知识图谱（星球内图谱）
);

@Override
public boolean ignoreTable(String tableName) {
    if (UserContext.get() == null) return true;      // 未登录 → 放行公开数据
    if (!UserContext.hasStarScope()) return true;    // 无星球上下文 → 不收窄
    return !TENANT_SCOPED_TABLES.contains(tableName);// 有作用域 → 只对白名单生效
}

@Override
public Expression getTenantId() {
    Long scope = UserContext.getStarScope();
    if (scope != null) return new LongValue(scope);
    LoginUserDTO u = UserContext.get();
    return new LongValue(u != null && u.getStarId() != null ? u.getStarId() : 0);
}

@Override
public String getTenantIdColumn() {
    return "star_id";
}
```

#### 实际效果

```java
// 处于星球 7 的页面，且我是星球 7 的成员时，你在代码里写：
noteMapper.selectList(new QueryWrapper<Note>().eq("status", "published"));

// MyBatis-Plus 自动变成：
// SELECT * FROM note WHERE status = 'published' AND star_id = 7
//                                                      ↑ 自动注入

// 而在首页 / 搜索页（没有 X-Star-Id），SQL 不会追加任何租户条件
```

#### 为什么白名单只有这 4 张表？

| 类别 | 表 | 为什么 |
|------|----|--------|
| **参与过滤** | `note`、`comment`、`note_column`、`knowledge_map` | 有 `star_id` 列，且「归属某星球」语义明确 |
| 无 `star_id` 列 | `resource`、`learning_path`、`message`、`notification`、`user`、`user_follow`… | 注入即报 `Unknown column 'star_id'` |
| 按自身维度隔离 | `like_record`（`user_id`）、`view_record`（`note_id`）、`note_collection`（`user_id`） | 隔离维度不是星球 |
| 全局共享 | `tag`、`note_tag`、`star`、`points_account`… | 跨星球通用 |

> ⚠️ **维护须知**：新增星球相关页面必须在 `router.js` 的 `ROUTE_STAR_PARAM` 登记；
> 否则该页面不带 `X-Star-Id`，隔离对它失效（**静默降级，不报错**）。

**关键理解：**

1. **作用域由请求头声明，而不是拿「用户的默认星球」当隐含条件。** `user` 表没有 `star_id`，用户与星球是多对多（`star_member`），不存在「用户唯一的星球」。若用「最早加入的星球」，用户在浏览 A 星球时插件会注入 B 星球的过滤条件 → 两条件互斥 → 一条内容都查不到。
2. **`starId`（JWT claim）与 `starScope`（请求级）是两个概念**：前者由 `UserService.resolveCurrentStarId()` 在登录时解析（优先 `user.current_star_id`，为空回退最早加入的星球），用于 `starFeed` 这类「我加入了哪些星球」的场景；后者只影响当前这一次请求是否收窄。
3. **隔离是三层叠加的**：① 显式 `eq("star_id", …)` 查询 → ② 成员/价格校验（付费星球脱敏）→ ③ 租户作用域注入。第三层是**纵深防御**：即便将来某处漏写显式条件，作用域内也不会越权读到其他星球的数据。


## 12. 实时通知（WebSocket）

### 后端配置

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");                          // 客户端订阅前缀
        config.setApplicationDestinationPrefixes("/app");             // 客户端发送前缀
        config.setUserDestinationPrefix("/user");                     // 用户目的地前缀（点对点）
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // 白名单方式，不是 "*"
                .setAllowedOriginPatterns(
                        "http://localhost:5173", "http://localhost:5174", "https://www.e-ren.icu")
                .withSockJS();  // 兼容不支持 WebSocket 的浏览器
    }
}
```

### 后端推送通知

```java
// 当有人点赞/评论时，推送通知
public void sendNotification(Long userId, Notification notification) {
    // STOMP 推送到 /user/{userId}/queue/notification
    messagingTemplate.convertAndSendToUser(
        userId.toString(),
        "/queue/notification",
        notification
    );
    // 实际推送到：/user/123/queue/notification
}
```

### 前端接收通知（store/notification.js）

```javascript
const connectWebSocket = () => {
  // 1. 创建 SockJS 连接
  const socket = new SockJS('/ws')

  // 2. 创建 STOMP 客户端
  const stompClient = Stomp.over(socket)

  // 3. 连接（带上 Token 认证）
  stompClient.connect(
    { Authorization: `Bearer ${userStore.token}` },
    () => {
      // 4. 订阅自己的通知队列（Spring 按 principal 解析成本人队列）
      stompClient.subscribe('/user/queue/notification', (message) => {
        const notification = JSON.parse(message.body)

        // 5. 更新未读数
        unreadCount.value++

        // 6. 显示弹窗
        notification.info({
          message: notification.title,
          description: notification.content
        })
      })
    }
  )
}
```

---

## 13. AI 聊天

### Spring AI 集成

```java
// config/CommonConfiguration.java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("你是一个编程助手，帮助用户解答编程问题。")
        .build();
}
```

### Controller（`controller/ChatController.java`）

```java
@PostMapping("/message")
public BaseResponse<ChatMessage> sendMessage(
        @Valid @RequestBody ChatRequest chatRequest,
        HttpServletRequest request) {
    Long userId = getCurrentUserId();
    return ResultUtils.success(chatService.sendMessage(chatRequest, userId));
}

@PostMapping(value = "/message/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@RateLimit(key = "chat_stream", windowSeconds = 60, maxRequests = 20)
public Flux<ServerSentEvent<String>> sendMessageStream(
        @Valid @RequestBody ChatRequest chatRequest,
        HttpServletRequest request) {
    Long userId = getCurrentUserId();
    return chatService.sendMessageStream(chatRequest, userId);
}
```

请求体 `ChatRequest` 字段：`sessionId`、`content`、`sessionName`、`streaming`（默认 `true`）。

### Service（`service/impl/ChatServiceImpl.java`）

```java
public ChatMessage sendMessage(ChatRequest request, Long userId) {
    // 1. 会话不存在则创建（会话信息持久化在 message_conversation 表）
    // 2. 从 Redis 读取该会话的历史上下文
    // 3. 调用 Spring AI ChatClient 生成回复
    // 4. 落库 ChatMessage，并回写会话的最后一条消息
    ...
}
```

> 历史上下文的 Redis key、保留条数与过期时间以 `ChatServiceImpl` 实现为准。

---

## 🎯 总结：理解项目的关键

### 数据流（贯穿所有功能）
```
用户操作 → 前端 Vue 组件 → api.js 调用 → request.js 拦截器加 Token
  → 后端 Controller → Service 业务逻辑 → Mapper 数据库操作
  → 返回 BaseResponse → 前端拦截器校验 code → 组件更新
```

### 权限流（贯穿所有接口）
```
前端：路由守卫 beforeEach → 检查 meta.requiresAuth
后端：AuthInterceptor → 读取 @LoginRequired 注解 → 解析 JWT Token
```

### 缓存流（提升性能）
```
热点数据 → Redis 缓存 → 多级缓存（Caffeine L1 → Redis L2 → DB）
计数类数据 → Redis INCR → 定时任务刷盘
状态类数据 → Redis Set（如点赞状态）
```
