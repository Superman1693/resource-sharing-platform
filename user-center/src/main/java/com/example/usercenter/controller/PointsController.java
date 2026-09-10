package com.example.usercenter.controller;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.PointsAccount;
import com.example.usercenter.model.domain.PointsLog;
import com.example.usercenter.model.domain.SignInRecord;
import com.example.usercenter.service.PointsService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分接口（签到 / 账户 / 流水）
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@LoginRequired
@Slf4j
@Tag(name = "积分接口")
public class PointsController {

    private final PointsService pointsService;

    @PostMapping("/sign")
    @Operation(summary = "签到")
    public BaseResponse<Map<String, Object>> sign() {
        Long userId = UserContext.get().getUserId();
        boolean ok = pointsService.signIn(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("signed", ok);
        if (ok) {
            PointsAccount a = pointsService.getAccount(userId);
            r.put("balance", a != null ? a.getBalance() : 0);
        }
        return ResultUtils.success(r);
    }

    @GetMapping("/sign/today")
    @Operation(summary = "今日是否已签到")
    public BaseResponse<Map<String, Object>> signToday() {
        Map<String, Object> r = new HashMap<>();
        r.put("signed", pointsService.isSignedToday(UserContext.get().getUserId()));
        return ResultUtils.success(r);
    }

    @GetMapping("/account")
    @Operation(summary = "积分账户")
    public BaseResponse<PointsAccount> account() {
        return ResultUtils.success(pointsService.getAccount(UserContext.get().getUserId()));
    }

    @GetMapping("/log")
    @Operation(summary = "积分流水（分页）")
    public BaseResponse<PageResult<PointsLog>> log(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtils.success(pointsService.getPointsLog(UserContext.get().getUserId(), page, pageSize));
    }

    @GetMapping("/sign/calendar")
    @Operation(summary = "某月签到日历")
    public BaseResponse<List<SignInRecord>> calendar(@RequestParam(required = false) String month) {
        return ResultUtils.success(pointsService.getSignCalendar(UserContext.get().getUserId(), month));
    }

    @PostMapping("/migrate")
    @AdminRequired
    @Operation(summary = "按贡献值初始化积分账户（管理员，幂等）")
    public BaseResponse<Map<String, Object>> migrate() {
        int count = pointsService.migrateFromContribution();
        return ResultUtils.success(Map.of("migrated", count));
    }
}
