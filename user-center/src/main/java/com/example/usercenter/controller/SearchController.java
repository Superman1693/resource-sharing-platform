package com.example.usercenter.controller;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.es.NoteDocument;
import com.example.usercenter.model.es.ResourceDocument;
import com.example.usercenter.service.EsSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 基于 Elasticsearch 的全文搜索接口
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "搜索接口（ES）")
public class SearchController {

    private final EsSearchService esSearchService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final com.example.usercenter.service.UserService userService;

    // ── 热搜词 ─────────────────────────────────────────────

    @GetMapping("/hot")
    @Operation(summary = "热搜词 top10（Redis ZSET）")
    public BaseResponse<java.util.List<String>> hotKeywords() {
        try {
            java.util.Set<String> set = redisTemplate.opsForZSet().reverseRange("search:hot", 0, 9);
            return ResultUtils.success(set == null
                    ? java.util.Collections.emptyList()
                    : new java.util.ArrayList<>(set));
        } catch (Exception e) {
            return ResultUtils.success(java.util.Collections.emptyList());
        }
    }

    // ── 用户搜索（公开，DB 模糊） ─────────────────────────

    @GetMapping("/users")
    @Operation(summary = "搜索用户（按用户名/账号模糊，公开）")
    public BaseResponse<java.util.List<java.util.Map<String, Object>>> searchUsersPub(
            @RequestParam(required = false) String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResultUtils.success(java.util.Collections.emptyList());
        }
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.example.usercenter.model.domain.User> qw =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        qw.and(w -> w.like("username", keyword).or().like("user_account", keyword))
          .last("LIMIT 20");
        java.util.List<com.example.usercenter.model.domain.User> users = userService.list(qw);
        // 仅返回脱敏字段，避免泄露密码/手机/邮箱
        java.util.List<java.util.Map<String, Object>> result = users.stream().map(u -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("avatarUrl", u.getAvatarUrl());
            m.put("userProfile", u.getBio());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResultUtils.success(result);
    }

    // ── 笔记搜索 ───────────────────────────────────────────

    @GetMapping("/notes")
    @Operation(summary = "ES 全文搜索笔记")
    public BaseResponse<PageResult<NoteDocument>> searchNotes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String contentType,
            @RequestParam(defaultValue = "hot") String sortType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        pageSize = Math.min(pageSize, 50);
        return ResultUtils.success(
                esSearchService.searchNotes(keyword, category, contentType, sortType, page, pageSize));
    }

    // ── 资源搜索 ───────────────────────────────────────────

    @GetMapping("/resources")
    @Operation(summary = "ES 全文搜索资源")
    public BaseResponse<PageResult<ResourceDocument>> searchResources(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        pageSize = Math.min(pageSize, 50);
        return ResultUtils.success(
                esSearchService.searchResources(keyword, tag, category, page, pageSize));
    }

    // ── 数据同步（管理员） ──────────────────────────────────

    @PostMapping("/sync/notes")
    @AdminRequired
    @Operation(summary = "全量同步笔记到 ES（管理员）")
    public BaseResponse<Map<String, Object>> syncNotes() {
        int count = esSearchService.syncAllNotes();
        return ResultUtils.success(Map.of("synced", count, "index", "note_index"));
    }

    @PostMapping("/sync/resources")
    @AdminRequired
    @Operation(summary = "全量同步资源到 ES（管理员）")
    public BaseResponse<Map<String, Object>> syncResources() {
        int count = esSearchService.syncAllResources();
        return ResultUtils.success(Map.of("synced", count, "index", "resource_index"));
    }
}
