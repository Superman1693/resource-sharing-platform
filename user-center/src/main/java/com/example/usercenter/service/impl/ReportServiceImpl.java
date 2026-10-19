package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.mapper.CommentMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.ReportMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Comment;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.Report;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.NoteService;
import com.example.usercenter.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 举报服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    /** 评论内容摘要长度（列表展示用） */
    private static final int COMMENT_SUMMARY_LEN = 30;

    private final ReportMapper reportMapper;
    private final NoteMapper noteMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final NoteService noteService;

    @Override
    public void create(Long reporterId, String targetType, Long targetId, String reason) {
        if (reporterId == null || StringUtils.isBlank(targetType) || targetId == null) return;
        // 同一举报人对同一目标 pending 不重复
        Long exists = this.count(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getStatus, "pending"));
        if (exists != null && exists > 0) return;
        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetType(targetType);
        r.setTargetId(targetId);
        r.setReason(reason);
        r.setStatus("pending");
        r.setCreateTime(new Date());
        this.save(r);
    }

    @Override
    public PageResult<Report> list(String status, int page, int pageSize) {
        LambdaQueryWrapper<Report> w = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreateTime);
        if (StringUtils.isNotBlank(status)) {
            w.eq(Report::getStatus, status);
        }
        IPage<Report> p = this.page(new Page<>(page, pageSize), w);
        List<Report> records = p.getRecords();
        fillDisplayFields(records);
        return new PageResult<>(p.getTotal(), page, pageSize, records);
    }

    /**
     * 填充列表展示用的派生字段（举报人昵称 / 目标标题 / 评论所属笔记）。
     * <p>管理端此前只能看到目标ID与举报人ID，无法判断被举报的是什么内容，故在此补充。
     * 按类型批量查询，避免逐条查库。
     */
    private void fillDisplayFields(List<Report> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> noteIds = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (Report r : records) {
            if (r.getReporterId() != null) {
                userIds.add(r.getReporterId());
            }
            if (r.getTargetId() == null) {
                continue;
            }
            if ("note".equals(r.getTargetType())) {
                noteIds.add(r.getTargetId());
            } else if ("comment".equals(r.getTargetType())) {
                commentIds.add(r.getTargetId());
            } else if ("user".equals(r.getTargetType())) {
                userIds.add(r.getTargetId());
            }
        }

        Map<Long, Note> noteMap = noteIds.isEmpty() ? Collections.emptyMap()
                : noteMapper.selectBatchIds(noteIds).stream()
                        .collect(Collectors.toMap(Note::getId, Function.identity(), (a, b) -> a));
        Map<Long, Comment> commentMap = commentIds.isEmpty() ? Collections.emptyMap()
                : commentMapper.selectBatchIds(commentIds).stream()
                        .collect(Collectors.toMap(Comment::getId, Function.identity(), (a, b) -> a));
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        for (Report r : records) {
            User reporter = r.getReporterId() == null ? null : userMap.get(r.getReporterId());
            if (reporter != null) {
                r.setReporterName(StringUtils.isNotBlank(reporter.getUsername())
                        ? reporter.getUsername() : reporter.getUserAccount());
            }
            if (r.getTargetId() == null) {
                continue;
            }
            switch (r.getTargetType() == null ? "" : r.getTargetType()) {
                case "note" -> {
                    Note n = noteMap.get(r.getTargetId());
                    r.setTargetTitle(n == null ? "内容已删除" : n.getTitle());
                }
                case "comment" -> {
                    Comment c = commentMap.get(r.getTargetId());
                    if (c == null) {
                        r.setTargetTitle("评论已删除");
                    } else {
                        r.setTargetNoteId(c.getNoteId());
                        String content = StringUtils.defaultString(c.getContent()).replaceAll("\\s+", " ").trim();
                        r.setTargetTitle(content.length() > COMMENT_SUMMARY_LEN
                                ? content.substring(0, COMMENT_SUMMARY_LEN) + "…" : content);
                    }
                }
                case "user" -> {
                    User u = userMap.get(r.getTargetId());
                    r.setTargetTitle(u == null ? "用户已注销"
                            : (StringUtils.isNotBlank(u.getUsername()) ? u.getUsername() : u.getUserAccount()));
                }
                default -> r.setTargetTitle(null);
            }
        }
    }

    @Override
    public boolean handle(Long id, String status, String remark, Long handlerId) {
        if (id == null || StringUtils.isBlank(status)) return false;
        Report r = this.getById(id);
        if (r == null) return false;
        r.setStatus(status);
        r.setHandlerId(handlerId);
        r.setHandleRemark(remark);
        r.setHandleTime(new Date());
        boolean updated = this.updateById(r);
        // 【修复】举报被「忽略」时，需把因举报被冻结为 pending 的笔记恢复为已发布。
        // 原实现只改 report 行状态，被举报笔记会永久停留在 pending（对普通用户不可见），
        // 而作者又无法自行改回（updateNote 已禁止非管理员做 pending → published）。
        if (updated && "ignored".equals(status)
                && "note".equals(r.getTargetType()) && r.getTargetId() != null) {
            try {
                noteService.approveNote(r.getTargetId());
            } catch (Exception e) {
                log.warn("恢复被举报笔记失败: noteId={}", r.getTargetId(), e);
            }
        }
        return updated;
    }
}
