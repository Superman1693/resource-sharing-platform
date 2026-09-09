# 分布式锁使用指南

## 一、已添加分布式锁的接口

### 🔴 严重级别（防止数据不一致）

| 接口 | 文件 | 锁参数 | 说明 |
|------|------|--------|------|
| 点赞笔记 | `NoteController.java` | `waitTime=0, leaseTime=3` | 防止重复点赞 |
| 点赞评论 | `CommentController.java` | `waitTime=0, leaseTime=3` | 防止重复点赞 |
| 关注用户 | `FollowController.java` | `waitTime=0, leaseTime=3` | 防止 TOCTOU 竞态 |
| 取消关注 | `FollowController.java` | `waitTime=0, leaseTime=3` | 防止重复操作 |
| 加入星球 | `StarController.java` | `waitTime=0, leaseTime=5` | 防止 TOCTOU + 非原子计数 |
| 退出星球 | `StarController.java` | `waitTime=0, leaseTime=5` | 防止非原子计数 |

### 🟡 中等级别（防止重复提交）

| 接口 | 文件 | 锁参数 | 说明 |
|------|------|--------|------|
| 创建笔记 | `NoteController.java` | `waitTime=0, leaseTime=5` | 防止重复提交 |
| 置顶笔记 | `NoteController.java` | `waitTime=0, leaseTime=5` | 防止超发置顶名额 |
| 举报笔记 | `NoteController.java` | `waitTime=0, leaseTime=5` | 防止重复举报 |
| 添加评论 | `CommentController.java` | `waitTime=0, leaseTime=3` | 防止重复提交 |
| 回复评论 | `CommentController.java` | `waitTime=0, leaseTime=3` | 防止重复提交 |
| 举报评论 | `CommentController.java` | `waitTime=0, leaseTime=5` | 防止重复举报 |
| 添加资源 | `ResourceController.java` | `waitTime=0, leaseTime=5` | 防止重复提交 |
| 发送私信 | `MessageController.java` | `waitTime=0, leaseTime=3` | 防止重复发送 |

---

## 二、锁的 Key 格式

锁的 Key 自动生成规则：

```
{keyPrefix}:{useUserId ? "user:{userId}" : "ip:{clientIp}"}:{requestUri}:{methodName}
```

示例：
```
prevent_duplicate:likeNote:uri:/api/note/like/123:user:1001
prevent_duplicate:follow:uri:/api/follow/add:user:1001
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
