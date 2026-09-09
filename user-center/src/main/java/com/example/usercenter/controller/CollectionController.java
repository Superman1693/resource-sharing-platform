package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.CollectionService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 笔记收藏接口
 */
@RestController
@RequestMapping("/collection")
@RequiredArgsConstructor
@LoginRequired
@Slf4j
@Tag(name = "收藏接口")
public class CollectionController {

    private final CollectionService collectionService;

    @PostMapping("/{noteId}")
    @Operation(summary = "收藏/取消收藏笔记（toggle）")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Map<String, Object>> toggle(@PathVariable Long noteId) {
        Long userId = UserContext.get().getUserId();
        boolean collected = collectionService.toggleCollect(userId, noteId);
        Map<String, Object> result = new HashMap<>();
        result.put("collected", collected);
        return ResultUtils.success(result);
    }

    @GetMapping("/check/{noteId}")
    @Operation(summary = "检查是否已收藏")
    public BaseResponse<Map<String, Object>> check(@PathVariable Long noteId) {
        Long userId = UserContext.get().getUserId();
        Map<String, Object> result = new HashMap<>();
        result.put("collected", collectionService.isCollected(userId, noteId));
        return ResultUtils.success(result);
    }

    @GetMapping("/my")
    @Operation(summary = "我的收藏列表")
    public BaseResponse<PageResult<Note>> myCollected(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(collectionService.getMyCollected(userId, page, pageSize));
    }
}
