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
@RateLimit(maxRequests = 10, timeWindowSeconds = 60)  // 每分钟最多10次，防暴力破解
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

    // 5. 生成 JWT Token
    long expireTime = rememberMe ? 7 * 24 * 3600 : 24 * 3600;  // 记住我=7天，否则=1天
    String token = JwtUtils.generateToken(
        user.getId(),
        user.getUserRole(),
        user.getStarId(),   // 多租户：用户所属星球ID
        expireTime
    );

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
// JwtUtils.generateToken 内部做了什么：
public static String generateToken(Long userId, Integer userRole, Long starId, long expireSeconds) {
    // JWT = Header.Payload.Signature
    // Header: { "alg": "HS256", "typ": "JWT" }
    // Payload: { "userId": 123, "userRole": 1, "starId": 456, "exp": 1718000000 }
    // Signature: HMAC-SHA256(header + payload, secretKey)

    return Jwts.builder()
        .claim("userId", userId)      // 把用户ID塞进Token
        .claim("userRole", userRole)  // 把角色塞进Token
        .claim("starId", starId)      // 把星球ID塞进Token
        .setExpiration(new Date(System.currentTimeMillis() + expireSeconds * 1000))
        .signWith(SignatureAlgorithm.HS256, SECRET_KEY)  // 用密钥签名
        .compact();
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
  → 生成 JWT Token（包含 userId, userRole, starId）
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
@Scheduled(fixedRate = 300000)  // 每 5 分钟执行一次
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
- Redis 方案：1000 次内存 INCR（极快） + 1 次批量 UPDATE（每 5 分钟）

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
@PostMapping("/note/{noteId}")
@LoginRequired
public BaseResponse<Boolean> likeNote(@PathVariable Long noteId) {
    Long userId = UserContext.getUserId();  // 从 ThreadLocal 获取当前用户ID
    boolean result = likeService.likeNote(userId, noteId);
    return ResultUtils.success(result);
}
```

```java
public boolean likeNote(Long userId, Long noteId) {
    // 1. Redis 检查是否已点赞（快速判断）
    String likeKey = "note:liked:" + noteId;
    Boolean isMember = redisTemplate.opsForSet().isMember(likeKey, userId);
    if (Boolean.TRUE.equals(isMember)) {
        return false;  // 已经点过赞了，直接返回
    }

    // 2. 数据库唯一索引兜底（防止并发问题）
    //    like_record 表有 (user_id, target_id, target_type) 的唯一索引
    try {
        LikeRecord record = new LikeRecord();
        record.setUserId(userId);
        record.setTargetId(noteId);
        record.setTargetType("note");
        likeRecordMapper.insert(record);
        // SQL: INSERT INTO like_record (user_id, target_id, target_type) VALUES (?, ?, 'note')
        // 如果已存在 → 唯一索引冲突 → 抛异常 → 被 catch 捕获
    } catch (DuplicateKeyException e) {
        return false;  // 并发插入，另一个请求已经插入了
    }

    // 3. Redis Set 记录点赞状态
    redisTemplate.opsForSet().add(likeKey, userId);

    // 4. 更新笔记点赞数
    noteMapper.incrementLikeCount(noteId);
    // SQL: UPDATE note SET like_count = like_count + 1 WHERE id = ?

    return true;
}
```

**防重复的双重保障：**
1. **Redis Set** — `SISMEMBER` 快速判断，大部分重复请求在这里被拦截
2. **数据库唯一索引** — 即使 Redis 判断失败（如 Redis 重启），数据库也能兜底

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

用户张三属于星球A → 他只能看到笔记1,2,3
用户李四属于星球B → 他只能看到笔记4,5
```

### 后端怎么实现数据隔离？

#### MyBatis-Plus 多租户插件（MybatisPlusConfig）

```java
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

    // 注册多租户插件
    interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
        @Override
        public Expression getTenantId() {
            // 从当前线程的 UserContext 获取 starId
            Long starId = UserContext.getStarId();
            return new LongValue(starId);
        }

        @Override
        public String getTenantIdColumn() {
            return "star_id";  // 所有表的租户列名
        }

        @Override
        public boolean ignoreTable(String tableName) {
            // 这些表是全局共享的，不需要加 star_id 条件
            return IGNORE_TABLES.contains(tableName);
        }
    }));

    return interceptor;
}
```

#### 实际效果

```java
// 你在代码里写：
noteMapper.selectById(123);

// MyBatis-Plus 自动变成：
// SELECT * FROM note WHERE id = 123 AND star_id = 当前用户的starId
//                                    ↑ 自动加的！你不用手动写
```

#### 哪些表需要隔离，哪些不需要？

```java
// TenantInterceptor.IGNORE_TABLES
public static final List<String> IGNORE_TABLES = Arrays.asList(
    "user",           // 用户是全局的（一个人可以加入多个星球）
    "like_record",    // 点赞记录是全局的
    "follow",         // 关注关系是全局的
    "notification",   // 通知是全局的
    "message"         // 私信是全局的
);
```

**关键理解：**
- **需要隔离的表**（note, comment, resource, star_member 等）：自动加 `star_id` 条件
- **不需要隔离的表**（user, like_record, follow 等）：全局共享

---

## 12. 实时通知（WebSocket）

### 后端配置

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/queue");  // 消息代理前缀
        config.setUserDestinationPrefix("/user");  // 用户目的地前缀
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
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
      // 4. 订阅自己的通知队列
      stompClient.subscribe(`/user/${userStore.id}/queue/notification`, (message) => {
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
// CommonConfiguration.java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("你是一个编程助手，帮助用户解答编程问题。")
        .build();
}
```

### Controller

```java
@PostMapping("/chat")
@LoginRequired
public BaseResponse<String> chat(@RequestBody AiChatRequest request) {
    String response = aiService.chat(request.getMessage(), request.getSessionId());
    return ResultUtils.success(response);
}
```

### Service

```java
public String chat(String message, String sessionId) {
    // 1. 从 Redis 获取历史对话
    String historyKey = "ai:chat:" + sessionId;
    List<String> history = redisTemplate.opsForList().range(historyKey, 0, -1);

    // 2. 构建带历史的 prompt
    String fullPrompt = String.join("\n", history) + "\n用户: " + message;

    // 3. 调用 AI 模型
    String response = chatClient.prompt()
        .user(fullPrompt)
        .call()
        .content();

    // 4. 保存对话到 Redis（保留最近 20 条）
    redisTemplate.opsForList().rightPush(historyKey, "用户: " + message);
    redisTemplate.opsForList().rightPush(historyKey, "AI: " + response);
    redisTemplate.opsForList().trim(historyKey, -20, -1);
    redisTemplate.expire(historyKey, 24, TimeUnit.HOURS);  // 24小时过期

    return response;
}
```

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
