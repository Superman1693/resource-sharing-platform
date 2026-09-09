package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.Knowledge;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.KnowledgeService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识地图接口
 * @author zy
 */
@RestController
@RequestMapping("/knowledge")
@Slf4j
public class KnowledgeController extends BaseController {

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * 获取知识地图（免登录）
     */
    @GetMapping("/map")
    public BaseResponse<Knowledge> getKnowledgeMap(@RequestParam(required = false) Long starId, HttpServletRequest request) {
        Knowledge knowledge = knowledgeService.getKnowledgeMap(starId);
        return ResultUtils.success(knowledge);
    }

    /**
     * 保存知识地图
     */
    @PostMapping("/map/save")
    public BaseResponse<Boolean> saveKnowledgeMap(@RequestBody Knowledge knowledge, HttpServletRequest request) {
        // 仅管理员可操作
        requireAdmin();

        if (knowledge == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识地图数据不能为空");
        }

        boolean result = knowledgeService.saveKnowledgeMap(knowledge, request);
        return ResultUtils.success(result);
    }

    /**
     * 获取知识地图节点内容（免登录）
     */
    @GetMapping("/node/{nodeId}/content")
    public BaseResponse<List<Note>> getNodeContent(@PathVariable String nodeId, HttpServletRequest request) {
        if (nodeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "节点ID不能为空");
        }

        List<Note> notes = knowledgeService.getNodeContent(nodeId);
        return ResultUtils.success(notes);
    }

    /**
     * 全量重建星球知识地图（管理员手动触发）
     */
    @PostMapping("/map/sync")
    public BaseResponse<Boolean> syncMap(@RequestParam Long starId, HttpServletRequest request) {
        requireAdmin();
        if (starId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球ID不能为空");
        }
        knowledgeService.regenerateMap(starId);
        return ResultUtils.success(true);
    }
}
