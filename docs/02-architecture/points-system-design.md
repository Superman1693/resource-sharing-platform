# 积分体系使用方案设计

> 日期：2026-10-16
> 背景：现有积分**只有获取、没有消耗**，长期只增不减 → 通胀后失去意义。
> 本文给出闭环设计与落地优先级。

---

## 一、现状盘点

| 项 | 现状 |
|---|---|
| 账户 | `points_account(user_id, balance, total_earned, update_time)` — `balance` 是余额，`total_earned` 是累计获得 |
| 流水 | `points_log(user_id, change, type, ref_type, ref_id, remark, create_time)` |
| **获取** | 签到 `5 + min(连续天数, 20)`；发布笔记 `+20`；笔记被点赞 `+2`；发表评论 `+1`；回复评论 `+1` |
| **消耗** | **无** |
| 已具备但闲置的能力 | `note.is_top`（置顶）、`note.locked`（`@TableField(exist=false)`，目前只用于「未加入星球时遮蔽正文」）、资源 `download_count` |

**核心问题**：积分是纯荣誉值。用户攒到一定量后没有任何用途，既不能激励持续产出，也不能回报优质内容的作者。

---

## 二、设计原则（先定方向，再选功能）

1. **闭环指向内容创作者**。最合适的消耗不是「被平台收走」，而是**在用户之间流动** —— 读者用积分换取作者的内容/服务，作者获得积分后可继续消费或提现为荣誉。这样积分才是活的。
2. **消费用 `balance`，等级用 `total_earned`**。等级不能因为消费而掉，否则用户不敢花。
3. **每次变动都要落 `points_log`**（含 `type` / `ref_type` / `ref_id`），保证可审计、可回滚、可幂等。
4. **扣减必须原子**（`UPDATE ... SET balance = GREATEST(balance - ?, 0)`），本仓库已提供 `PointsAccountMapper#incrementAtomically`。

---

## 三、五个消耗出口（按性价比排序）

### ① 积分解锁「付费笔记」 —— 最推荐，字段几乎齐备

`note.locked` 与 `@TableField(exist=false)` 的遮蔽机制已经存在（当前只用于星球成员校验），扩展成本最低。

- 新增 `note.price`（`int`，默认 `0` = 免费），作者发布时可选设置，上限由等级决定。
- 详情接口：`locked && 未购买` → 只返回 `summary` + 正文遮蔽，`locked=true`。
- 新增 `POST /note/unlock/{id}`：
  1. 校验余额 ≥ price（不足直接拒绝）
  2. 原子扣减：`points_log(-price, type='unlock', ref_type='note', ref_id=id)`
  3. 写 `note_purchase(user_id, note_id, price, create_time)`（唯一键 `(user_id, note_id)` 保证幂等）
  4. 按比例给作者加分（建议作者 90%、平台留 10% 作为「系统账户」入账，便于统计）
- 已购买用户再次访问直接放行（查 `note_purchase`）。

> 为什么最合适：读者付费意愿最强的就是「这篇笔记正好解决我的问题」；作者获得直接回报；平台抽成有据可查。

### ② 打赏作者 —— 社交属性最强，能提升内容质量

- `POST /points/tip` `{ noteId, amount }`：扣读者 `balance`，给作者加分（同样 9:1 分成）。
- 笔记详情页展示「最近打赏」与累计打赏额；作者个人主页展示「收到打赏」总数。
- 打赏门槛建议 ≥ 1 分、单日上限（防刷）。

> 与 ① 的区别：① 是「买内容」，② 是「表达认可」，成本更低、频次更高，适合作为入门级消耗。

### ③ 积分置顶笔记

- `note.is_top` 已存在，但目前只有管理端能免费置顶。
- 新增自助置顶：消耗固定积分（如 50）置顶 24 小时；新增 `note.top_until`（`datetime`）。
- 定时任务清理 `top_until < now()` 的记录；列表排序已是 `is_top DESC`，无需改动。

> 适合作为「积分回收池」：高频、低价值，能有效消耗通胀。

### ④ 下载资源消耗积分

- 资源表加 `price`（`0` = 免费）；`POST /resource/download/{id}` 前校验并扣减。
- 顺带解决一个既有缺陷：**下载计数目前可被刷新无限刷**。改为「本人每资源只计一次」，写入 `resource_download(user_id, resource_id)`（唯一键），计数只在该表首次插入时 +1。

### ⑤ 创建星球 / 加入门槛

- 创建星球消耗一笔较大积分（如 500），或要求「累计获得 ≥ 1000」作为申请资格。
- 作用是把积分变成**活跃度与贡献度的证明**，而不是单纯的货币。

---

## 四、等级体系：让积分「有意义」而不只是「能花」

用 `total_earned` 分级（消费不掉级），建议 5 档：

| 等级 | 门槛（累计获得） | 解锁 |
|---|---|---|
| L1 新手 | 0 | 发布笔记 |
| L2 活跃 | 100 | 可设置笔记售价（上限 10） |
| L3 贡献 | 500 | 每日 +1 次免费置顶、可创建专栏 |
| L4 资深 | 2000 | 笔记售价上限 50、专属徽章 |
| L5 核心 | 10000 | 可申请创建星球、主页展示标识 |

等级建议展示在：个人主页、评论区昵称旁、笔记作者卡片。

---

## 五、防滥用（必须配套）

| 风险 | 对策 |
|---|---|
| 自赞刷分 | 作者给自己点赞/评论不产生积分（当前 `likeNote` 会给作者 +2，**未排除自赞** → 应加 `if (!authorId.equals(likeUserId))`） |
| 反复发布刷分 | 已修复：发布积分以 `points_log` 是否存在该笔记 `publish` 记录做幂等 |
| 高并发扣成负数 | 已提供原子 upsert，且 `GREATEST(balance + change, 0)` 兜底 |
| 刷解锁 | `note_purchase` 唯一键 + 先查已购直接放行 |
| 每日无限刷签到/点赞 | 增加「每日获取上限」（如 200 分），在 `addPoints` 前汇总当日正向流水判断 |
| 洗积分 | 打赏/解锁的积分让作者获得的是「累计获得」权重更高的等级分，抽取 10% 沉淀，抑制互刷收益 |

---

## 六、落地优先级建议

| 优先级 | 内容 | 理由 |
|---|---|---|
| **P0** | 付费笔记解锁 + 打赏 | 直接形成闭环，字段基本齐备；同时补上「自赞不积分」这个既有漏洞 |
| **P1** | 等级体系展示 | 成本低、观感提升明显，让积分从「能花」变成「有价值」 |
| **P2** | 自助置顶、资源付费下载 | 作为积分回收池，并顺带修掉下载计数可刷的问题 |
| **P3** | 创建星球门槛 | 与星球体系耦合较深，放最后 |

---

## 七、需要注意的既有事项

- `note.locked` 是 `@TableField(exist = false)`，**数据库没有 `locked` 列**。若要让作者能持久化「该笔记可付费」，需要新增真实列（例如 `price`），并把现有「星球遮蔽」与「付费锁定」两个语义用同一个 `locked` 字段合流，前端遮罩组件（`NoteDetail.vue` 的 `.star-lock-banner`）可直接复用。
- 积分扣减入口统一走 `PointsService`，不要直接操作 `points_account`，否则会漏掉流水。
- 若引入「平台抽成」，建议给系统账户一个固定 `user_id`（如 `0`）并在统计中排除，避免污染用户榜单。

---

## 八、实现状态（2026-10-16）

已按本文 **P0 + P1** 落地，P2/P3 待办。

### 已完成

| 项 | 实现 |
|---|---|
| **迁移脚本** | `V3__remove_comment_moderation_and_add_points_spend.sql`：`note.price` 列、`note_purchase`、`note_tip` 两张表；`comment.status` 默认值改为 `approved`，历史 `pending/rejected` 统一放行 |
| **① 付费笔记解锁** | `PointsService#unlockNote`：原子扣减 → 写 `note_purchase`（唯一键幂等，冲突则退回）→ 作者得 90%，写 `unlock_income` 流水。`GET /note/{id}` 未解锁时返回 `content=null + locked=true + price`，前端复用星球遮罩展示「用 N 积分解锁」 |
| **② 打赏作者** | `PointsService#tipNote`：单次 1~500，禁止自赏，作者得 90%，写 `note_tip` 与 `tip_income` 流水；详情页为他人笔记展示「打赏」下拉（1/5/10/50） |
| **等级体系** | 前端 `demo1/src/utils/pointsLevel.js`：`getLevelInfo(totalEarned)`，5 档；个人主页签到卡片展示 `Lv.N 名称` 与升级所需累计积分 |
| **防滥用（自赞）** | `NoteServiceImpl#likeNote` 排除作者本人，自赞不再 +2 积分 |
| **发布/编辑定价** | `NotePublish.vue` 增加「售价」输入（0 ~ **当前等级上限**，见 P4），`createNote`/`updateNote` 经 `normalizeNotePrice` 校验 |

### 实测结果（8081 实例）

| 用例 | 结果 |
|---|---|
| A 发布付费笔记（price=5） | A +20 积分 ✅ |
| B 未解锁访问详情 | `locked=true`、`content=null`、`price=5` ✅ |
| B 积分不足时解锁 | 拒绝：「积分不足，还差 5 分」✅ |
| B 解锁后 | 正文可取；B −5、**A +4（90%）** ✅ |
| B 打赏 10 积分 | B −10、**A +9（90%）** ✅ |
| 打赏自己 / 金额 999 | 均被拒 ✅ |
| 账户核对 | A：20+4+9=33；B：100−5−10+1=86 ✅ |

### 追加完成（P2 / P3，2026-10-16 同日）

| 项 | 实现 |
|---|---|
| **P2-1 自助置顶** | `note.top_until` + `POST /note/boost/{id}`：消耗 `BOOST_COST`（50）积分置顶 `BOOST_HOURS`（24）小时，仅作者本人、仅已发布笔记；`ExpireTopNoteTask` 每 10 分钟批量到期下架（`is_top=0, top_until=NULL`）并清 `noteList` 缓存。管理员置顶不带 `top_until`，不受影响 |
| **P2-2 资源付费下载** | `resource.price` + `resource_download`（唯一键 `user_id+resource_id`）。`PointsService#unlockResource` 统一处理免费/付费：付费扣分、上传者得 90%；**首次产生记录才 `download_count +1`**，顺带修掉「刷新即可刷爆下载数」。下载接口改为需登录（匿名仅可下免费资源且不计数） |
| **P3 创建星球门槛** | `POST /star/create` 消耗 `CREATE_STAR_COST`（500）积分，配合 `@Transactional` 保证不留半成品星球 |
| **实测** | 置顶 `is_top=1 / top_until=+24h`；模拟到期执行同款 UPDATE → `is_top=0 / top_until=NULL`；资源两次下载**仅计 1 次**、扣 10 上传者得 9；余额 90 创建星球被拒（还差 410）、充值 600 后成功并扣 500 |

### 追加完成（P4：额度与对账，2026-10-16）

| 项 | 实现 |
|---|---|
| **每日获取上限** | `PointsService.DAILY_EARN_LIMIT = 200`，在 `addPointsInternal` 统一拦截：只约束**平台发放**类型（`EARN_TYPES = sign/publish/like/comment`），按剩余额度**截断发放**并在 `remark` 标注「今日获取上限，实发 N 分」；额度用尽则直接跳过（**不写流水**）。**用户间转移（`unlock_income`/`tip_income`/`resource_income`）不计入、不受限** —— 那是别人花掉的积分转过来，限流会让作者收不到应有报酬 |
| **售价上限随等级** | 新增后端权威枚举 `model/enums/PointsLevel.java`（L1 新手 0 / L2 活跃 50 / L3 贡献 100 / L4 资深 200 / L5 核心 500，按 `total_earned` 定级）。`NoteServiceImpl#normalizeNotePrice` 与 `ResourceServiceImpl#normalizePrice` 均改为按 `pointsService.getPriceLimit(userId)` 校验；资源由原来的静默 `Math.min(price, 200)` **改为直接报错**（用户填 200 实际只生效 50 却毫无提示，是隐患）。前端 `pointsLevel.js` 的 `min`/`priceLimit` 与枚举严格对齐，发布页按 `GET /points/quota` 下发值渲染提示 |
| **对账视图** | `GET /api/points/reconcile?days=30`（`@AdminRequired`）：`PointsReconcileMapper`（注解式聚合 SQL）+ `PointsReconcileServiceImpl`。含总览（账户数 / 未消费存量 / 累计发放 / 累计消耗 / 平台抽成 / 成交单数 / 今日已发放）、抽成**分渠道**（笔记解锁 / 笔记打赏 / 资源下载）、近 N 天趋势（发放 / 消耗 / 抽成合并到同一日期轴）、收入榜与消费榜。管理端页面 `Admin/PointsReconcile.vue`，图表用纯 CSS 条形图，**未引入 ECharts**（避免为一个页面加 1MB 依赖） |
| **对账口径** | 「发放」只算平台发放类型（不含用户间转移，否则重复计数虚高）；「消耗」按 `SPEND_TYPES` 白名单（排除 `resource_refund` 这类退回正数）；「平台抽成」由三张成交表反推 `SUM(成交额 − 收款方实得)`，无需额外账目表 |
| **迁移 V5** | 为对账查询补 4 个 `idx_create_time`（`points_log` / `note_purchase` / `note_tip` / `resource_download`）—— 对账只按时间过滤，原索引均以 `user_id` / `note_id` 为前缀，命不中 |
| **额度查询接口** | `GET /api/points/quota`：返回 `level / levelName / totalEarned / balance / priceLimit / nextLevel... / dailyEarnLimit / todayEarned / todayRemain`，发布页与个人主页共用 |

### 实测结果（8081 实例，Flyway v4 → v5）

| 用例 | 结果 |
|---|---|
| L1 新手提交 `price=5` | 拒绝：「当前等级暂不能设置售价，可通过签到、发布、互动累积积分提升等级」✅ |
| 提到 L2（`total_earned=100`）后查 `/points/quota` | `level=2(活跃) priceLimit=50`、`nextLevel=3 需 500`、每日上限 200 ✅ |
| L2 提交 `price=50` / `price=51` | 成功（笔记 id=32）/ 拒绝「当前等级售价上限为 50 积分」✅ |
| 资源同规则 `price=50` / `price=51` | 成功（id=18）/ 拒绝 ✅ |
| B 解锁 50 + 打赏 10 + 下载 50 | A 实得 45 + 9 + 45；抽成 5 + 1 + 5 = **11**，成交 **3** 单 ✅ |
| `GET /points/reconcile` | 分渠道 5/1/5、趋势「10-16 发放 40 消耗 110 抽成 11」、收入榜「P4作者 99」、消费榜「P4读者 110」✅ |
| 剩余 1 分时签到（本应发 6 分） | **实发 1 分**，`balance 289→290`，`remark` 带「今日获取上限，实发 1 分」✅ |
| 额度已满时发评论 | 评论发布成功（`code=0`），但 `points_log` 条数 **5→5 不变**（不写流水）✅ |
| 用户间转移是否占额度 | A 的 `todayEarned=40`（两次发布各 20），而 `balance=139`（含 99 收入）→ **income 不计入额度** ✅ |

### 备注

- 文档第七节提到的「建议给系统账户固定 `user_id`」**未采用**：平台抽成部分目前**不落任何账户**，只在 `points_log.remark` 中标注「平台抽成 10%」，对账时按 `price - author_gain` 汇总即可，避免多出一个需要特殊排除的账户。
- `note.locked` 仍是 `@TableField(exist = false)`（数据库无此列），继续作为「内容被遮蔽」的统一标记，同时服务星球门禁与付费解锁两种场景。

### 实现这两个功能时踩的两个坑（值得记住）

1. **`change` 是 MySQL 保留字**。用 `QueryWrapper.gt("change", 0)` 拼出的是 `change > 0`，直接语法错误；
   必须写 `gt("\`change\`", 0)`。更隐蔽的是它发生在 `addPoints` 的 `try/catch` 里 ——
   **异常被吞掉 → 所有积分发放静默失效**（笔记照发、积分不发），日志里只有一行 `WARN 加分失败`。
   已把该 `catch` 改为传 Throwable 打完整堆栈，便于以后一眼定位。
2. **`only_full_group_by` 要求 SELECT 与 GROUP BY 的分组表达式完全一致**。
   `SELECT DATE_FORMAT(create_time,'%Y-%m-%d') ... GROUP BY DATE(create_time)` 会被判为
   「非聚合列未参与分组」直接报错，必须两边都写 `DATE_FORMAT(create_time,'%Y-%m-%d')`。
   （注意：对 **GROUP BY 中的列**再套函数是合法的，如 `GROUP BY d` 而 SELECT `DATE_FORMAT(d,...)`。）

