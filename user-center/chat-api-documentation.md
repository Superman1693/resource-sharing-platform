# AI聊天功能接口文档

## 1. 接口概述

本接口文档描述了用户中心系统中的AI聊天功能接口，包括消息发送、会话管理、消息查询等功能。

## 2. 接口列表

| 接口名称 | URL | 方法 | 功能描述 |
|---------|-----|------|--------|
| 发送聊天消息 | /api/chat/message | POST | 发送消息到AI并获取回复 |
| 流式发送聊天消息 | /api/chat/message/stream | POST | 流式发送消息到AI并获取实时回复 |
| 获取会话列表 | /api/chat/sessions | GET | 获取当前用户的所有聊天会话 |
| 获取会话详情 | /api/chat/session/{sessionId} | GET | 获取指定会话的详细信息 |
| 创建会话 | /api/chat/session | POST | 创建一个新的聊天会话 |
| 更新会话 | /api/chat/session | PUT | 更新会话信息 |
| 删除会话 | /api/chat/session/{sessionId} | DELETE | 删除指定的聊天会话 |
| 获取会话消息列表 | /api/chat/messages/{sessionId} | GET | 获取指定会话的消息列表，支持分页 |
| 标记消息为已读 | /api/chat/message/{messageId}/read | PUT | 将指定消息标记为已读状态 |

## 3. 详细接口说明

### 3.1 发送聊天消息

**URL**: `/api/chat/message`
**方法**: `POST`
**请求体**:
```json
{
  "sessionId": "string", // 会话ID，如果为空则创建新会话
  "content": "string", // 消息内容（必填）
  "sessionName": "string", // 会话名称，创建新会话时使用
  "streaming": false // 是否使用流式响应
}
```

**响应格式**:
```json
{
  "code": 0,
  "data": {
    "messageId": "string",
    "sessionId": "string",
    "userId": 1,
    "senderType": "ai",
    "content": "string",
    "status": "delivered",
    "timestamp": "2026-03-29T16:00:00",
    "isStreaming": false,
    "isCompleted": true,
    "errorMessage": null
  },
  "message": "success"
}
```

### 3.2 流式发送聊天消息

**URL**: `/api/chat/message/stream`
**方法**: `POST`
**请求体**:
```json
{
  "sessionId": "string", // 会话ID，如果为空则创建新会话
  "content": "string", // 消息内容（必填）
  "sessionName": "string", // 会话名称，创建新会话时使用
  "streaming": true // 是否使用流式响应
}
```

**响应格式**:
- 类型: `text/event-stream`
- 内容: 流式返回AI的回复内容

### 3.3 获取会话列表

**URL**: `/api/chat/sessions`
**方法**: `GET`
**响应格式**:
```json
{
  "code": 0,
  "data": [
    {
      "sessionId": "string",
      "userId": 1,
      "sessionName": "string",
      "createTime": "2026-03-29T16:00:00",
      "updateTime": "2026-03-29T16:00:00",
      "status": "active",
      "messageCount": 5,
      "lastMessage": "string",
      "lastMessageTime": "2026-03-29T16:00:00"
    }
  ],
  "message": "success"
}
```

### 3.4 获取会话详情

**URL**: `/api/chat/session/{sessionId}`
**方法**: `GET`
**响应格式**:
```json
{
  "code": 0,
  "data": {
    "sessionId": "string",
    "userId": 1,
    "sessionName": "string",
    "createTime": "2026-03-29T16:00:00",
    "updateTime": "2026-03-29T16:00:00",
    "status": "active",
    "messageCount": 5,
    "lastMessage": "string",
    "lastMessageTime": "2026-03-29T16:00:00"
  },
  "message": "success"
}
```

### 3.5 创建会话

**URL**: `/api/chat/session`
**方法**: `POST`
**请求参数**:
- `sessionName`: 会话名称（必填）

**响应格式**:
```json
{
  "code": 0,
  "data": {
    "sessionId": "string",
    "userId": 1,
    "sessionName": "string",
    "createTime": "2026-03-29T16:00:00",
    "updateTime": "2026-03-29T16:00:00",
    "status": "active",
    "messageCount": 0,
    "lastMessage": null,
    "lastMessageTime": null
  },
  "message": "success"
}
```

### 3.6 更新会话

**URL**: `/api/chat/session`
**方法**: `PUT`
**请求体**:
```json
{
  "sessionId": "string", // 会话ID（必填）
  "sessionName": "string" // 会话名称
}
```

**响应格式**:
```json
{
  "code": 0,
  "data": {
    "sessionId": "string",
    "userId": 1,
    "sessionName": "string",
    "createTime": "2026-03-29T16:00:00",
    "updateTime": "2026-03-29T16:00:00",
    "status": "active",
    "messageCount": 5,
    "lastMessage": "string",
    "lastMessageTime": "2026-03-29T16:00:00"
  },
  "message": "success"
}
```

### 3.7 删除会话

**URL**: `/api/chat/session/{sessionId}`
**方法**: `DELETE`
**响应格式**:
```json
{
  "code": 0,
  "data": true,
  "message": "success"
}
```

### 3.8 获取会话消息列表

**URL**: `/api/chat/messages/{sessionId}`
**方法**: `GET`
**请求参数**:
- `page`: 页码，默认1
- `size`: 每页大小，默认20

**响应格式**:
```json
{
  "code": 0,
  "data": [
    {
      "messageId": "string",
      "sessionId": "string",
      "userId": 1,
      "senderType": "user",
      "content": "string",
      "status": "read",
      "timestamp": "2026-03-29T16:00:00",
      "isStreaming": false,
      "isCompleted": true,
      "errorMessage": null
    },
    {
      "messageId": "string",
      "sessionId": "string",
      "userId": 1,
      "senderType": "ai",
      "content": "string",
      "status": "delivered",
      "timestamp": "2026-03-29T16:00:01",
      "isStreaming": false,
      "isCompleted": true,
      "errorMessage": null
    }
  ],
  "message": "success"
}
```

### 3.9 标记消息为已读

**URL**: `/api/chat/message/{messageId}/read`
**方法**: `PUT`
**响应格式**:
```json
{
  "code": 0,
  "data": true,
  "message": "success"
}
```

## 4. 错误码说明

| 错误码 | 描述 | 解决方案 |
|-------|------|--------|
| 0 | 成功 | - |
| 40000 | 参数错误 | 检查请求参数是否正确 |
| 40100 | 未登录 | 请先登录系统 |
| 40300 | 无权限 | 您没有权限执行此操作 |
| 50000 | 系统错误 | 系统内部错误，请稍后重试 |

## 5. 认证方式

所有接口都需要用户登录后才能访问，使用Session进行身份验证。

## 6. 安全策略

1. **用户身份验证**：通过Session验证用户身份
2. **权限控制**：用户只能访问自己的会话和消息
3. **消息内容过滤**：系统会对消息内容进行安全检测
4. **速率限制**：防止恶意请求导致系统过载

## 7. 示例调用

### 7.1 发送消息示例

**请求**:
```bash
POST /api/chat/message
Content-Type: application/json

{
  "content": "你好，小峰！"
}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "messageId": "123456",
    "sessionId": "789012",
    "userId": 1,
    "senderType": "ai",
    "content": "哈哈～你好呀！我是小峰，见到你真开心！有什么好玩的事情想和我分享吗？😎",
    "status": "delivered",
    "timestamp": "2026-03-29T16:00:00",
    "isStreaming": false,
    "isCompleted": true,
    "errorMessage": null
  },
  "message": "success"
}
```

### 7.2 流式发送消息示例

**请求**:
```bash
POST /api/chat/message/stream
Content-Type: application/json

{
  "content": "给我讲一个笑话"
}
```

**响应**:
```
哈哈～你好呀！我是小峰，让我给你讲一个好玩的笑话吧！

有一天，小明问他爸爸："爸爸，为什么大海是蓝色的呀？"

爸爸想了想，说："因为鱼在海里游的时候，会一直说'blue blue blue'，所以大海就变成蓝色的了！"

小明听了，想了一会儿，然后说："那为什么红海是红色的呢？"

爸爸笑着说："因为那里的鱼只会说'red red red'呀！"

哈哈哈哈，是不是很有趣呀？😜
```

## 8. 技术实现

- **后端框架**: Spring Boot 3.2.12
- **AI服务**: 阿里云DashScope
- **存储**: Redis
- **认证**: Session
- **API文档**: Swagger/OpenAPI

## 9. 注意事项

1. **API密钥配置**: 确保在`application-dev.yml`中正确配置了阿里云DashScope的API密钥
2. **Redis服务**: 确保Redis服务正常运行，用于存储会话和消息
3. **会话管理**: 系统会自动管理会话，用户可以创建多个会话
4. **消息存储**: 消息会在Redis中存储30天，会话会存储7天
5. **流式响应**: 流式接口返回的是Server-Sent Events格式，前端需要使用相应的处理方式
