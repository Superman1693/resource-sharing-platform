package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.LearningPath;
import com.example.usercenter.model.domain.LearningPathNode;
import com.example.usercenter.model.domain.request.UpdateNodeStatusRequest;
import com.example.usercenter.service.LearningPathService;
import com.example.usercenter.utils.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/learning-path")
@RequiredArgsConstructor
@LoginRequired
@Slf4j
@Tag(name = "学习路径接口")
public class LearningPathController {

    private final LearningPathService learningPathService;

    @GetMapping("/list")
    @Operation(summary = "获取用户学习路径列表")
    public BaseResponse<List<LearningPath>> list() {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(learningPathService.getUserPaths(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取学习路径详情")
    public BaseResponse<LearningPath> detail(@PathVariable Long id) {
        Long userId = UserContext.get().getUserId();
        return ResultUtils.success(learningPathService.getPathDetail(id, userId));
    }

    @GetMapping("/{id}/nodes")
    @Operation(summary = "获取路径节点列表")
    public BaseResponse<List<LearningPathNode>> nodes(@PathVariable Long id) {
        return ResultUtils.success(learningPathService.getPathNodes(id));
    }

    @PostMapping("/node/status")
    @Operation(summary = "更新节点状态")
    public BaseResponse<Void> updateNodeStatus(@RequestBody UpdateNodeStatusRequest request) {
        if (request == null || request.getNodeId() == null || StringUtils.isBlank(request.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long userId = UserContext.get().getUserId();
        learningPathService.updateNodeStatus(request.getNodeId(), userId, request.getStatus());
        return ResultUtils.success(null);
    }
}
