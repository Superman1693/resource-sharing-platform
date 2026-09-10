package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.PointsAccountMapper;
import com.example.usercenter.mapper.PointsLogMapper;
import com.example.usercenter.mapper.SignInRecordMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.PointsAccount;
import com.example.usercenter.model.domain.PointsLog;
import com.example.usercenter.model.domain.SignInRecord;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.PointsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
            log.warn("加分失败 userId={} type={}: {}", userId, type, e.getMessage());
        }
    }

    /** 实际加分：写流水 + 累加账户（account upsert，并发兜底） */
    private void addPointsInternal(Long userId, int change, String type, String refType, Long refId, String remark) {
        PointsLog log = new PointsLog();
        log.setUserId(userId);
        log.setChange(change);
        log.setType(type);
        log.setRefType(refType);
        log.setRefId(refId);
        log.setRemark(remark);
        log.setCreateTime(new Date());
        pointsLogMapper.insert(log);

        PointsAccount acc = pointsAccountMapper.selectById(userId);
        if (acc == null) {
            acc = new PointsAccount();
            acc.setUserId(userId);
            acc.setBalance(change);
            acc.setTotalEarned(change);
            acc.setUpdateTime(new Date());
            try {
                pointsAccountMapper.insert(acc);
            } catch (Exception e) {
                acc = pointsAccountMapper.selectById(userId);
                if (acc != null) {
                    acc.setBalance(nvl(acc.getBalance()) + change);
                    acc.setTotalEarned(nvl(acc.getTotalEarned()) + change);
                    acc.setUpdateTime(new Date());
                    pointsAccountMapper.updateById(acc);
                }
            }
        } else {
            acc.setBalance(nvl(acc.getBalance()) + change);
            acc.setTotalEarned(nvl(acc.getTotalEarned()) + change);
            acc.setUpdateTime(new Date());
            pointsAccountMapper.updateById(acc);
        }
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
}
