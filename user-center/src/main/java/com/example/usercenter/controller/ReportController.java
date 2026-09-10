package com.example.usercenter.controller;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Report;
import com.example.usercenter.service.ReportService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 举报管理接口（管理员）
 */
@RestController
@RequestMapping("/report")
@RequiredArgsConstructor
@AdminRequired
@Slf4j
@Tag(name = "举报管理接口")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/list")
    @Operation(summary = "举报列表（按状态筛选）")
    public BaseResponse<PageResult<Report>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtils.success(reportService.list(status, page, pageSize));
    }

    @PostMapping("/handle/{id}")
    @Operation(summary = "处理举报（resolved/ignored + 备注）")
    public BaseResponse<Boolean> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = body == null ? null : (String) body.get("status");
        String remark = body == null ? null : (String) body.get("remark");
        Long handlerId = UserContext.get().getUserId();
        return ResultUtils.success(reportService.handle(id, status, remark, handlerId));
    }
}
