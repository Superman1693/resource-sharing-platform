package com.example.usercenter.controller;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.Tag;
import com.example.usercenter.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 标签接口
 */
@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签接口")
public class TagController {

    private final TagService tagService;

    @GetMapping("/list")
    @Operation(summary = "标签广场（按使用次数降序）")
    public BaseResponse<List<Tag>> list() {
        return ResultUtils.success(tagService.listTags());
    }

    @GetMapping("/{name}/notes")
    @Operation(summary = "某标签下的笔记列表")
    public BaseResponse<PageResult<Note>> notesByTag(
            @PathVariable String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(tagService.getNotesByTag(name, page, pageSize));
    }

    @GetMapping("/suggest")
    @Operation(summary = "标签联想（前缀匹配）")
    public BaseResponse<List<String>> suggest(@RequestParam(required = false) String q) {
        return ResultUtils.success(tagService.suggest(q));
    }

    @PostMapping("/migrate")
    @AdminRequired
    @Operation(summary = "从 note.tags JSON 回填到关联表（管理员，幂等）")
    public BaseResponse<Map<String, Object>> migrate() {
        int count = tagService.migrateFromJson();
        return ResultUtils.success(Map.of("migrated", count));
    }
}
