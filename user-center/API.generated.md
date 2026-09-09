## 知识星球前端 RESTful API 接口文档（自动生成版）

> 说明：本接口文档依据当前前端代码实际调用的接口自动分析生成，可直接提供给后端实现。仅包含前端**真实发起 HTTP 请求**的接口，未在前端调用的功能不会在此文档中虚构。

---

## 一、通用约定

### 1.1 基础信息

- **前端 Axios 实例 `baseURL`**：`/api`
- **后端实际建议路径**：文档中的 URL 默认**不带** `/api` 前缀，推荐后端实现为如 `/user/login`，通过网关或 Vite 代理将 `/api` 前缀转发到后端。

### 1.2 统一返回结构（BaseResponse\<T\>）

除特别说明外，所有通过 `utils/request.js` 调用的接口，前端均按以下结构处理返回值：

```ts
interface BaseResponse<T> {
  code: number; // 0 表示成功，非 0 表示业务失败
  data?: T; // 业务数据
  message?: string; // 简单错误文案
  description?: string; // 详细错误说明（前端优先展示）
}
```

- **成功约定**：`code === 0` 视为成功，前端通常使用 `res.data`。
- **错误约定**：`code !== 0` 时，`request` 响应拦截器会 `Promise.reject` 一个业务错误对象：

```ts
{
  isBusinessError: true,
  code: number,
  message: string,      // description || message || '请求失败'
  description?: string,
}
```

### 1.3 常见业务错误码（前端已有处理）

- `40100`：未登录或登录已过期
  - 前端提示：`未登录或登录已过期，请先登录`
- `40101`：无权限
  - 前端提示：`无权限`
- 其他：前端统一按 `description || message || '请求失败' / '数据加载失败' / '更新失败，请重试！'` 等友好文案展示。

### 1.4 认证与会话

- Axios 实例配置：`withCredentials: true`
- 推荐后端使用 **Cookie + Session** 或等价方案，依赖浏览器自动携带 Cookie。

---

## 二、用户注册 / 登录 / 注销

### 2.1 用户注册

#### 接口

- **URL**：`POST /user/register`
- **说明**：新用户注册账号。

#### 请求体（JSON）

来自 `Register.vue` 中 `values.user`：

```json
{
  "username": "string", // 用户名（网名），必填
  "userAccount": "string", // 登录账号，长度 4-20，必填
  "gender": "string", // "1"=男, "2"=女, "3"=不愿透露（前端使用字符串）
  "userPassword": "string", // 登录密码，至少 8 位，必填
  "checkPassword": "string", // 确认密码，需与 userPassword 一致，必填
  "phone": "string", // 电话，可选
  "email": "string", // 邮箱，可选（前端有 email 格式校验）
  "avatarUrl": "string" // 头像 URL，可选
}
```

> 前端表单校验：
>
> - `userAccount`：必填，长度 4–20
> - `username`：必填
> - `userPassword`：必填，最少 8 位
> - `checkPassword`：必填，且需与 `userPassword` 一致

#### 响应

- **成功（code=0）**

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "userAccount": "test123",
    "username": "测试用户",
    "userRole": 0,
    "userStatus": 0,
    "avatarUrl": "https://...",
    "phone": "13800000000",
    "email": "test@example.com",
    "createTime": "2025-01-01T12:00:00Z"
  }
}
```

> 前端行为：注册成功后提示“注册成功，请登录”，并跳转 `/login`。

- **失败**

```json
{
  "code": 40000,
  "message": "参数错误",
  "description": "两次密码输入不一致"
}
```

---

### 2.2 用户登录

#### 接口

- **URL**：`POST /user/login`
- **说明**：账号密码登录，建立会话（依赖 Cookie / Session）。

#### 请求体（JSON）

```json
{
  "userAccount": "string", // 登录账号
  "userPassword": "string" // 登录密码
}
```

#### 响应

- **成功（code=0）**

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "userAccount": "test123",
    "userRole": 1, // 1=管理员, 0=普通用户（前端按此判断角色）
    "userStatus": 0,
    "username": "测试用户",
    "avatarUrl": "https://...",
    "phone": "13800000000",
    "email": "test@example.com",
    "createTime": "2025-01-01T12:00:00Z"
  }
}
```

> 前端行为：
>
> - 将 `data` 写入 Pinia 的 `userLogin` store
> - 同步到 `localStorage.userInfo`
> - 提示“登录成功”
> - 跳转 `/main`

- **失败（示例）**

```json
{
  "code": 40102,
  "message": "账号或密码错误",
  "description": "用户名或密码错误"
}
```

> 前端失败提示：`res.description || res.message || '用户名或密码错误'`

---

### 2.3 用户退出登录（注销会话）

#### 接口

- **URL**：`POST /user/userLogout`
- **说明**：退出登录，销毁服务器端会话 / token。

#### 请求体

- 无请求体。

#### 响应

- **成功**

```json
{
  "code": 0,
  "data": true
}
```

- **失败**

```json
{
  "code": 50000,
  "message": "服务器内部错误",
  "description": "退出登录失败"
}
```

> 前端行为：不论成功与否，都会清空本地用户状态（Pinia + localStorage），失败时提示后端退出失败但本地状态已清除。

---

### 2.4 获取当前登录用户信息

#### 接口

- **URL**：`GET /user/current`
- **说明**：返回当前会话关联的用户信息，用于前端初始化用户状态和“个人资料”页面。

#### 请求参数

- 无。

#### 响应

- **成功**

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "userAccount": "test123",
    "userRole": 1,
    "userStatus": 0,
    "username": "测试用户",
    "avatarUrl": "https://...",
    "phone": "13800000000",
    "email": "test@example.com",
    "gender": 1, // 1=男, 0=女, 其他/空=未知
    "address": "string", // 住址，可选
    "createTime": "2025-01-01T12:00:00Z"
  }
}
```

- **未登录**

```json
{
  "code": 40100,
  "message": "未登录",
  "description": "未登录或登录已过期"
}
```

> 前端在 `Profile.vue` 中根据 `gender` 数值渲染为“男 / 女 / 未知”，并支持单字段更新。

---

## 三、用户信息管理与后台用户管理

### 3.1 更新用户信息（当前用户 / 管理员）

> 前端在：
>
> - 「用户管理」页面 `Home/Account.vue` 中编辑任意用户信息（管理员）
> - 「个人资料」页面 `Home/Profile.vue` 中按字段更新当前用户信息

#### 接口

- **URL**：`PUT /user/update`
- **说明**：更新用户信息。前端会根据具体页面构造 `updateData`。

#### 请求体（JSON）

通用字段（两处页面都会使用的字段）：

```json
{
  "id": 123, // 必填，用户 ID
  "userAccount": "string", // 账号，可选（仅管理界面会编辑）
  "username": "string", // 用户名，可选
  "gender": 1, // 性别：1=男, 0=女, 其他（如 2/null）=未知
  "phone": "string", // 手机号，可选
  "email": "string", // 邮箱，可选
  "avatarUrl": "string", // 头像 URL，可选（由上传接口返回）
  "userRole": 1, // 角色：1=管理员, 0=普通用户（仅管理端）
  "address": "string" // 地址，仅个人资料页会提交
}
```

前端字段转换约定：

- 在 `Home/Account.vue` 中：

  - 展示时：
    - `gender: 1 → "男"; 0 → "女"; else → "未知"`
    - `userRole: 1 → "管理员"; 0 → "普通用户"`
  - 保存前：
    - `"男" → 1; "女" → 0; "未知" → null`
    - `"管理员" → 1; "普通用户" → 0`

- 在 `Home/Profile.vue` 中：
  - 单字段更新，构造：

```ts
{
  id: userInfo.id,
  [field]: field === 'gender'
    ? (gender === '男' ? 1 : gender === '女' ? 0 : 2)
    : userInfo[field]
}
```

> 建议后端对 `gender` 字段支持 `0/1/2/null` 等多值，统一视为 0/1/未知 即可。

#### 响应

- **成功**

```json
{
  "code": 0,
  "data": true
}
```

> 成功后前端：
>
> - 在用户管理表格中会将编辑中的行合并回数据源，并清理编辑状态；
> - 在个人资料页会关闭对应字段的编辑状态并更新本地缓存值。

- **失败**

```json
{
  "code": 40301,
  "message": "无权限",
  "description": "仅管理员可修改其他用户信息"
}
```

> 前端错误提示：`error.description || error.message || '更新失败，请重试！'`，在用户管理页会回滚表格行数据到编辑前的值。

---

### 3.2 搜索用户列表（管理员）

> 用于：
>
> - `AdminUser.vue`：后台用户列表管理（带按账号搜索）
> - `Home/Account.vue`：加载全部用户到可编辑表格

#### 接口

- **URL**：`GET /user/search`
- **说明**：根据条件搜索用户列表，仅管理员可调用。

#### 查询参数

- `userAccount`：`string`，可选  
  用途：按账号模糊查询（在 `AdminUser.vue` 中使用）。

> 其他查询条件（如 `username`、`phone`、分页参数）前端暂未使用，可由后端自行扩展。

示例：

- `GET /user/search`
- `GET /user/search?userAccount=test`

#### 响应

前端视为用户列表，并兼容两种形式：

```json
{
  "code": 0,
  "data": [
    {
      "id": 123,
      "userAccount": "test123",
      "username": "测试用户",
      "gender": 1, // 1=男, 0=女, 其他/空=未知
      "phone": "13800000000",
      "email": "test@example.com",
      "avatarUrl": "https://...",
      "userRole": 1, // 1=管理员, 0=普通用户
      "userStatus": 0,
      "createTime": "2025-01-01T12:00:00Z"
    }
  ]
}
```

或退化形式（不推荐）：

```json
[
  {
    "id": 123,
    "userAccount": "test123",
    "username": "测试用户"
  }
]
```

> 前端兼容逻辑：`Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : [])`。

#### 错误

被前端特殊处理的业务错误码：

- `40100`：未登录或登录已过期
- `40101`：无权限

示例：

```json
{
  "code": 40101,
  "message": "FORBIDDEN",
  "description": "无权限"
}
```

---

### 3.3 删除用户（管理员）

> 用于后台用户管理 `AdminUser.vue` 中删除用户记录。

#### 接口

- **URL**：`POST /user/delete`
- **说明**：根据用户 ID 删除用户，仅管理员可调用。

#### 请求体（JSON）

前端调用：`deleteUser(record.id)`，在 `api.js` 中实现为：

```ts
request.post("/user/delete", userId, {
  headers: { "Content-Type": "application/json" },
});
```

> 建议后端兼容以下两种形式（推荐 JSON 对象）：

- **推荐：对象形式**

```json
{
  "id": 123
}
```

- **或：纯数值（兼容）**

```json
123
```

#### 响应

前端判断逻辑：

```ts
const ok = (res.data ?? res) === true;
```

因此建议实现为标准 `BaseResponse<boolean>`，但前端也兼容纯 `boolean`。

- **推荐响应（标准）**

```json
{
  "code": 0,
  "data": true
}
```

- **简化版本（不推荐）**

```json
true
```

> 前端行为：
>
> - `ok === true` 时提示“删除成功”并重新加载列表
> - 否则提示“删除失败”

---

### 3.4 用户头像 / 通用文件上传（业务侧）

> 此节描述通过 **Axios 封装接口 `uploadFile`** 调用的上传逻辑（如「用户管理」里编辑头像、「权限演示」页上传示例）。
> 通过 Ant Design `Upload` 组件直接访问的上传接口见后文「四、文件与资源上传」。

#### 接口

- **URL**：`POST /user/upload`
- **说明**：上传用户头像或通用文件，返回可访问的文件 URL。

#### 请求体（`multipart/form-data`）

前端封装函数：

```ts
export const uploadFile = (formData) => request.post("/user/upload", formData);
```

常见字段：

- 字段名：`file`（必填，`image/*` 或其他）
- 附加字段（示例）：
  - 在 `Home/Permissions.vue` 中还会附带 `name`, `age` 等自定义字段：

```ts
formData.append("name", name.value);
formData.append("age", age.value);
formData.append("file", file.value);
```

> 后端可按需接收除 `file` 外的其他业务字段。

头像上传场景（`Home/Account.vue`）中，前端会在保存行数据前先调用该接口上传头像文件，拿到 URL 后再合并到 `/user/update` 请求体。

#### 响应

需兼容以下两种形式之一：

1. **字符串 URL**

```json
{
  "code": 0,
  "data": "https://static.example.com/avatar/xxx.png"
}
```

2. **包含 URL 字段的对象**

```json
{
  "code": 0,
  "data": {
    "url": "https://static.example.com/avatar/xxx.png"
  }
}
```

> 前端解析逻辑：
>
> ```ts
> const uploadData = uploadResp?.data;
> const uploadedUrl =
>   typeof uploadData === "string" ? uploadData : uploadData?.url;
> ```

---

## 四、账号注销（彻底销户）

该接口未通过 `utils/request.js`，而是直接使用 `axios.post`，返回结构与 `BaseResponse` 不同。

### 4.1 账号注销接口

#### 接口

- **前端实际请求 URL**：`POST /api/user/closeAccount`
- **推荐后端真实路径**：`POST /user/closeAccount`（由 `/api` 代理转发）

#### 说明

- 功能：当前登录用户主动申请注销账号，需输入密码确认身份。
- 注销成功后：
  - 前端调用 `userStore.logout()` 清空本地用户登录状态
  - 跳转 `/login`

#### 请求体（JSON）

```json
{
  "userPassword": "string" // 当前登录用户密码，用于二次确认
}
```

#### 响应（非 BaseResponse 结构）

前端直接访问 `response.data.success` 与 `response.data.message`：

- **成功**

```json
{
  "success": true,
  "message": "账号注销成功"
}
```

- **失败**

```json
{
  "success": false,
  "message": "密码错误"
}
```

> 网络错误或异常场景，前端提示：“网络错误，注销失败”。

---

## 五、文件与资源上传（Ant Design Upload 组件直连）

本节接口是通过 Ant Design Vue `a-upload` 组件**直接访问后端 URL**，不经过 `utils/request.js` 拦截器，但整体仍推荐使用 `BaseResponse` 结构。

### 5.1 用户头像上传（个人资料页）

#### 接口

- **前端实际请求 URL**：`POST /api/user/upload`
- **推荐后端真实路径**：`POST /user/upload`
- **调用位置**：`Home/Profile.vue` 中的头像上传组件。

#### 请求体（`multipart/form-data`）

- 字段名：`file`
- 类型：`image/*`

> 前端通过 `a-upload` 的 `action="/api/user/upload"` 直接上传，`withCredentials: true`。

#### 响应

推荐沿用「3.4 用户头像 / 通用文件上传」中的 `BaseResponse<string | { url: string }>` 格式。

前端处理逻辑（节选）：

```ts
if (info.file.status === "done") {
  const resp = info.file.response;
  if (resp && resp.code === 0) {
    const url = typeof resp.data === "string" ? resp.data : resp.data?.url;
    if (url) userInfo.value.avatarUrl = url;
    message.success("头像上传成功");
  } else {
    message.error(resp?.description || resp?.message || "头像上传失败");
  }
}
```

---

### 5.2 内容封面图片上传（内容发布）

#### 接口

- **前端实际请求 URL**：`POST /api/user/upload`
- **推荐后端真实路径**：`POST /user/upload`
- **调用位置**：`Content/ContentPublish.vue` 中的封面图片上传。

#### 请求体（`multipart/form-data`）

- 字段名：`file`
- 类型：`image/*`

#### 响应

同样推荐使用：

```json
{
  "code": 0,
  "data": "https://static.example.com/cover/xxx.png"
}
```

或：

```json
{
  "code": 0,
  "data": {
    "url": "https://static.example.com/cover/xxx.png"
  }
}
```

前端使用方式与 5.1 相同，只是将 URL 写入 `formState.coverImage` 字段。

---

### 5.3 学习资源文件上传

#### 接口

- **前端实际请求 URL**：`POST /api/resource/upload`
- **推荐后端真实路径**：`POST /resource/upload`
- **调用位置**：`Resources/ResourceAdd.vue` 中的“资源文件”上传。

#### 请求体（`multipart/form-data`）

前端使用 Ant Upload：

- 字段名：`file`
- 大小限制：**100MB 以内**（前端已校验）

伪 HTTP 示例：

```http
POST /resource/upload
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="file"; filename="resource.zip"
Content-Type: application/zip

<binary data>
--boundary--
```

#### 响应

前端期望 `response.data.data.url` 存在，并用于填充资源下载地址：

```json
{
  "code": 0,
  "data": {
    "url": "https://static.example.com/resource/xxx.zip"
  }
}
```

前端处理逻辑（节选）：

```ts
if (info.file.status === "done") {
  antMessage.success("文件上传成功");
  formState.downloadUrl = info.file.response?.data?.url || "";
} else if (info.file.status === "error") {
  antMessage.error("文件上传失败");
}
```

> 建议：除 `url` 外，可在 `data` 中补充文件名、大小、类型等元信息，前端目前未使用但可扩展。

---

## 六、接口一览表

| 模块 | 接口路径             | 方法 | 描述                            | 认证/权限要求              | 返回结构                                |
| ---- | -------------------- | ---- | ------------------------------- | -------------------------- | --------------------------------------- |
| 用户 | `/user/register`     | POST | 用户注册                        | 否                         | `BaseResponse<User>`                    |
| 用户 | `/user/login`        | POST | 用户登录                        | 否                         | `BaseResponse<User>`                    |
| 用户 | `/user/userLogout`   | POST | 退出登录（销毁会话）            | 已登录用户                 | `BaseResponse<boolean>`                 |
| 用户 | `/user/current`      | GET  | 获取当前登录用户信息            | 已登录用户                 | `BaseResponse<User>`                    |
| 用户 | `/user/update`       | PUT  | 更新用户信息                    | 本人 / 管理员              | `BaseResponse<boolean>`                 |
| 用户 | `/user/search`       | GET  | 搜索用户列表                    | 管理员                     | `BaseResponse<User[]>` \| `User[]`      |
| 用户 | `/user/delete`       | POST | 删除用户（后台管理）            | 管理员                     | `BaseResponse<boolean>` \| `boolean`    |
| 用户 | `/user/upload`       | POST | 上传头像/通用文件，返回文件 URL | 已登录用户（建议限制）     | `BaseResponse<string\|{url:string}>`    |
| 用户 | `/user/closeAccount` | POST | 账号注销（彻底销户）            | 已登录用户 + 密码校验      | `{ success: boolean; message: string }` |
| 资源 | `/resource/upload`   | POST | 上传学习资源文件                | 已登录用户（建议仅管理员） | `BaseResponse<{url:string,...}>`        |

> `User` 结构（综合前端字段的推荐形态）：

```json
{
  "id": 123,
  "userAccount": "test123",
  "username": "测试用户",
  "userRole": 1,
  "userStatus": 0,
  "avatarUrl": "https://...",
  "phone": "13800000000",
  "email": "test@example.com",
  "gender": 1,
  "address": "江软",
  "createTime": "2025-01-01T12:00:00Z"
}
```

---

## 七、笔记/内容管理接口

### 7.1 获取笔记/内容列表

#### 接口

- **URL**：`GET /note/list`
- **说明**：获取笔记/内容列表，支持筛选和排序。

#### 查询参数

- `keyword`：`string`，可选，关键词搜索（标题、摘要）
- `category`：`string`，可选，分类筛选（`frontend`、`backend`、`algorithm`、`database`、`other`）
- `contentType`：`string`，可选，内容类型（`article`、`question`、`note`）
- `status`：`string`，可选，状态筛选（`published`、`draft`）
- `sortType`：`string`，可选，排序方式（`hot` 热度排序、`latest` 最新排序），默认 `hot`

#### 响应

```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "title": "Vue3 组合式 API 深度解析",
      "category": "frontend",
      "contentType": "article",
      "summary": "深入探讨 Vue3 Composition API...",
      "author": "知识星球管理员",
      "authorId": 1,
      "viewCount": 1250,
      "commentCount": 45,
      "likeCount": 89,
      "status": "published",
      "publishTime": "2025-10-01 10:20:00",
      "publishTimestamp": 1727761200000,
      "coverImage": "https://...",
      "tags": ["Vue3", "前端"]
    }
  ]
}
```

---

### 7.2 获取笔记/内容详情

#### 接口

- **URL**：`GET /note/{id}`
- **说明**：根据 ID 获取笔记/内容详情。

#### 路径参数

- `id`：`number`，笔记/内容 ID

#### 响应

```json
{
  "code": 0,
  "data": {
    "id": 1,
    "title": "Vue3 组合式 API 深度解析",
    "category": "frontend",
    "contentType": "article",
    "content": "# 什么是 Composition API？\n\n...",
    "author": "知识星球管理员",
    "authorId": 1,
    "viewCount": 1250,
    "commentCount": 45,
    "likeCount": 89,
    "status": "published",
    "publishTime": "2025-10-01 10:20:00",
    "coverImage": "https://...",
    "tags": ["Vue3", "前端"],
    "starId": 1
  }
}
```

> 前端行为：获取详情后会自动调用 `/note/view/{id}` 增加浏览量。

---

### 7.3 创建笔记/内容

#### 接口

- **URL**：`POST /note/add`
- **说明**：创建新的笔记/内容。

#### 请求体（JSON）

```json
{
  "title": "string", // 必填，标题，最大100字符
  "category": "string", // 必填，分类
  "contentType": "string", // 可选，内容类型（article/question/note）
  "content": "string", // 必填，内容（支持 Markdown）
  "summary": "string", // 可选，摘要
  "coverImage": "string", // 可选，封面图片 URL
  "tags": ["string"], // 可选，标签数组
  "starId": 1 // 可选，所属星球 ID
}
```

#### 响应

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "title": "...",
    "createTime": "2025-10-01T12:00:00Z"
  }
}
```

---

### 7.4 更新笔记/内容

#### 接口

- **URL**：`PUT /note/update/{id}`
- **说明**：更新已存在的笔记/内容。

#### 路径参数

- `id`：`number`，笔记/内容 ID

#### 请求体（JSON）

同创建接口，所有字段可选。

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 7.5 删除笔记/内容

#### 接口

- **URL**：`POST /note/delete`
- **说明**：删除笔记/内容（软删除或硬删除）。

#### 请求体（JSON）

```json
{
  "id": 123
}
```

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 7.6 搜索内容（管理员）

#### 接口

- **URL**：`GET /content/search`
- **说明**：管理员搜索所有内容，支持更复杂的筛选条件。

#### 查询参数

- `keyword`：`string`，可选，关键词
- `contentType`：`string`，可选，内容类型
- `status`：`string`，可选，状态（`published`、`draft`、`deleted`）
- `authorId`：`number`，可选，作者 ID

#### 响应

同 `/note/list` 接口。

---

### 7.7 点赞笔记/内容

#### 接口

- **URL**：`POST /note/like/{id}`
- **说明**：点赞笔记/内容。

#### 路径参数

- `id`：`number`，笔记/内容 ID

#### 响应

```json
{
  "code": 0,
  "data": {
    "likeCount": 90
  }
}
```

---

### 7.8 增加浏览量

#### 接口

- **URL**：`POST /note/view/{id}`
- **说明**：增加笔记/内容的浏览量（静默调用，无需用户交互）。

#### 路径参数

- `id`：`number`，笔记/内容 ID

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

## 八、评论管理接口

### 8.1 获取评论列表

#### 接口

- **URL**：`GET /comment/list`
- **说明**：获取评论列表，支持筛选。

#### 查询参数

- `noteId`：`number`，可选，笔记/内容 ID
- `keyword`：`string`，可选，关键词搜索（评论内容、用户名）
- `status`：`string`，可选，状态筛选（`approved`、`pending`、`hidden`）
- `page`：`number`，可选，页码
- `pageSize`：`number`，可选，每页数量

#### 响应

```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "noteId": 1,
      "noteTitle": "Vue3 组合式 API 深度解析",
      "userId": 2,
      "username": "用户A",
      "avatar": "https://...",
      "content": "非常详细的讲解，收获很大！",
      "status": "approved",
      "createTime": "2025-10-01 11:30:00",
      "likeCount": 5,
      "replies": [
        {
          "id": 11,
          "userId": 1,
          "username": "知识星球管理员",
          "content": "谢谢支持！",
          "createTime": "2025-10-01 11:35:00",
          "likeCount": 2
        }
      ]
    }
  ]
}
```

---

### 8.2 添加评论

#### 接口

- **URL**：`POST /comment/add`
- **说明**：为笔记/内容添加评论。

#### 请求体（JSON）

```json
{
  "noteId": 1, // 必填，笔记/内容 ID
  "content": "string" // 必填，评论内容，最大500字符
}
```

#### 响应

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "content": "...",
    "createTime": "2025-10-01T12:00:00Z"
  }
}
```

---

### 8.3 回复评论

#### 接口

- **URL**：`POST /comment/reply/{commentId}`
- **说明**：回复指定评论。

#### 路径参数

- `commentId`：`number`，被回复的评论 ID

#### 请求体（JSON）

```json
{
  "noteId": 1, // 必填，笔记/内容 ID
  "content": "string" // 必填，回复内容
}
```

#### 响应

同添加评论接口。

---

### 8.4 删除评论

#### 接口

- **URL**：`POST /comment/delete`
- **说明**：删除评论（管理员或评论作者）。

#### 请求体（JSON）

```json
{
  "id": 123
}
```

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 8.5 审核评论（管理员）

#### 接口

- **URL**：`POST /comment/approve`
- **说明**：审核评论，设置状态（仅管理员）。

#### 请求体（JSON）

```json
{
  "id": 123,
  "status": "approved" // approved=通过, hidden=屏蔽
}
```

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 8.6 点赞评论

#### 接口

- **URL**：`POST /comment/like/{id}`
- **说明**：点赞评论。

#### 路径参数

- `id`：`number`，评论 ID

#### 响应

```json
{
  "code": 0,
  "data": {
    "likeCount": 6
  }
}
```

---

## 九、资源管理接口

### 9.1 获取资源列表

#### 接口

- **URL**：`GET /resource/list`
- **说明**：获取学习资源列表。

#### 查询参数

- `keyword`：`string`，可选，关键词搜索
- `tag`：`string`，可选，资源类型（`pdf`、`video`、`code`、`tool`）
- `category`：`string`，可选，分类
- `status`：`string`，可选，状态（`enabled`、`disabled`）

#### 响应

```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "name": "Vue3 官方文档中文版",
      "tag": "pdf",
      "description": "Vue3 官方文档完整中文翻译版本",
      "downloadCount": 1230,
      "fileSize": "2.5 MB",
      "downloadUrl": "https://...",
      "coverImage": "https://...",
      "status": "enabled",
      "updatedAt": "2025-10-01 10:20:00"
    }
  ]
}
```

---

### 9.2 添加资源

#### 接口

- **URL**：`POST /resource/add`
- **说明**：添加新的学习资源。

#### 请求体（JSON）

```json
{
  "title": "string", // 必填，资源标题
  "description": "string", // 必填，资源描述
  "resourceType": "string", // 必填，资源类型（document/video/code/other）
  "category": "string", // 必填，分类
  "tags": ["string"], // 可选，标签数组
  "downloadUrl": "string", // 可选，下载链接
  "coverImage": "string", // 可选，封面图片 URL
  "isPublic": true // 可选，是否公开，默认 true
}
```

#### 响应

```json
{
  "code": 0,
  "data": {
    "id": 123,
    "title": "...",
    "createTime": "2025-10-01T12:00:00Z"
  }
}
```

---

### 9.3 更新资源

#### 接口

- **URL**：`PUT /resource/update/{id}`
- **说明**：更新资源信息。

#### 路径参数

- `id`：`number`，资源 ID

#### 请求体（JSON）

同添加接口，所有字段可选。

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 9.4 删除资源

#### 接口

- **URL**：`POST /resource/delete`
- **说明**：删除资源（仅管理员）。

#### 请求体（JSON）

```json
{
  "id": 123
}
```

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 9.5 上传资源文件

#### 接口

- **URL**：`POST /resource/upload`
- **说明**：上传资源文件，返回文件 URL。

#### 请求体（`multipart/form-data`）

- 字段名：`file`
- 类型：任意文件
- 大小限制：**100MB 以内**（前端已校验）

#### 响应

```json
{
  "code": 0,
  "data": {
    "url": "https://static.example.com/resource/xxx.pdf",
    "fileSize": "2.5 MB",
    "fileName": "vue3-docs.pdf"
  }
}
```

---

### 9.6 下载资源

#### 接口

- **URL**：`GET /resource/download/{id}`
- **说明**：下载资源文件。

#### 路径参数

- `id`：`number`，资源 ID

#### 响应

- **Content-Type**：`application/octet-stream` 或对应文件类型
- **Body**：文件二进制流

> 前端行为：下载后会自动增加资源的 `downloadCount`。

---

### 9.7 切换资源状态

#### 接口

- **URL**：`POST /resource/toggleStatus`
- **说明**：启用/停用资源（仅管理员）。

#### 请求体（JSON）

```json
{
  "id": 123,
  "status": "enabled" // enabled=启用, disabled=停用
}
```

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

## 十、知识图谱接口

### 10.1 获取知识地图

#### 接口

- **URL**：`GET /knowledge/map`
- **说明**：获取知识地图数据。

#### 查询参数

- `starId`：`number`，可选，星球 ID

#### 响应

```json
{
  "code": 0,
  "data": {
    "starId": 1,
    "nodes": [
      {
        "id": "vue3",
        "label": "Vue3",
        "type": "core",
        "x": 400,
        "y": 200
      }
    ],
    "edges": [
      {
        "source": "vue3",
        "target": "composition"
      }
    ]
  }
}
```

---

### 10.2 保存知识地图

#### 接口

- **URL**：`POST /knowledge/map/save`
- **说明**：保存知识地图数据（仅管理员）。

#### 请求体（JSON）

```json
{
  "starId": 1,
  "nodes": [
    {
      "id": "vue3",
      "label": "Vue3",
      "type": "core",
      "x": 400,
      "y": 200
    }
  ],
  "edges": [
    {
      "source": "vue3",
      "target": "composition"
    }
  ]
}
```

#### 响应

```json
{
  "code": 0,
  "data": true
}
```

---

### 10.3 获取知识地图节点内容

#### 接口

- **URL**：`GET /knowledge/node/{nodeId}/content`
- **说明**：获取指定节点相关的内容列表。

#### 路径参数

- `nodeId`：`string`，节点 ID

#### 响应

```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "title": "Vue3 相关文章",
      "contentType": "article",
      "viewCount": 1250,
      "likeCount": 89
    }
  ]
}
```

---

## 十一、统计数据接口

### 11.1 获取数据统计概览

#### 接口

- **URL**：`GET /stats/data`
- **说明**：获取数据统计概览（仅管理员）。

#### 查询参数

- `startDate`：`string`，可选，开始日期（YYYY-MM-DD）
- `endDate`：`string`，可选，结束日期（YYYY-MM-DD）

#### 响应

```json
{
  "code": 0,
  "data": {
    "overview": [
      { "title": "总笔记数", "value": 156, "unit": "篇", "trend": "+12.5%" },
      { "title": "总浏览量", "value": 45680, "unit": "次", "trend": "+18.2%" },
      { "title": "总评论数", "value": 2340, "unit": "条", "trend": "+15.3%" },
      { "title": "资源下载量", "value": 8900, "unit": "次", "trend": "+8.7%" }
    ],
    "details": [
      {
        "id": 1,
        "date": "2025-10-01",
        "notes": 5,
        "views": 320,
        "comments": 45,
        "downloads": 120
      }
    ]
  }
}
```

---

### 11.2 获取用户统计数据

#### 接口

- **URL**：`GET /stats/user`
- **说明**：获取用户统计数据（仅管理员）。

#### 查询参数

- `startDate`：`string`，可选，开始日期
- `endDate`：`string`，可选，结束日期
- `source`：`string`，可选，来源渠道（`organic`、`ad`、`partner`）

#### 响应

```json
{
  "code": 0,
  "data": {
    "overview": [
      { "title": "累计用户", "value": 86520, "trend": "+5.3%" },
      { "title": "本月新增", "value": 2350, "trend": "+12.1%" },
      { "title": "活跃用户", "value": 18960, "trend": "+6.8%" },
      { "title": "付费转化率", "value": "12.5%", "trend": "+1.2%" }
    ],
    "details": [
      {
        "id": 1,
        "date": "2025-10-01",
        "source": "自然注册",
        "newUsers": 180,
        "active": 980
      }
    ]
  }
}
```

---

### 11.3 获取销售统计数据

#### 接口

- **URL**：`GET /stats/sales`
- **说明**：获取销售统计数据（仅管理员）。

#### 查询参数

- `startDate`：`string`，可选，开始日期
- `endDate`：`string`，可选，结束日期
- `channel`：`string`，可选，销售渠道（`web`、`mini`、`offline`）

#### 响应

```json
{
  "code": 0,
  "data": {
    "overview": [
      { "title": "本月 GMV", "value": 186532, "unit": "元", "trend": "+12.5%" },
      { "title": "支付订单数", "value": 3210, "unit": "单", "trend": "+8.1%" },
      { "title": "退款金额", "value": 8650, "unit": "元", "trend": "-2.3%" },
      { "title": "客单价", "value": 580, "unit": "元", "trend": "+3.4%" }
    ],
    "details": [
      {
        "id": 1,
        "date": "2025-10-01",
        "channel": "官网商城",
        "orders": 320,
        "gmv": 168530,
        "refund": 3200
      }
    ]
  }
}
```

---

## 十二、接口一览表（完整版）

| 模块      | 接口路径                           | 方法 | 描述                            | 认证/权限要求              | 返回结构                                |
| --------- | ---------------------------------- | ---- | ------------------------------- | -------------------------- | --------------------------------------- |
| 用户      | `/user/register`                   | POST | 用户注册                        | 否                         | `BaseResponse<User>`                    |
| 用户      | `/user/login`                      | POST | 用户登录                        | 否                         | `BaseResponse<User>`                    |
| 用户      | `/user/userLogout`                 | POST | 退出登录（销毁会话）            | 已登录用户                 | `BaseResponse<boolean>`                 |
| 用户      | `/user/current`                    | GET  | 获取当前登录用户信息            | 已登录用户                 | `BaseResponse<User>`                    |
| 用户      | `/user/update`                     | PUT  | 更新用户信息                    | 本人 / 管理员              | `BaseResponse<boolean>`                 |
| 用户      | `/user/search`                     | GET  | 搜索用户列表                    | 管理员                     | `BaseResponse<User[]>`                  |
| 用户      | `/user/delete`                     | POST | 删除用户（后台管理）            | 管理员                     | `BaseResponse<boolean>`                 |
| 用户      | `/user/upload`                     | POST | 上传头像/通用文件，返回文件 URL | 已登录用户                 | `BaseResponse<string\|{url}>`           |
| 用户      | `/user/closeAccount`               | POST | 账号注销（彻底销户）            | 已登录用户 + 密码校验      | `{ success: boolean; message: string }` |
| 笔记/内容 | `/note/list`                       | GET  | 获取笔记/内容列表               | 已登录用户                 | `BaseResponse<Note[]>`                  |
| 笔记/内容 | `/note/{id}`                       | GET  | 获取笔记/内容详情               | 已登录用户                 | `BaseResponse<Note>`                    |
| 笔记/内容 | `/note/add`                        | POST | 创建笔记/内容                   | 已登录用户                 | `BaseResponse<Note>`                    |
| 笔记/内容 | `/note/update/{id}`                | PUT  | 更新笔记/内容                   | 作者 / 管理员              | `BaseResponse<boolean>`                 |
| 笔记/内容 | `/note/delete`                     | POST | 删除笔记/内容                   | 作者 / 管理员              | `BaseResponse<boolean>`                 |
| 笔记/内容 | `/note/like/{id}`                  | POST | 点赞笔记/内容                   | 已登录用户                 | `BaseResponse<{likeCount}>`             |
| 笔记/内容 | `/note/view/{id}`                  | POST | 增加浏览量                      | 已登录用户                 | `BaseResponse<boolean>`                 |
| 内容管理  | `/content/search`                  | GET  | 搜索内容（管理员）              | 管理员                     | `BaseResponse<Note[]>`                  |
| 评论      | `/comment/list`                    | GET  | 获取评论列表                    | 已登录用户                 | `BaseResponse<Comment[]>`               |
| 评论      | `/comment/add`                     | POST | 添加评论                        | 已登录用户                 | `BaseResponse<Comment>`                 |
| 评论      | `/comment/reply/{commentId}`       | POST | 回复评论                        | 已登录用户                 | `BaseResponse<Comment>`                 |
| 评论      | `/comment/delete`                  | POST | 删除评论                        | 作者 / 管理员              | `BaseResponse<boolean>`                 |
| 评论      | `/comment/approve`                 | POST | 审核评论（管理员）              | 管理员                     | `BaseResponse<boolean>`                 |
| 评论      | `/comment/like/{id}`               | POST | 点赞评论                        | 已登录用户                 | `BaseResponse<{likeCount}>`             |
| 资源      | `/resource/list`                   | GET  | 获取资源列表                    | 已登录用户                 | `BaseResponse<Resource[]>`              |
| 资源      | `/resource/add`                    | POST | 添加资源                        | 已登录用户（建议仅管理员） | `BaseResponse<Resource>`                |
| 资源      | `/resource/update/{id}`            | PUT  | 更新资源                        | 管理员                     | `BaseResponse<boolean>`                 |
| 资源      | `/resource/delete`                 | POST | 删除资源                        | 管理员                     | `BaseResponse<boolean>`                 |
| 资源      | `/resource/upload`                 | POST | 上传资源文件                    | 已登录用户（建议仅管理员） | `BaseResponse<{url,fileSize,fileName}>` |
| 资源      | `/resource/download/{id}`          | GET  | 下载资源                        | 已登录用户                 | `Blob`                                  |
| 资源      | `/resource/toggleStatus`           | POST | 切换资源状态                    | 管理员                     | `BaseResponse<boolean>`                 |
| 知识图谱  | `/knowledge/map`                   | GET  | 获取知识地图                    | 已登录用户                 | `BaseResponse<KnowledgeMap>`            |
| 知识图谱  | `/knowledge/map/save`              | POST | 保存知识地图                    | 管理员                     | `BaseResponse<boolean>`                 |
| 知识图谱  | `/knowledge/node/{nodeId}/content` | GET  | 获取节点内容                    | 已登录用户                 | `BaseResponse<Note[]>`                  |
| 统计数据  | `/stats/data`                      | GET  | 获取数据统计概览                | 管理员                     | `BaseResponse<DataStats>`               |
| 统计数据  | `/stats/user`                      | GET  | 获取用户统计数据                | 管理员                     | `BaseResponse<UserStats>`               |
| 统计数据  | `/stats/sales`                     | GET  | 获取销售统计数据                | 管理员                     | `BaseResponse<SalesStats>`              |

---

## 十三、数据模型定义

### Note（笔记/内容）

```typescript
interface Note {
  id: number;
  title: string;
  category: string; // frontend/backend/algorithm/database/other
  contentType: string; // article/question/note
  content: string; // Markdown 格式
  summary?: string;
  author: string;
  authorId: number;
  viewCount: number;
  commentCount: number;
  likeCount: number;
  status: string; // published/draft/deleted
  publishTime: string;
  publishTimestamp?: number;
  coverImage?: string;
  tags?: string[];
  starId?: number;
  createTime: string;
  updateTime: string;
}
```

### Comment（评论）

```typescript
interface Comment {
  id: number;
  noteId: number;
  noteTitle?: string;
  userId: number;
  username: string;
  avatar?: string;
  content: string;
  status: string; // approved/pending/hidden
  createTime: string;
  likeCount: number;
  replies?: Comment[]; // 回复列表
}
```

### Resource（资源）

```typescript
interface Resource {
  id: number;
  name: string;
  title: string;
  description: string;
  resourceType: string; // document/video/code/other
  category: string;
  tag: string; // pdf/video/code/tool
  downloadUrl: string;
  coverImage?: string;
  fileSize: string;
  downloadCount: number;
  status: string; // enabled/disabled
  tags?: string[];
  isPublic: boolean;
  createTime: string;
  updateTime: string;
}
```

### KnowledgeMap（知识地图）

```typescript
interface KnowledgeMap {
  starId?: number;
  nodes: KnowledgeNode[];
  edges: KnowledgeEdge[];
}

interface KnowledgeNode {
  id: string;
  label: string;
  type: string; // core/topic/concept
  x: number;
  y: number;
}

interface KnowledgeEdge {
  source: string;
  target: string;
}
```

---

## 十四、总结

- ✅ 所有前端组件已更新为使用真实接口调用
- ✅ 所有接口已补充完整文档说明
- ✅ 统一使用 `BaseResponse<T>` 返回结构
- ✅ 统一错误处理机制（`isBusinessError`、`code`、`description`）
- ✅ RESTful 风格接口路径
- ✅ 完整的请求参数和响应结构说明

后端开发人员可直接参考本文档实现所有接口。
