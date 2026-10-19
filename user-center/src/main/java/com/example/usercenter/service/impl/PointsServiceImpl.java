package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.NotePurchaseMapper;
import com.example.usercenter.mapper.NoteTipMapper;
import com.example.usercenter.mapper.PointsAccountMapper;
import com.example.usercenter.mapper.PointsLogMapper;
import com.example.usercenter.mapper.ResourceDownloadMapper;
import com.example.usercenter.mapper.ResourceMapper;
import com.example.usercenter.mapper.SignInRecordMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.NotePurchase;
import com.example.usercenter.model.domain.NoteTip;
import com.example.usercenter.model.domain.PointsAccount;
import com.example.usercenter.model.domain.PointsLog;
import com.example.usercenter.model.domain.ResourceDownload;
import com.example.usercenter.model.domain.SignInRecord;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.enums.PointsLevel;
import com.example.usercenter.service.PointsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 积分服务实现：签到 + 积分账户 + 流水
 */
@Service
@Slf4j
public class PointsServiceImpl implements PointsService {

    @Resource
    private PointsAccountMapper pointsAccountMapper;
    @Resource
    private PointsLogMapper pointsLogMapper;
    @Resource
    private SignInRecordMapper signInRecordMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private NoteMapper noteMapper;
    @Resource
    private NotePurchaseMapper notePurchaseMapper;
    @Resource
    private NoteTipMapper noteTipMapper;
    @Resource
    private ResourceMapper resourceMapper;
    @Resource
    private ResourceDownloadMapper resourceDownloadMapper;

    /** 平台抽成比例（%）：作者实得 = 成交额 × (100 - 该值) / 100 */
    private static final int PLATFORM_FEE_PERCENT = 10;

    /** 单次打赏金额上下限 */
    private static final int TIP_MIN = 1;
    private static final int TIP_MAX = 500;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean signIn(Long userId) {
        Date today = truncateToDate(new Date());
        // 今日是否已签
        Long cnt = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId).eq(SignInRecord::getSignDate, today));
        if (cnt != null && cnt > 0) return false;
        // 查昨日记录算连续天数
        Date yesterday = addDays(today, -1);
        SignInRecord yRec = signInRecordMapper.selectOne(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId).eq(SignInRecord::getSignDate, yesterday));
        int continuous = (yRec != null && yRec.getContinuousDays() != null) ? yRec.getContinuousDays() + 1 : 1;
        // 写签到记录
        SignInRecord rec = new SignInRecord();
        rec.setUserId(userId);
        rec.setSignDate(today);
        rec.setContinuousDays(continuous);
        rec.setCreateTime(new Date());
        signInRecordMapper.insert(rec);
        // 加分：基础 5 + 连续加成（上限 +20）
        int bonus = Math.min(continuous, 20);
        int change = 5 + bonus;
        addPointsInternal(userId, change, "sign", null, null, "签到（连续第" + continuous + "天）");
        return true;
    }

    @Override
    public PointsAccount getAccount(Long userId) {
        return pointsAccountMapper.selectById(userId);
    }

    @Override
    public boolean isSignedToday(Long userId) {
        Date today = truncateToDate(new Date());
        Long cnt = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId).eq(SignInRecord::getSignDate, today));
        return cnt != null && cnt > 0;
    }

    @Override
    public PageResult<PointsLog> getPointsLog(Long userId, int page, int pageSize) {
        IPage<PointsLog> p = pointsLogMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<PointsLog>()
                        .eq(PointsLog::getUserId, userId)
                        .orderByDesc(PointsLog::getCreateTime));
        return new PageResult<>(p.getTotal(), page, pageSize, p.getRecords());
    }

    @Override
    public List<SignInRecord> getSignCalendar(Long userId, String month) {
        Date[] range = parseMonthRange(month);
        return signInRecordMapper.selectList(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId)
                .ge(SignInRecord::getSignDate, range[0])
                .lt(SignInRecord::getSignDate, range[1])
                .orderByAsc(SignInRecord::getSignDate));
    }

    @Override
    public void addPoints(Long userId, int change, String type, String refType, Long refId, String remark) {
        try {
            addPointsInternal(userId, change, type, refType, refId, remark);
        } catch (Exception e) {
            // 传 Throwable（末位参数）以打印完整堆栈：此前只打 e.getMessage()，
            // 一旦 SQL 出错会得到一段多行文本，藏在启动日志中段难以定位。
            log.warn("加分失败 userId={} type={}", userId, type, e);
        }
    }

    /** 实际加分：写流水 + 累加账户 */
    private void addPointsInternal(Long userId, int change, String type, String refType, Long refId, String remark) {
        // 【每日获取上限】只约束「平台发放」（签到/发布/被赞/评论）。
        // 用户间转移（unlock_income / tip_income / resource_income）不受限 ——
        // 那是别人花掉的积分转过来，不是增发，限流会导致作者收不到报酬。
        if (change > 0 && DAILY_EARN_LIMIT > 0 && EARN_TYPES.contains(type)) {
            int remain = DAILY_EARN_LIMIT - getTodayEarned(userId);
            if (remain <= 0) {
                log.info("用户 {} 今日获取积分已达上限 {}，本次不再发放（type={}, 应发 {}）",
                        userId, DAILY_EARN_LIMIT, type, change);
                return;
            }
            if (change > remain) {
                log.info("用户 {} 今日获取积分仅剩 {} 分，由 {} 截断发放（type={}）",
                        userId, remain, change, type);
                remark = (remark == null ? "" : remark) + "（今日获取上限，实发 " + remain + " 分）";
                change = remain;
            }
        }

        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChange(change);
        log.setType(type);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setRemark(remark);
        log.setCreateTime(new Date());
        pointsLogMapper.insert(log);

        // 【修复】改用原子 upsert 累加。
        // 原实现是「selectById → 内存里 balance + change → updateById」的读-改-写，
        // 在并发加分（点赞/评论/发布同时发生）时两个请求会读到同一个旧值 → 丢失更新；
        // 且负数变更没有任何下限保护，余额可能被刷成负数。
        pointsAccountMapper.incrementAtomically(userId, change);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateFromContribution() {
        List<User> users = userMapper.selectList(null);
        int count = 0;
        for (User u : users) {
            if (pointsAccountMapper.selectById(u.getId()) != null) continue; // 幂等
            long contribution = calcContribution(u.getId());
            PointsAccount acc = new PointsAccount();
            acc.setUserId(u.getId());
            int bal = (int) Math.min(contribution, Integer.MAX_VALUE);
            acc.setBalance(bal);
            acc.setTotalEarned(bal);
            acc.setUpdateTime(new Date());
            try {
                pointsAccountMapper.insert(acc);
                count++;
            } catch (Exception e) { /* 并发跳过 */ }
        }
        log.info("积分账户迁移完成，共 {} 个用户", count);
        return count;
    }

    /** 复用 getUserGrowth 算法：发布*10 + 获赞*2 + 评论 */
    private long calcContribution(Long userId) {
        QueryWrapper<Note> pw = new QueryWrapper<>();
        pw.eq("author_id", userId).eq("status", "published").eq("is_delete", 0);
        long publishCount = noteMapper.selectCount(pw);

        QueryWrapper<Note> lw = new QueryWrapper<>();
        lw.eq("author_id", userId).eq("status", "published").eq("is_delete", 0)
                .select("COALESCE(SUM(like_count),0) as total");
        long totalLikes = toLong(noteMapper.selectObjs(lw).stream().filter(Objects::nonNull).findFirst().orElse(0));

        QueryWrapper<Note> cw = new QueryWrapper<>();
        cw.eq("author_id", userId).eq("status", "published").eq("is_delete", 0)
                .select("COALESCE(SUM(comment_count),0) as total");
        long totalComments = toLong(noteMapper.selectObjs(cw).stream().filter(Objects::nonNull).findFirst().orElse(0));

        return publishCount * 10 + totalLikes * 2 + totalComments;
    }

    private int nvl(Integer v) { return v == null ? 0 : v; }

    private long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0; }
    }

    private Date truncateToDate(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private Date addDays(Date d, int n) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.add(Calendar.DAY_OF_MONTH, n);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private Date[] parseMonthRange(String month) {
        Calendar c = Calendar.getInstance();
        if (month != null && month.matches("\\d{4}-\\d{2}")) {
            int y = Integer.parseInt(month.substring(0, 4));
            int m = Integer.parseInt(month.substring(5, 7)) - 1;
            c.set(y, m, 1, 0, 0, 0);
        } else {
            c.set(Calendar.DAY_OF_MONTH, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
        }
        c.set(Calendar.MILLISECOND, 0);
        Date start = c.getTime();
        c.add(Calendar.MONTH, 1);
        return new Date[]{start, c.getTime()};
    }

    // ==================== 积分消耗：付费笔记解锁 / 打赏 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockNote(Long userId, Long noteId) {
        Note note = requireNote(noteId);
        int price = note.getPrice() == null ? 0 : note.getPrice();
        if (price <= 0) {
            return true; // 免费笔记，无需解锁
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        if (userId.equals(note.getAuthorId())) {
            return true; // 作者本人始终可见
        }
        if (hasUnlocked(userId, noteId)) {
            return true; // 已购买，幂等
        }

        int balance = currentBalance(userId);
        if (balance < price) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "积分不足，还差 " + (price - balance) + " 分");
        }

        // 原子扣减（GREATEST 兜底，不会扣成负数）
        pointsAccountMapper.incrementAtomically(userId, -price);
        insertLog(userId, -price, "unlock", "note", noteId, "解锁笔记《" + note.getTitle() + "》");

        int authorGain = price * (100 - PLATFORM_FEE_PERCENT) / 100;
        NotePurchase p = new NotePurchase();
        p.setUserId(userId);
        p.setNoteId(noteId);
        p.setAuthorId(note.getAuthorId());
        p.setPrice(price);
        p.setAuthorGain(authorGain);
        p.setCreateTime(new Date());
        try {
            notePurchaseMapper.insert(p);
        } catch (DuplicateKeyException e) {
            // 并发下另一个请求已完成购买：把本次扣款退回，保持幂等
            pointsAccountMapper.incrementAtomically(userId, price);
            insertLog(userId, price, "unlock_refund", "note", noteId, "重复解锁退回");
            return true;
        }

        if (note.getAuthorId() != null && authorGain > 0) {
            pointsAccountMapper.incrementAtomically(note.getAuthorId(), authorGain);
            insertLog(note.getAuthorId(), authorGain, "unlock_income", "note", noteId,
                    "笔记被解锁《" + note.getTitle() + "》（平台抽成 " + PLATFORM_FEE_PERCENT + "%）");
        }
        log.info("笔记解锁成功: userId={}, noteId={}, price={}", userId, noteId, price);
        return true;
    }

    @Override
    public boolean hasUnlocked(Long userId, Long noteId) {
        if (userId == null || noteId == null) {
            return false;
        }
        Long cnt = notePurchaseMapper.selectCount(new LambdaQueryWrapper<NotePurchase>()
                .eq(NotePurchase::getUserId, userId)
                .eq(NotePurchase::getNoteId, noteId));
        return cnt != null && cnt > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean tipNote(Long userId, Long noteId, int amount) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        if (amount < TIP_MIN || amount > TIP_MAX) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "单次打赏需在 " + TIP_MIN + " ~ " + TIP_MAX + " 积分之间");
        }
        Note note = requireNote(noteId);
        if (userId.equals(note.getAuthorId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能打赏自己的笔记");
        }

        int balance = currentBalance(userId);
        if (balance < amount) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "积分不足，还差 " + (amount - balance) + " 分");
        }

        pointsAccountMapper.incrementAtomically(userId, -amount);
        insertLog(userId, -amount, "tip", "note", noteId, "打赏《" + note.getTitle() + "》");

        int authorGain = amount * (100 - PLATFORM_FEE_PERCENT) / 100;
        NoteTip t = new NoteTip();
        t.setUserId(userId);
        t.setNoteId(noteId);
        t.setAuthorId(note.getAuthorId());
        t.setAmount(amount);
        t.setAuthorGain(authorGain);
        t.setCreateTime(new Date());
        noteTipMapper.insert(t);

        if (note.getAuthorId() != null && authorGain > 0) {
            pointsAccountMapper.incrementAtomically(note.getAuthorId(), authorGain);
            insertLog(note.getAuthorId(), authorGain, "tip_income", "note", noteId,
                    "收到打赏《" + note.getTitle() + "》（平台抽成 " + PLATFORM_FEE_PERCENT + "%）");
        }
        log.info("打赏成功: userId={}, noteId={}, amount={}", userId, noteId, amount);
        return true;
    }

    // ------------------------- 私有工具 -------------------------

    private Note requireNote(Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID不合法");
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getIsDelete() != null && note.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        return note;
    }

    /** 今日已获得的积分（仅计平台发放类型；用户间转移不计入） */
    @Override
    public int getTodayEarned(Long userId) {
        QueryWrapper<PointsLog> qw = new QueryWrapper<>();
        // ⚠️ `change` 是 MySQL 保留字，QueryWrapper 不会自动加反引号 ——
        // 写成 "change" 会拼出 `change > 0` 这样的非法 SQL，直接抛语法错误。
        qw.eq("user_id", userId)
                .gt("`change`", 0)
                .in("type", EARN_TYPES)
                .ge("create_time", todayStart());
        qw.select("COALESCE(SUM(`change`), 0) AS total");
        return (int) toLong(pointsLogMapper.selectObjs(qw).stream()
                .filter(Objects::nonNull).findFirst().orElse(0));
    }

    /** 今日零点（与 DB 的 CURDATE() 同机同时区） */
    private Date todayStart() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    @Override
    public int getPriceLimit(Long userId) {
        return levelOf(userId).getPriceLimit();
    }

    /** 按累计获得积分取等级（无账户视为 L1） */
    private PointsLevel levelOf(Long userId) {
        PointsAccount acc = userId == null ? null : pointsAccountMapper.selectById(userId);
        return PointsLevel.of(acc == null ? 0 : acc.getTotalEarned());
    }

    private int currentBalance(Long userId) {
        PointsAccount acc = pointsAccountMapper.selectById(userId);
        return acc == null || acc.getBalance() == null ? 0 : acc.getBalance();
    }

    private void insertLog(Long userId, int change, String type, String refType, Long refId, String remark) {
        PointsLog entry = new PointsLog();
        entry.setUserId(userId);
        entry.setChange(change);
        entry.setType(type);
        entry.setRefType(refType);
        entry.setRefId(refId);
        entry.setRemark(remark);
        entry.setCreateTime(new Date());
        pointsLogMapper.insert(entry);
    }

    // ==================== 通用消费 / 资源解锁 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int spend(Long userId, int amount, String type, String refType, Long refId, String remark) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消费积分必须大于 0");
        }
        int balance = currentBalance(userId);
        if (balance < amount) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "积分不足，还差 " + (amount - balance) + " 分");
        }
        pointsAccountMapper.incrementAtomically(userId, -amount);
        insertLog(userId, -amount, type, refType, refId, remark);
        return balance - amount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockResource(Long userId, Long resourceId) {
        if (resourceId == null || resourceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不合法");
        }
        // 注意：实体 Resource 与注解 jakarta.annotation.Resource 简名冲突，
        // 这里必须用全限定名
        com.example.usercenter.model.domain.Resource r = resourceMapper.selectById(resourceId);
        if (r == null || (r.getIsDelete() != null && r.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源不存在");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN, "请先登录");
        }
        // 已有下载记录（含免费资源重复下载）→ 直接放行，且不重复计数
        if (hasPurchasedResource(userId, resourceId)) {
            return true;
        }

        int price = r.getPrice() == null ? 0 : r.getPrice();
        String title = r.getTitle() != null ? r.getTitle() : r.getName();
        boolean isUploader = userId.equals(r.getUploaderId());
        int uploaderGain = 0;

        // 付费资源且非上传者本人：先校验余额并扣分
        if (price > 0 && !isUploader) {
            int balance = currentBalance(userId);
            if (balance < price) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "积分不足，还差 " + (price - balance) + " 分");
            }
            pointsAccountMapper.incrementAtomically(userId, -price);
            insertLog(userId, -price, "resource_download", "resource", resourceId, "下载资源《" + title + "》");
            uploaderGain = price * (100 - PLATFORM_FEE_PERCENT) / 100;
        }

        ResourceDownload rd = new ResourceDownload();
        rd.setUserId(userId);
        rd.setResourceId(resourceId);
        rd.setUploaderId(r.getUploaderId());
        rd.setPrice(isUploader ? 0 : price);
        rd.setUploaderGain(uploaderGain);
        rd.setCreateTime(new Date());
        try {
            resourceDownloadMapper.insert(rd);
        } catch (DuplicateKeyException e) {
            // 并发下已被另一次请求解锁：退回本次扣款，保持幂等
            if (!isUploader && price > 0) {
                pointsAccountMapper.incrementAtomically(userId, price);
                insertLog(userId, price, "resource_refund", "resource", resourceId, "重复下载退回");
            }
            return true;
        }

        // 首次下载：下载数 +1（唯一键保证同一用户只计一次，刷新无法刷爆）
        resourceMapper.incrementDownloadCount(resourceId);

        if (!isUploader && uploaderGain > 0 && r.getUploaderId() != null) {
            pointsAccountMapper.incrementAtomically(r.getUploaderId(), uploaderGain);
            insertLog(r.getUploaderId(), uploaderGain, "resource_income", "resource", resourceId,
                    "资源被下载《" + title + "》（平台抽成 " + PLATFORM_FEE_PERCENT + "%）");
        }
        return true;
    }

    @Override
    public boolean hasPurchasedResource(Long userId, Long resourceId) {
        if (userId == null || resourceId == null) {
            return false;
        }
        Long cnt = resourceDownloadMapper.selectCount(new LambdaQueryWrapper<ResourceDownload>()
                .eq(ResourceDownload::getUserId, userId)
                .eq(ResourceDownload::getResourceId, resourceId));
        return cnt != null && cnt > 0;
    }
}
