# 系统接口与数据模型设计总览

> 说明：本文档根据现有前端页面与业务流程整理，涵盖接口规范、字段说明及数据库建议表结构，便于后端代码实现与数据建模。接口统一返回 `BaseResponse<T>`。

---

## 1. 公共说明

- 基础路径：`/api`（前端通过 Vite 代理转发至后端 `http://localhost:8080`）
- 认证机制：Session（登录成功后设置 `JSESSIONID` Cookie）
- 通用返回结构：

{
"code": 0,
"data": {},
"message": "ok",
"description": ""
}| 字段 | 说明 |
| --- | --- |
| `code` | 0=成功；其他=失败 |
| `data` | 实际业务数据 |
| `message` | 简要提示 |
| `description` | 失败时的详细说明（前端优先使用该字段展示） |

---

## 2. 用户模块（User）

### 2.1 接口列表

| 功能             | 方法&路径                 | 请求体 / 参数         | 返回数据         |
| ---------------- | ------------------------- | --------------------- | ---------------- |
| 用户注册         | `POST /user/register`     | `UserRegisterRequest` | `Long` 新用户 ID |
| 用户登录         | `POST /user/login`        | `UserLoginRequest`    | `UserVO`         |
| 用户注销（前端） | `POST /user/userLogout`   | 无                    | `1`表示成功      |
| 获取当前登录用户 | `GET /user/current`       | 无                    | `UserVO`         |
| 更新用户信息     | `PUT /user/update`        | `UserUpdateRequest`   | `"更新成功"`     |
| 管理员搜索用户   | `GET /user/search`        | `userAccount` (可选)  | `List<UserVO>`   |
| 管理员删除用户   | `POST /user/delete`       | `Long` 用户 ID        | `true/false`     |
| 文件上传         | `POST /user/upload`       | multipart/form-data   | `"上传成功"`     |
| 账号注销（新增） | `POST /user/closeAccount` | `CloseAccountRequest` | `"注销成功"`     |

### 2.2 数据结构

{
"UserRegisterRequest": {
"userAccount": "string(4-20)",
"userPassword": "string(>=8)",
"checkPassword": "string(>=8)",
"username": "string",
"phone": "string",
"email": "string",
"avatarUrl": "string",
"gender": 0
},
"UserLoginRequest": {
"userAccount": "string",
"userPassword": "string"
},
"UserUpdateRequest": {
"id": 1,
"username": "string",
"phone": "string",
"email": "string",
"avatarUrl": "string",
"gender": 0,
"userPassword": "string>=8 (可选)"
},
"UserVO": {
"id": 1,
"username": "string",
"userAccount": "string",
"avatarUrl": "string",
"gender": 0,
"phone": "string",
"email": "string",
"userStatus": 0,
"userRole": 0,
"createTime": "datetime",
"updateTime": "datetime"
},
"CloseAccountRequest": {
"userPassword": "string>=8"
}
}### 2.3 建议表结构

- `user`（用户表）
  - `id` BIGINT PK
  - `user_account` VARCHAR(32) UNIQUE
  - `user_password` VARCHAR(256)
  - `username` VARCHAR(32)
  - `phone` VARCHAR(20)
  - `email` VARCHAR(64)
  - `avatar_url` VARCHAR(255)
  - `gender` TINYINT
  - `user_status` TINYINT (0=正常,1=封禁,2=注销)
  - `user_role` TINYINT (0=普通,1=管理员,2=超级)
  - `create_time` DATETIME
  - `update_time` DATETIME
  - `is_delete` TINYINT

---

## 3. 商品管理（Product）

### 3.1 接口列表

| 功能         | 方法&路径                  | 请求体 / 参数                                | 返回数据          |
| ------------ | -------------------------- | -------------------------------------------- | ----------------- |
| 查询商品列表 | `GET /product/list`        | 支持 `keyword, category, status, page, size` | `Page<ProductVO>` |
| 商品详情     | `GET /product/{id}`        | 路径参数 `id`                                | `ProductDetailVO` |
| 新增商品     | `POST /product`            | `ProductCreateRequest`                       | `Long` 商品 ID    |
| 更新商品     | `PUT /product/{id}`        | `ProductUpdateRequest`                       | `"更新成功"`      |
| 上架/下架    | `PUT /product/{id}/status` | `{ "status": "on/off" }`                     | `"操作成功"`      |
| 删除商品     | `DELETE /product/{id}`     | 路径参数                                     | `"删除成功"`      |

### 3.2 数据结构

{
"ProductVO": {
"id": 1001,
"name": "string",
"category": "string",
"price": 399,
"stock": 120,
"status": "on/off",
"imageUrl": "string",
"updatedAt": "datetime"
},
"ProductDetailVO": {
"id": 1001,
"name": "string",
"category": "string",
"price": 399,
"stock": 120,
"status": "on/off",
"imageUrl": "string",
"description": "text",
"specs": "json or text",
"updatedAt": "datetime",
"createdAt": "datetime"
},
"ProductCreateRequest": {
"name": "string",
"category": "string",
"price": 399,
"stock": 120,
"imageUrl": "string",
"description": "string",
"specs": "string/json"
},
"ProductUpdateRequest": {
"name": "string",
"category": "string",
"price": 399,
"stock": 120,
"imageUrl": "string",
"description": "string",
"specs": "string/json",
"status": "on/off"
}
}### 3.3 建议表结构

- `product`
  - `id` BIGINT PK
  - `name` VARCHAR(64)
  - `category` VARCHAR(32)
  - `price` DECIMAL(10,2)
  - `stock` INT
  - `status` VARCHAR(8) (`on/off`)
  - `image_url` VARCHAR(255)
  - `description` TEXT
  - `specs` JSON
  - `created_at` DATETIME
  - `updated_at` DATETIME
  - `is_delete` TINYINT

---

## 4. 订单管理（Order）

### 4.1 接口列表

| 功能         | 方法&路径                | 请求体 / 参数                                             | 返回数据        |
| ------------ | ------------------------ | --------------------------------------------------------- | --------------- |
| 查询订单列表 | `GET /order/list`        | `orderId, status, payType, page, size`                    | `Page<OrderVO>` |
| 订单详情     | `GET /order/{id}`        | 路径参数                                                  | `OrderDetailVO` |
| 发货         | `PUT /order/{id}/ship`   | `{ "logisticsNo": "string" }`                             | `"发货成功"`    |
| 完成订单     | `PUT /order/{id}/finish` | 无                                                        | `"操作成功"`    |
| 修改订单状态 | `PUT /order/{id}/status` | `{ "status": "pending/paid/shipped/finished/cancelled" }` | `"操作成功"`    |

### 4.2 数据结构

{
"OrderVO": {
"id": "string",
"customer": "string",
"amount": 1288,
"status": "pending/paid/shipped/finished",
"payType": "alipay/wechat/unionpay",
"items": 3,
"createdAt": "datetime"
},
"OrderDetailVO": {
"id": "string",
"customer": "string",
"amount": 1288,
"status": "pending/paid/shipped/finished",
"payType": "alipay/wechat/unionpay",
"createdAt": "datetime",
"address": "string",
"logisticsNo": "string",
"goods": [
{
"name": "string",
"price": 399,
"quantity": 2
}
]
}
}### 4.3 建议表结构

- `order`

  - `id` VARCHAR(32) PK
  - `user_id` BIGINT
  - `amount` DECIMAL(10,2)
  - `status` VARCHAR(16)
  - `pay_type` VARCHAR(16)
  - `address` VARCHAR(255)
  - `logistics_no` VARCHAR(64)
  - `created_at` DATETIME
  - `updated_at` DATETIME

- `order_item`
  - `id` BIGINT PK
  - `order_id` VARCHAR(32)
  - `product_id` BIGINT
  - `product_name` VARCHAR(64)
  - `price` DECIMAL(10,2)
  - `quantity` INT

---

## 5. 售后与客服模块

### 5.1 售后工单（AfterSales）

| 功能         | 方法&路径                      | 请求体 / 参数                         | 返回数据             |
| ------------ | ------------------------------ | ------------------------------------- | -------------------- |
| 查询售后工单 | `GET /afterSales/list`         | `requestId, type, status, page, size` | `Page<AfterSalesVO>` |
| 处理售后工单 | `PUT /afterSales/{id}/process` | 无                                    | `"进入处理中"`       |
| 完成售后工单 | `PUT /afterSales/{id}/finish`  | 无                                    | `"已完成"`           |
| 驳回售后工单 | `PUT /afterSales/{id}/reject`  | `{ "reason": "string" }`              | `"已驳回"`           |

数据结构：

{
"AfterSalesVO": {
"id": "string",
"orderId": "string",
"customer": "string",
"type": "return/exchange/repair",
"reason": "string",
"status": "pending/processing/finished/rejected",
"createdAt": "datetime"
}
}表结构建议：

- `after_sales`
  - `id` VARCHAR(32) PK
  - `order_id` VARCHAR(32)
  - `user_id` BIGINT
  - `type` VARCHAR(16)
  - `reason` TEXT
  - `status` VARCHAR(16)
  - `reject_reason` TEXT
  - `created_at` DATETIME
  - `updated_at` DATETIME

### 5.2 客服工单（Service Ticket）

| 功能         | 方法&路径                         | 请求体 / 参数                | 返回数据                |
| ------------ | --------------------------------- | ---------------------------- | ----------------------- |
| 查询客服工单 | `GET /serviceTicket/list`         | `ticketId, priority, status` | `Page<ServiceTicketVO>` |
| 接单         | `PUT /serviceTicket/{id}/accept`  | 无                           | `"已接单"`              |
| 完成         | `PUT /serviceTicket/{id}/resolve` | `{ "solution": "string" }`   | `"已解决"`              |
| 挂起         | `PUT /serviceTicket/{id}/suspend` | `{ "reason": "string" }`     | `"已挂起"`              |

数据结构：

{
"ServiceTicketVO": {
"id": "string",
"customer": "string",
"subject": "string",
"priority": "low/medium/high",
"status": "pending/processing/resolved/suspended",
"createdAt": "datetime"
}
}表结构：

- `service_ticket`
  - `id` VARCHAR(32) PK
  - `user_id` BIGINT
  - `subject` VARCHAR(128)
  - `description` TEXT
  - `priority` VARCHAR(8)
  - `status` VARCHAR(16)
  - `solution` TEXT
  - `suspend_reason` TEXT
  - `created_at` DATETIME
  - `updated_at` DATETIME

---

## 6. 资源管理模块（Resource）

| 功能          | 方法&路径                   | 请求体 / 参数                      | 返回数据           |
| ------------- | --------------------------- | ---------------------------------- | ------------------ |
| 查询资源      | `GET /resource/list`        | `keyword, tag, status`             | `Page<ResourceVO>` |
| 新增资源      | `POST /resource`            | `ResourceCreateRequest`            | `Long` ID          |
| 更新资源      | `PUT /resource/{id}`        | `ResourceUpdateRequest`            | `"更新成功"`       |
| 启用/停用资源 | `PUT /resource/{id}/status` | `{ "status": "enabled/disabled" }` | `"操作成功"`       |

数据结构：

{
"ResourceVO": {
"id": 1,
"name": "string",
"tag": "string",
"status": "enabled/disabled",
"url": "string",
"updatedAt": "datetime"
},
"ResourceCreateRequest": {
"name": "string",
"tag": "string",
"url": "string",
"status": "enabled"
}
}表结构：

- `resource_material`
  - `id` BIGINT PK
  - `name` VARCHAR(64)
  - `tag` VARCHAR(32)
  - `url` VARCHAR(255)
  - `status` VARCHAR(16)
  - `created_at` DATETIME
  - `updated_at` DATETIME

---

## 7. 推广运营模块（Campaign）

| 功能         | 方法&路径                   | 请求体 / 参数           | 返回数据             |
| ------------ | --------------------------- | ----------------------- | -------------------- |
| 查询推广活动 | `GET /campaign/list`        | `type, status`          | `Page<CampaignVO>`   |
| 创建活动     | `POST /campaign`            | `CampaignCreateRequest` | `Long` 活动 ID       |
| 更新活动     | `PUT /campaign/{id}`        | `CampaignUpdateRequest` | `"更新成功"`         |
| 复制活动     | `POST /campaign/{id}/clone` | 无                      | `Long` 新活动 ID     |
| 相关二维码   | `GET /campaign/qrcode`      | 可选 `type`             | `List<CampaignQrVO>` |

数据结构：

{
"CampaignVO": {
"id": 1,
"name": "string",
"type": "online/offline/partner",
"status": "running/pending/finished",
"budget": 30000,
"leads": 560,
"start": "date",
"end": "date"
},
"CampaignCreateRequest": {
"name": "string",
"type": "online/offline/partner",
"budget": 30000,
"start": "date",
"end": "date",
"goal": "string"
},
"CampaignQrVO": {
"id": 1,
"title": "string",
"desc": "string",
"url": "string"
}
}表结构：

- `campaign`

  - `id` BIGINT PK
  - `name` VARCHAR(64)
  - `type` VARCHAR(16)
  - `status` VARCHAR(16)
  - `budget` DECIMAL(12,2)
  - `leads` INT
  - `start_date` DATE
  - `end_date` DATE
  - `goal` TEXT
  - `created_at` DATETIME
  - `updated_at` DATETIME

- `campaign_qrcode`
  - `id` BIGINT PK
  - `title` VARCHAR(64)
  - `description` VARCHAR(128)
  - `image_url` VARCHAR(255)
  - `campaign_id` BIGINT (可选)

---

## 8. 数据统计模块

### 8.1 销售统计（Sales Stats）

| 功能     | 方法&路径                   | 请求体 / 参数                | 返回数据              |
| -------- | --------------------------- | ---------------------------- | --------------------- |
| 销售概览 | `GET /stats/sales/overview` | `range`                      | `SalesOverviewVO`     |
| 销售明细 | `GET /stats/sales/list`     | `range, channel, page, size` | `Page<SalesRecordVO>` |

数据结构：

{
"SalesOverviewVO": {
"gmv": 186532,
"orders": 3210,
"refund": 8650,
"avgOrder": 580,
"gmvTrend": 0.125,
"orderTrend": 0.081,
"refundTrend": -0.023,
"avgOrderTrend": 0.034
},
"SalesRecordVO": {
"date": "2025-10-01",
"channel": "官网商城",
"orders": 320,
"gmv": 168530,
"refund": 3200
}
}表结构数据来源可直接聚合自 `order` 表。

### 8.2 用户统计（User Stats）

| 功能         | 方法&路径                  | 请求体 / 参数               | 返回数据                 |
| ------------ | -------------------------- | --------------------------- | ------------------------ |
| 用户概览     | `GET /stats/user/overview` | `range`                     | `UserOverviewVO`         |
| 用户来源明细 | `GET /stats/user/list`     | `range, source, page, size` | `Page<UserStatRecordVO>` |

数据结构：

{
"UserOverviewVO": {
"totalUsers": 86520,
"newUsers": 2350,
"activeUsers": 18960,
"conversionRate": 0.125,
"totalUsersTrend": 0.053,
"newUsersTrend": 0.121,
"activeUsersTrend": 0.068
},
"UserStatRecordVO": {
"date": "2025-10-01",
"source": "自然注册",
"newUsers": 180,
"activeUsers": 980
}
}数据来源：可聚合 `user` 表 + 活跃日志。

---

## 9. 其他接口（上传等）

- 文件上传：`POST /user/upload`（已定义，返回字符串或 `{ url: "" }`）
- 通用分页结构：建议使用 `Page<T>` → `records`, `total`, `page`, `size`

---

## 10. 开发注意事项

1. **后端需统一在失败时返回 `description`**，前端已优先显示该字段。
2. **上传接口需返回可访问的图片 URL**（可用字符串或 `data.url`）；前端已同时兼容。
3. **状态字段规范**：尽量使用固定枚举以便前端转换展示，例如：
   - 商品：`on/off`
   - 订单：`pending/paid/shipped/finished/cancelled`
   - 售后：`pending/processing/finished/rejected`
   - 客服工单：`pending/processing/resolved/suspended`
4. **分页接口**：建议参数 `page`（默认 1）, `size`（默认 10~20），返回 `total` 与 `records`。
5. **时间字段**统一使用 ISO8601 格式字符串（含时区）。

---

如需后续扩展（如真正的增删改接口、批量导入导出、报表下载），可在上述基础上新增对应的 RESTful 接口并扩展表结构。
