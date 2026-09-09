package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.FollowService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
@LoginRequired
@Slf4j
@Tag(name = "关注接口")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/add")
    @Operation(summary = "关注用户")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Void> follow(@RequestBody Map<String, Object> body) {
        Long currentUserId = UserContext.get().getUserId();
        Long userId = Long.valueOf(body.get("userId").toString());
        followService.follow(currentUserId, userId);
        return ResultUtils.success(null);
    }

    @PostMapping("/delete")
    @Operation(summary = "取消关注")
    @PreventDuplicate(waitTime = 0, leaseTime = 3, message = "操作过于频繁，请稍后再试")
    public BaseResponse<Void> unfollow(@RequestBody Map<String, Object> body) {
        Long currentUserId = UserContext.get().getUserId();
        Long userId = Long.valueOf(body.get("userId").toString());
        followService.unfollow(currentUserId, userId);
        return ResultUtils.success(null);
    }

    @GetMapping("/status")
    @Operation(summary = "检查关注状态")
    public BaseResponse<Map<String, Object>> checkStatus(@RequestParam Long userId) {
        Long currentUserId = UserContext.get().getUserId();
        Map<String, Object> result = new HashMap<>();
        result.put("isFollowing", followService.isFollowing(currentUserId, userId));
        result.put("followingCount", followService.getFollowingCount(userId));
        result.put("followerCount", followService.getFollowerCount(userId));
        return ResultUtils.success(result);
    }

    @GetMapping("/list")
    @Operation(summary = "获取关注列表")
    public BaseResponse<PageResult<User>> getFollowingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(followService.getFollowingList(userId, page, pageSize));
    }

    @GetMapping("/follower/list")
    @Operation(summary = "获取粉丝列表")
    public BaseResponse<PageResult<User>> getFollowerList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(followService.getFollowerList(userId, page, pageSize));
    }

    @GetMapping("/count")
    @Operation(summary = "获取关注/粉丝数")
    public BaseResponse<Map<String, Long>> getCount() {
        Long userId = UserContext.get().getUserId();
        Map<String, Long> result = new HashMap<>();
        result.put("followingCount", followService.getFollowingCount(userId));
        result.put("followerCount", followService.getFollowerCount(userId));
        return ResultUtils.success(result);
    }
}
