package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.RecommendService;
import com.example.usercenter.utils.UserContext;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 个性化推荐接口（标签偏好 + 热门兜底）
 * 免登录走热门兜底，登录走标签偏好
 * @author zy
 */
@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Resource
    private RecommendService recommendService;

    /** 推荐笔记列表（默认 6 条） */
    @GetMapping("/list")
    public BaseResponse<List<Note>> recommend(@RequestParam(defaultValue = "6") int size) {
        Long userId = null;
        try {
            LoginUserDTO dto = UserContext.get();
            if (dto != null) {
                userId = dto.getUserId();
            }
        } catch (Exception ignored) {
        }
        return ResultUtils.success(recommendService.recommend(userId, size));
    }
}
