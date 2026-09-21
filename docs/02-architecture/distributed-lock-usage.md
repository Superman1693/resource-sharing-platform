# 分布式锁使用指南

## 一、已添加分布式锁的接口

全部锁的 `waitTime` 均为 `0`（不等待，拿不到锁立即失败）。当前共 **16 处** `@PreventDuplicate`：

### 🔴 严重级别（防止数据不一致，`leaseTime = 3`）

| 接口 | 文件 · 方法 | 说明 |
|------|-------------|------|
| 点赞笔记 | `NoteController.likeNote` | 防止重复点赞 |
| 点赞评论 | `CommentController.likeComment` | 防止重复点赞 |
| 收藏笔记 | `CollectionController.toggle` | 防止重复收藏 |
| 关注用户 | `FollowController.follow` | 防止 TOCTOU 竞态 |
| 取消关注 | `FollowController.unfollow` | 防止重复操作 |
| 添加评论 | `CommentController.addComment` | 防止重复提交 |
| 回复评论 | `CommentController.replyComment` | 防止重复提交 |
| 发送私信 | `MessageController.send` | 防止重复发送 |

### 🟡 中等级别（防止重复提交 / 名额超发，`leaseTime = 5`）

| 接口 | 文件 · 方法 | 说明 |
|------|-------------|------|
| 创建笔记 | `NoteController.createNote` | 防止重复提交 |
| 置顶笔记 | `NoteController.topNote` | 防止超发置顶名额（`keyPrefix = prevent_duplicate:topNote`） |
| 举报笔记 | `NoteController.reportNote` | 防止重复举报 |
| 举报评论 | `CommentController.reportComment` | 防止重复举报 |
| 添加资源 | `ResourceController.addResource` | 防止重复提交 |
| 加入星球 | `StarController.joinStar` | 防止 TOCTOU + 非原子计数 |
| 退出星球 | `StarController.exitStar` | 防止非原子计数 |
| 创建星球 | `StarController.createStar` | 防止重复创建 |

---

## 二、锁的 Key 格式

锁 Key 由 `interceptor/PreventDuplicateAspect.buildLockKey()` 生成：

```
{keyPrefix 或 prevent_duplicate}[:user:{userId} 或 :ip:{clientIp}]:uri:{requestUri}:method:{methodName}
```

- `keyPrefix` 未填时默认用字符串 `prevent_duplicate`
- 注解 `useUserId = true` 时追加 `:user:{userId}`；若取不到登录用户则追加 `:ip:{clientIp}`

示例：

```
prevent_duplicate:user:1001:uri:/api/note/like/123:method:likeNote
prevent_duplicate:user:1001:uri:/api/follow/add:method:follow
prevent_duplicate:topNote:user:1001:uri:/api/note/top/123:method:topNote
```

---

## 三、注解参数说明

```java
@PreventDuplicate(
    waitTime = 0,      // 等待获取锁的时间（秒），0 = 不等待直接失败
    leaseTime = 5,     // 锁的自动过期时间（秒）
    keyPrefix = "",    // 锁的 key 前缀，默认使用方法名
    useUserId = true,  // 是否使用用户 ID 作为 key 的一部分
    message = "操作过于频繁，请稍后再试"  // 提示信息
)
```

---

## 四、如何在新接口中使用

### 方式一：使用注解（推荐）

```java
@PostMapping("/your-api")
@LoginRequired
@PreventDuplicate(waitTime = 0, leaseTime = 5, message = "操作过于频繁")
public BaseResponse<?> yourMethod() {
    // 业务逻辑
}
```

### 方式二：使用工具类（灵活）

```java
@Resource
private DistributedLockUtils lockUtils;

public void yourMethod() {
    String lockKey = "lock:your-business:" + id;
    
    lockUtils.executeWithLock(lockKey, 3, 10,
        () -> {
            // 获取锁成功，执行业务逻辑
            doSomething();
        },
        () -> {
            // 获取锁失败
            throw new BusinessException(ErrorCode.OPERATION_TOO_FREQUENT);
        }
    );
}
```

---

## 五、锁的过期时间建议

| 场景 | 建议 leaseTime | 说明 |
|------|---------------|------|
| 点赞/取消点赞 | 3 秒 | 操作简单，快速完成 |
| 评论/回复 | 3 秒 | 操作简单 |
| 创建/编辑资源 | 5 秒 | 可能涉及文件上传 |
| 加入/退出组织 | 5 秒 | 涉及多表操作 |
| 发送消息 | 3 秒 | 操作简单 |
| 复杂业务 | 10-30 秒 | 根据业务耗时调整 |

---

## 六、错误码

| 错误码 | 说明 |
|--------|------|
| 42900 | 操作过于频繁，请稍后再试 |
| 42901 | 并发操作失败，请重试 |

---

## 七、内存占用

| 组件 | 内存占用 |
|------|---------|
| Redisson 客户端 | ~10MB (JVM 堆) |
| 每个锁 | ~100 bytes (Redis) |
| 1000 个并发锁 | ~100KB (可忽略) |

**结论**：分布式锁对服务器内存影响极小，700MB 内存完全可以使用。

---

## 八、注意事项

1. **锁的粒度**：锁的 key 要足够细粒度，避免锁范围过大影响并发
2. **过期时间**：leaseTime 要大于业务执行时间，避免业务未完成锁已过期
3. **异常处理**：确保在 finally 块中释放锁（AOP 切面已自动处理）
4. **避免嵌套锁**：避免在锁内再获取其他锁，防止死锁
5. **Redis 连接**：确保 Redis 连接正常，否则分布式锁会降级为无锁状态
