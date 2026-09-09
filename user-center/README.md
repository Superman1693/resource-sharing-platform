# 📚 编程知识星球 - 笔记资源分享平台

<div align="center">

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?logo=spring-boot)
![Vue.js](https://img.shields.io/badge/Vue.js-3.x-brightgreen?logo=vue.js)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)
![Redis](https://img.shields.io/badge/Redis-7.x-red?logo=redis)
![Elasticsearch](https://img.shields.io/badge/Elasticsearch-8.x-yellow?logo=elasticsearch)
![License](https://img.shields.io/badge/License-MIT-blue)

**一个现代化的编程学习社区平台，支持笔记发布、资源分享、AI 聊天、知识管理等功能**

[🌐 在线演示](https://www.e-ren.icu) · [🐛 报告 Bug](https://github.com/Superman1693/resource-sharing-platform/issues) · [💡 功能建议](https://github.com/Superman1693/resource-sharing-platform/issues)

</div>

---

## 📖 项目简介

编程知识星球是一个全栈的笔记资源分享平台，旨在为开发者提供一个学习交流的社区。用户可以发布笔记、分享资源、参与讨论、创建学习路径，并通过 AI 助手获取编程帮助。

### ✨ 核心亮点

- 🤖 **AI 智能助手** - 集成大语言模型，提供编程问答和代码建议
- 📝 **Markdown 编辑器** - 支持实时预览、代码高亮、快捷键操作
- 🔍 **全文搜索** - 基于 Elasticsearch 的高性能搜索，支持中文分词
- 🔒 **安全可靠** - JWT 认证、分布式锁、XSS 防护、接口限流、文件上传校验
- ⚡ **高并发优化** - 原子计数器、多级缓存、布隆过滤器、异步事件驱动
- 📱 **响应式设计** - 完美适配桌面端和移动端

---

## 🚀 功能特性

### 用户系统
- ✅ 用户注册/登录（账号密码 + GitHub OAuth）
- ✅ 个人主页与资料编辑
- ✅ 用户关注/粉丝系统
- ✅ 私信聊天

### 笔记功能
- ✅ Markdown 编辑器（实时预览、代码高亮）
- ✅ 笔记分类与标签
- ✅ 笔记点赞、评论、收藏
- ✅ 笔记置顶与审核
- ✅ 浏览量统计与热度排名

### 资源分享
- ✅ 文件上传（支持多种格式）
- ✅ 资源分类与搜索
- ✅ 下载计数

### 知识管理
- ✅ 知识星球（创建/加入）
- ✅ 学习路径规划
- ✅ 学习进度追踪

### AI 助手
- ✅ AI 聊天（支持流式响应）
- ✅ 聊天会话管理
- ✅ 编程问答

### 社区互动
- ✅ 站内通知系统
- ✅ 评论审核机制
- ✅ 举报功能
- ✅ 贡献热力图

---

## 🛠️ 技术栈

### 后端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.12 | 核心框架 |
| MyBatis-Plus | 3.5.14 | ORM 框架 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 7.x | 缓存、会话、分布式锁 |
| Elasticsearch | 8.15 | 全文搜索 |
| Spring AI | 1.0.0-M4 | AI 能力集成 |
| Redisson | 3.27 | 分布式锁、布隆过滤器 |
| JWT | 0.12.3 | 身份认证 |
| Spring Security | - | 安全框架 |
| Aliyun OSS | 3.17 | 文件存储 |
| SpringDoc | 2.5 | API 文档 |
| Druid | 1.2.x | 数据库连接池 + SQL 监控 |
| Caffeine | 3.x | 本地缓存（L1） |
| Jackson | 2.x | JSON 序列化（替代 FastJSON） |

### 前端技术

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue.js | 3.x | 核心框架 |
| Vite | 7.x | 构建工具 |
| Ant Design Vue | 4.x | UI 组件库 |
| Pinia | 3.x | 状态管理（持久化插件） |
| Vue Router | 4.x | 路由管理 |
| Axios | 1.x | HTTP 客户端 |
| Marked | 17.x | Markdown 解析 |
| Vditor | 3.x | Markdown 编辑器 |
| ECharts | - | 图表可视化 |
| highlight.js | 11.x | 代码高亮 |
| DOMPurify | 3.x | XSS 防护 |

---

## 📁 项目结构

```
resource-sharing-platform/
├── user-center/                 # 后端项目
│   ├── src/main/java/com/example/usercenter/
│   │   ├── annotation/         # 自定义注解
│   │   │   ├── LoginRequired   # 登录校验
│   │   │   ├── AdminRequired   # 管理员校验
│   │   │   ├── PreventDuplicate # 防重复提交
│   │   │   ├── RateLimit       # 接口限流
│   │   │   └── MaskSensitive   # 数据脱敏
│   │   ├── common/             # 通用模型
│   │   │   ├── BaseResponse    # 统一响应
│   │   │   ├── ErrorCode       # 错误码
│   │   │   ├── PageResult      # 分页结果
│   │   │   └── ResultUtils     # 响应工具
│   │   ├── config/             # 配置类
│   │   │   ├── CacheConfig     # 多级缓存（Caffeine L1 + Redis L2）
│   │   │   ├── BloomFilterConfig # 布隆过滤器
│   │   │   ├── MybatisPlusConfig # 分页 + 多租户插件
│   │   │   ├── RedissonConfig  # 分布式锁配置
│   │   │   ├── RedisConfig     # Redis 配置
│   │   │   ├── SecurityConfig  # 安全配置
│   │   │   ├── WebSocketConfig # WebSocket 配置
│   │   │   ├── AsyncConfig     # 异步线程池
│   │   │   └── JacksonConfig   # Jackson 序列化
│   │   ├── controller/         # 控制器层（REST API）
│   │   ├── service/            # 业务逻辑层
│   │   ├── mapper/             # 数据访问层
│   │   ├── model/              # 数据模型
│   │   │   ├── domain/         # 实体类
│   │   │   ├── domain/request/ # 请求 DTO
│   │   │   ├── dto/            # 传输对象
│   │   │   └── es/             # ES 文档模型
│   │   ├── interceptor/        # 拦截器
│   │   │   ├── AuthInterceptor # JWT 鉴权
│   │   │   ├── RateLimitInterceptor # 限流
│   │   │   └── TenantInterceptor # 多租户隔离
│   │   ├── event/              # Spring 事件
│   │   ├── filter/             # XSS 过滤器
│   │   ├── serializer/         # Jackson 自定义序列化
│   │   ├── task/               # 定时任务
│   │   └── utils/              # 工具类
│   │       ├── JwtUtils        # JWT 工具
│   │       ├── UserContext     # 用户上下文
│   │       ├── DistributedLockUtils # 分布式锁
│   │       ├── AliyunOSSOperator # OSS 操作
│   │       └── RedisChatOperator # Redis 聊天
│   ├── src/main/resources/
│   │   ├── application.yml     # 主配置
│   │   ├── mapper/             # MyBatis XML
│   │   └── db/                 # Flyway 迁移脚本
│   └── pom.xml
│
├── demo1/                       # 前端项目
│   ├── src/
│   │   ├── views/              # 页面组件
│   │   │   ├── Notes/          # 笔记相关页面
│   │   │   ├── Resources/      # 资源相关页面
│   │   │   ├── Stars/          # 星球相关页面
│   │   │   ├── Knowledge/      # 知识图谱页面
│   │   │   ├── User/           # 用户相关页面
│   │   │   └── Stats/          # 统计相关页面
│   │   ├── components/         # 公共组件
│   │   ├── layouts/            # 布局组件
│   │   │   ├── BasicLayout     # 管理端布局
│   │   │   └── UserLayout      # 用户端布局
│   │   ├── router/             # 路由配置
│   │   ├── store/              # Pinia 状态管理
│   │   │   ├── userLogin.js    # 用户认证状态
│   │   │   ├── app.js          # 应用状态
│   │   │   └── notification.js # 通知状态
│   │   └── utils/              # 工具函数
│   │       ├── request.js      # axios 封装
│   │       ├── api.js          # API 调用封装
│   │       ├── errorTracker.js # 错误追踪
│   │       └── performance.js  # 性能监控
│   ├── package.json
│   └── vite.config.js
│
└── README.md
```

---

## 🚀 快速开始

### 环境要求

- **JDK**: 17+
- **Node.js**: 18+
- **MySQL**: 8.0+
- **Redis**: 7.x
- **Elasticsearch**: 8.x（可选，用于搜索功能）

### 1. 克隆项目

```bash
git clone https://github.com/Superman1693/resource-sharing-platform.git
cd resource-sharing-platform
```

### 2. 数据库配置

```sql
-- 创建数据库
CREATE DATABASE yiya DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 导入表结构
mysql -u root -p yiya < demo1/database.sql
```

### 3. 后端配置

```bash
cd user-center

# 复制配置文件示例
cp src/main/resources/application-dev.yml.example src/main/resources/application-dev.yml

# 编辑配置文件，填入你的配置
# - MySQL 连接信息
# - Redis 连接信息
# - 阿里云 OSS 配置
# - AI API Key
```

### 4. 启动后端

```bash
# 使用 Maven 启动
./mvnw spring-boot:run

# 或者指定 profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

后端启动在 `http://localhost:8080`

### 5. 前端配置

```bash
cd demo1

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端启动在 `http://localhost:5173`

### 6. 访问应用

- **前端地址**: http://localhost:5173
- **后端 API**: http://localhost:8080/api
- **Swagger 文档**: http://localhost:8080/api/swagger-ui.html

---

## ⚙️ 配置说明

### application-dev.yml 配置项

```yaml
center:
  datasource:
    host: localhost          # MySQL 地址
    port: 3306              # MySQL 端口
    database: yiya          # 数据库名
    username: root          # 用户名
    password: your_password # 密码

  redis:
    host: localhost          # Redis 地址
    port: 6379              # Redis 端口
    password: your_password # Redis 密码
    database: 1             # Redis 数据库

  aliyun:
    oss:
      endpoint: https://oss-cn-beijing.aliyuncs.com
      access-key-id: your_access_key_id
      access-key-secret: your_access_key_secret
      bucket-name: your_bucket_name

  openai:
    api-key: your_api_key   # DashScope API Key
    base-url: https://dashscope.aliyuncs.com/compatible-mode/

spring:
  jwt:
    secret: your_random_256bit_secret  # JWT 签名密钥（建议随机生成）
    expiration: 86400                   # Token 过期时间（秒）

github:
  oauth:
    client-id: your_github_client_id
    client-secret: your_github_client_secret
    redirect-uri: http://localhost:5173/oauth/callback
```

### 配置类清单

| 配置类 | 职责 |
|--------|------|
| `CacheConfig` | 多级缓存：Caffeine L1 + Redis L2 |
| `BloomFilterConfig` | Redisson 布隆过滤器，防缓存穿透 |
| `MybatisPlusConfig` | 分页插件 + 多租户插件 |
| `RedissonConfig` | Redisson 客户端（分布式锁） |
| `WebSocketConfig` | STOMP 消息代理 + SockJS 端点 |
| `AsyncConfig` | @Async 线程池 + @EnableScheduling |
| `SecurityConfig` | Spring Security（禁用 CSRF/Session，CORS） |
| `WebMvcConfig` | 拦截器注册（Auth + RateLimit） |
| `JacksonConfig` | Jackson 全局配置 |
| `DruidConfig` | Druid 连接池 + SQL 防火墙 + 监控 |
| `ElasticsearchConfig` | ES 8.x Java Client |
| `SwaggerConfig` | SpringDoc OpenAPI 文档 |

---

## 📚 API 文档

启动后端后，访问 Swagger 文档：

```
http://localhost:8080/api/swagger-ui.html
```

### 主要 API 模块

| 模块 | 路径前缀 | 说明 |
|------|----------|------|
| 用户 | `/api/user` | 注册、登录、个人信息 |
| 笔记 | `/api/note` | 笔记 CRUD、点赞、评论 |
| 资源 | `/api/resource` | 资源上传、下载 |
| 评论 | `/api/comment` | 评论、回复、举报 |
| 关注 | `/api/follow` | 关注、粉丝 |
| 私信 | `/api/message` | 私信聊天 |
| 星球 | `/api/star` | 知识星球 |
| 通知 | `/api/notification` | 站内通知 |
| AI | `/api/chat` | AI 聊天 |

---

## 🔒 安全特性

### 1. 身份认证
- JWT 无状态认证
- Token 黑名单机制（注销/销户后立即失效）
- 支持 GitHub OAuth 登录

### 2. 接口安全
- 分布式锁防重复提交
- Redis 滑动窗口限流（登录接口 10次/分钟）
- XSS 过滤（Jsoup）
- CORS 跨域配置
- 文件上传安全：扩展名白名单 + 大小限制 + 文件名清洗

### 3. 数据安全
- 密码 BCrypt 加密（自动迁移旧 MD5 密码）
- 敏感信息配置分离（配置文件不入 Git）
- SQL 注入防护（MyBatis-Plus 参数化查询）
- 数据脱敏：手机号/邮箱自动脱敏输出（`138****5678` / `z***@qq.com`）

### 4. 并发安全
- 点赞/下载等计数操作使用原子 SQL 递增，避免并发丢失更新
- 会话创建使用事务 + 唯一约束兜底，防止重复创建
- 用户注册使用数据库唯一约束兜底，防止并发重复注册

---

## 🧪 测试

### 运行后端测试

```bash
cd user-center
./mvnw test
```

### 分布式锁测试

```bash
# Linux/Mac
bash test/distributed-lock-test.sh

# Windows PowerShell
.\test\distributed-lock-test.ps1
```

---

## 📦 部署

### Docker 部署（推荐）

```bash
# 构建后端
cd user-center
./mvnw clean package -DskipTests

# 构建前端
cd demo1
npm run build

# 使用 Docker Compose 启动
docker-compose up -d
```

### 传统部署

1. 打包后端：`mvn clean package -DskipTests`
2. 运行：`java -jar target/user-center-1.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
3. 部署前端：将 `demo1/dist` 目录部署到 Nginx

---

## 📋 更新日志

### 2025-06-08 - 安全与稳定性修复

#### 🔴 安全修复
- 文件上传接口添加认证、文件类型白名单、大小限制、文件名清洗
- 登录接口添加限流（10次/分钟），防止暴力破解
- `getUnreadCount` 改用参数化查询，消除 SQL 注入风险

#### 🟠 稳定性修复
- 用户注册：并发竞态条件修复，数据库唯一约束兜底
- 账号注销：JWT Token 立即失效（加入 Redis 黑名单）
- 评论点赞/资源下载：改用原子 SQL 递增，避免并发丢失更新
- 会话创建：事务 + 唯一约束兜底，防止重复创建

#### 🟡 代码质量
- 多个 Controller 添加缺失的 `@LoginRequired` / `@AdminRequired` 注解
- 前端 `NoteManage.vue` 修复 filterForm 未声明导致的运行时错误
- 前端 `OAuthCallback.vue` 修复 Pinia store 直接赋值导致的持久化问题
- 前端 `NoteDetail.vue` 修复 marked.js 废弃 API 导致的代码高亮失效

---

## 🤝 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建功能分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'Add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 提交 Pull Request

### 代码规范

- 后端遵循阿里巴巴 Java 开发手册
- 前端使用 ESLint + Prettier
- 提交信息使用 Conventional Commits 规范

---

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源许可证。

---

## 🙏 致谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [Ant Design Vue](https://antdv.com/)
- [MyBatis-Plus](https://baomidou.com/)
- [Redisson](https://redisson.org/)

---

## 📧 联系方式

- **作者**: Superman1693
- **GitHub**: [Superman1693](https://github.com/Superman1693)
- **Gitee**: [Superman1693](https://gitee.com/Superman1693)

---

<div align="center">

**如果这个项目对你有帮助，请给个 ⭐ Star 支持一下！**

</div>
