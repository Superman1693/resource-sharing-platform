package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.*;
import com.example.usercenter.model.domain.Comment;
import com.example.usercenter.model.domain.LikeRecord;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.Report;
import com.example.usercenter.model.domain.Stats;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.StatsService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计数据接口
 * 
 * @author zy
 */
@RestController
@RequestMapping("/stats")
@Slf4j
@Tag(name = "统计接口")
public class StatsController extends BaseController {

    @Resource
    private StatsService statsService;

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private ResourceMapper resourceMapper;

    @Resource
    private CommentMapper commentMapper;

    @Resource
    private LikeRecordMapper likeRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ReportMapper reportMapper;

    /**
     * 平台数据总览（管理端首页）
     */
    @GetMapping("/overview")
    @Operation(summary = "平台数据总览（总用户/总笔记/总评论/总资源/今日新增笔记/待审核举报数）")
    public BaseResponse<Map<String, Object>> getOverview() {
        requireAdmin();
        Map<String, Object> result = new HashMap<>();

        // 总用户数（@TableLogic 自动过滤已删除）
        result.put("totalUsers", userMapper.selectCount(null));

        // 已发布笔记数
        QueryWrapper<Note> publishedNoteQuery = new QueryWrapper<>();
        publishedNoteQuery.eq("status", "published");
        result.put("totalNotes", noteMapper.selectCount(publishedNoteQuery));

        // 总评论数
        result.put("totalComments", commentMapper.selectCount(null));

        // 总资源数
        result.put("totalResources", resourceMapper.selectCount(null));

        // 今日新增笔记数（publish_time >= 今日 0 点）
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        QueryWrapper<Note> todayNoteQuery = new QueryWrapper<>();
        todayNoteQuery.ge("publish_time", cal.getTime());
        result.put("todayNewNotes", noteMapper.selectCount(todayNoteQuery));

        // 待审核举报数
        QueryWrapper<Report> pendingReportQuery = new QueryWrapper<>();
        pendingReportQuery.eq("status", "pending");
        result.put("pendingReports", reportMapper.selectCount(pendingReportQuery));

        return ResultUtils.success(result);
    }

    /**
     * 获取数据统计概览
     */
    @GetMapping("/data")
    public BaseResponse<Stats> getDataStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        requireAdmin();
        Stats stats = statsService.getDataStats(startDate, endDate, request);
        return ResultUtils.success(stats);
    }

    /**
     * 获取用户统计数据
     */
    @GetMapping("/user")
    public BaseResponse<Stats> getUserStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String source,
            HttpServletRequest request) {
        requireAdmin();
        Stats stats = statsService.getUserStats(startDate, endDate, source, request);
        return ResultUtils.success(stats);
    }

    /**
     * 获取当前用户个人统计数据
     */
    @GetMapping("/personal")
    @LoginRequired
    @Operation(summary = "获取用户个人统计数据")
    public BaseResponse<Map<String, Object>> getPersonalStats() {
        Long userId = UserContext.get().getUserId();

        // 查询用户发布的笔记数
        QueryWrapper<Note> noteQuery = new QueryWrapper<>();
        noteQuery.eq("author_id", userId).eq("status", "published");
        long noteCount = noteMapper.selectCount(noteQuery);

        // 查询用户的评论数
        QueryWrapper<Comment> commentQuery = new QueryWrapper<>();
        commentQuery.eq("user_id", userId);
        long commentCount = commentMapper.selectCount(commentQuery);

        // 查询用户上传的资源数（Resource 与 jakarta.annotation.Resource 简单名冲突，使用全限定名）
        QueryWrapper<com.example.usercenter.model.domain.Resource> resourceQuery = new QueryWrapper<>();
        resourceQuery.eq("uploader_id", userId);
        long resourceCount = resourceMapper.selectCount(resourceQuery);

        // 查询用户收到的点赞数（笔记被点赞）
        QueryWrapper<LikeRecord> likeQuery = new QueryWrapper<>();
        likeQuery.eq("target_type", "note").inSql("target_id",
                "SELECT id FROM note WHERE author_id = " + userId + " AND is_delete = 0");
        long likeCount = likeRecordMapper.selectCount(likeQuery);

        Map<String, Object> stats = new HashMap<>();
        stats.put("noteCount", noteCount);
        stats.put("resourceCount", resourceCount);
        stats.put("commentCount", commentCount);
        stats.put("likeCount", likeCount);
        return ResultUtils.success(stats);
    }

    /**
     * 获取当前用户收到的点赞列表（分页，"我的获赞"专属页）
     */
    @GetMapping("/myLikes")
    @LoginRequired
    @Operation(summary = "获取当前用户收到的点赞列表")
    public BaseResponse<PageResult<Map<String, Object>>> getMyLikes(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }
        Long userId = UserContext.get().getUserId();

        // 分页查询当前用户笔记收到的点赞（子查询限定被赞笔记属于当前用户）
        Page<LikeRecord> pageParam = new Page<>(page, pageSize);
        QueryWrapper<LikeRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("target_type", "note")
                .inSql("target_id", "SELECT id FROM note WHERE author_id = " + userId + " AND is_delete = 0")
                .orderByDesc("create_time");
        IPage<LikeRecord> likePage = likeRecordMapper.selectPage(pageParam, wrapper);
        List<LikeRecord> records = likePage.getRecords();

        List<Map<String, Object>> list = new ArrayList<>();
        if (!records.isEmpty()) {
            // 批量查询被赞笔记标题
            Set<Long> noteIds = records.stream().map(LikeRecord::getTargetId).collect(Collectors.toSet());
            Map<Long, Note> noteMap = noteMapper.selectBatchIds(noteIds).stream()
                    .collect(Collectors.toMap(Note::getId, n -> n));
            // 批量查询点赞者用户信息
            Set<Long> likerIds = records.stream().map(LikeRecord::getUserId).collect(Collectors.toSet());
            Map<Long, User> userMap = userMapper.selectBatchIds(likerIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (LikeRecord lr : records) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", lr.getId());
                Note note = noteMap.get(lr.getTargetId());
                item.put("noteId", lr.getTargetId());
                item.put("noteTitle", note != null ? note.getTitle() : null);
                User liker = userMap.get(lr.getUserId());
                item.put("likerId", lr.getUserId());
                item.put("likerName", liker != null ? liker.getUsername() : null);
                item.put("likerAvatar", liker != null ? liker.getAvatarUrl() : null);
                item.put("createTime", lr.getCreateTime() != null ? sdf.format(lr.getCreateTime()) : null);
                list.add(item);
            }
        }
        return ResultUtils.success(new PageResult<>(likePage.getTotal(), likePage.getCurrent(), likePage.getSize(), list));
    }

    /**
     * 获取贡献热力图数据（过去一年每天的活动记录）
     */
    @GetMapping("/contribution")
    @LoginRequired
    @Operation(summary = "获取贡献热力图数据")
    public BaseResponse<List<Map<String, Object>>> getContributionData(
            @RequestParam(required = false) Integer year) {
        Long userId = UserContext.get().getUserId();
        Calendar cal = Calendar.getInstance();
        if (year != null) {
            cal.set(Calendar.YEAR, year);
        }
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DAY_OF_MONTH, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();

        // 查询该时间段内用户的笔记发布记录
        QueryWrapper<Note> noteQuery = new QueryWrapper<>();
        noteQuery.eq("author_id", userId)
                .ge("publish_time", startDate)
                .select("DATE(publish_time) as date_str", "COUNT(*) as cnt")
                .groupBy("DATE(publish_time)");

        List<Map<String, Object>> noteRecords = noteMapper.selectMaps(noteQuery);

        // 查询评论记录
        QueryWrapper<Comment> commentQuery = new QueryWrapper<>();
        commentQuery.eq("user_id", userId)
                .ge("create_time", startDate)
                .select("DATE(create_time) as date_str", "COUNT(*) as cnt")
                .groupBy("DATE(create_time)");

        List<Map<String, Object>> commentRecords = commentMapper.selectMaps(commentQuery);

        // 合并数据：按日期汇总活动数
        Map<String, Integer> dateCountMap = new HashMap<>();
        for (Map<String, Object> record : noteRecords) {
            String dateStr = String.valueOf(record.get("date_str"));
            int count = ((Number) record.get("cnt")).intValue();
            dateCountMap.merge(dateStr, count, Integer::sum);
        }
        for (Map<String, Object> record : commentRecords) {
            String dateStr = String.valueOf(record.get("date_str"));
            int count = ((Number) record.get("cnt")).intValue();
            dateCountMap.merge(dateStr, count, Integer::sum);
        }

        // 转换为前端需要的格式
        List<Map<String, Object>> result = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Map.Entry<String, Integer> entry : dateCountMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", entry.getKey());
            item.put("count", entry.getValue());
            result.add(item);
        }

        // 按日期排序
        result.sort((a, b) -> String.valueOf(a.get("date")).compareTo(String.valueOf(b.get("date"))));

        return ResultUtils.success(result);
    }

    /**
     * 获取学习趋势数据（近6个月每月的笔记和评论数）
     */
    @GetMapping("/trend")
    @LoginRequired
    @Operation(summary = "获取学习趋势数据")
    public BaseResponse<List<Map<String, Object>>> getLearningTrend() {
        Long userId = UserContext.get().getUserId();
        List<Map<String, Object>> result = new ArrayList<>();
        SimpleDateFormat monthFmt = new SimpleDateFormat("yyyy-MM");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -5); // 从6个月前开始
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        for (int i = 0; i < 6; i++) {
            Date monthStart = cal.getTime();
            cal.add(Calendar.MONTH, 1);
            Date monthEnd = cal.getTime();

            String monthLabel = monthFmt.format(monthStart);

            // 该月笔记数
            QueryWrapper<Note> noteQuery = new QueryWrapper<>();
            noteQuery.eq("author_id", userId)
                    .ge("publish_time", monthStart)
                    .lt("publish_time", monthEnd);
            long notes = noteMapper.selectCount(noteQuery);

            // 该月评论数
            QueryWrapper<Comment> commentQuery = new QueryWrapper<>();
            commentQuery.eq("user_id", userId)
                    .ge("create_time", monthStart)
                    .lt("create_time", monthEnd);
            long comments = commentMapper.selectCount(commentQuery);

            Map<String, Object> item = new HashMap<>();
            item.put("month", monthLabel);
            item.put("notes", notes);
            item.put("comments", comments);
            result.add(item);
        }

        return ResultUtils.success(result);
    }
}
