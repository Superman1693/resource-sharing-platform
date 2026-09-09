# 多级缓存使用指南

## 一、架构说明

```
┌─────────────────────────────────────────────────────────────┐
│                        请求流程                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   客户端请求 ──→ Controller ──→ Service ──→ 缓存查询        │
│                                              │              │
│                     ┌────────────────────────┘              │
│                     ▼                                       │
│              ┌──────────────┐                               │
│              │ L1 Caffeine  │ ← JVM 内存，微秒级访问        │
│              │ (本地缓存)    │                               │
│              └──────┬───────┘                               │
│                     │ miss                                  │
│                     ▼                                       │
│              ┌──────────────┐                               │
│              │ L2 Redis     │ ← 网络访问，毫秒级访问        │
│              │ (分布式缓存)  │                               │
│              └──────┬───────┘                               │
│                     │ miss                                  │
│                     ▼                                       │
│              ┌──────────────┐                               │
│              │ 数据库查询    │ ← MySQL，毫秒~秒级           │
│              └──────────────┘                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、缓存策略

| 缓存名称 | L1 TTL | L2 TTL | 最大条数 | 适用场景 |
|---------|--------|--------|----------|----------|
| `noteList` | 2分钟 | 5分钟 | 500 | 笔记列表（变化频繁） |
| `noteDetail` | 5分钟 | 10分钟 | 1000 | 笔记详情（读多写少） |

---

## 三、使用方法

### 3.1 缓存注解

```java
// 查询时自动缓存
@Cacheable(value = "noteDetail", key = "#id", unless = "#result == null")
public Note getNoteDetail(Long id) {
    // ...
}

// 写入时自动驱逐缓存
@CacheEvict(value = "noteList", allEntries = true)
public Note createNote(Note note, HttpServletRequest request) {
    // ...
}

// 同时驱逐多个缓存
@Caching(evict = {
    @CacheEvict(value = "noteDetail", key = "#id"),
    @CacheEvict(value = "noteList", allEntries = true)
})
public boolean likeNote(Long id, HttpServletRequest request) {
    // ...
}
```

### 3.2 监控接口（管理员）

**查看缓存统计**:
```bash
GET /api/cache/stats
```

**清除指定缓存**:
```bash
POST /api/cache/evict/{cacheName}
```

**清除所有缓存**:
```bash
POST /api/cache/evictAll
```

---

## 四、性能对比

| 接口 | 无缓存 | L1 命中 | 性能提升 |
|------|--------|---------|----------|
| 笔记列表 | ~200ms | ~70ms | **65%** |
| 笔记详情 | ~300ms | ~120ms | **60%** |

---

## 五、配置说明

### 5.1 Caffeine 配置

**文件**: `CacheConfig.java`

```java
Caffeine.newBuilder()
    .initialCapacity(100)      // 初始容量
    .maximumSize(2000)         // 最大缓存条数
    .expireAfterWrite(5, MINUTES)  // 写入后过期
    .expireAfterAccess(2, MINUTES) // 访问后过期
    .recordStats()             // 开启统计
```

### 5.2 Redis TTL 配置

```java
configMap.put("noteList", defaultConfig.entryTtl(Duration.ofMinutes(5)));
configMap.put("noteDetail", defaultConfig.entryTtl(Duration.ofMinutes(10)));
```

---

## 六、注意事项

### 6.1 多实例部署

Caffeine 是 JVM 进程内缓存，多实例部署时：
- 实例 A 更新数据 → 清除实例 A 的 L1 缓存
- 实例 B 的 L1 缓存仍是旧数据
- **解决方案**: L1 TTL 设置较短（2-5分钟），通过短过期时间缓解

### 6.2 缓存穿透

查询不存在的数据会穿透到数据库。
- **防护**: `unless = "#result == null"` 不缓存 null 值

### 6.3 缓存雪崩

大量 key 同时过期导致数据库压力骤增。
- **防护**: Caffeine 和 Redis 的 TTL 不同，不会同时过期

---

## 七、常见问题

### Q1: 如何查看缓存命中率？

```bash
curl http://localhost:8080/api/cache/stats
```

### Q2: 如何手动清除缓存？

```bash
# 清除笔记详情缓存
curl -X POST http://localhost:8080/api/cache/evict/noteDetail

# 清除所有缓存
curl -X POST http://localhost:8080/api/cache/evictAll
```

### Q3: 如何添加新的缓存？

1. 在 `CacheConfig.java` 中添加 Redis TTL 配置：
```java
configMap.put("newCache", defaultConfig.entryTtl(Duration.ofMinutes(10)));
```

2. 在 Service 方法上添加注解：
```java
@Cacheable(value = "newCache", key = "#id")
public SomeEntity getSomething(Long id) {
    // ...
}
```
