package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.service.CaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 验证码接口
 */
@RestController
@RequestMapping("/captcha")
@Slf4j
@Tag(name = "验证码接口")
public class CaptchaController {

    @Resource
    private CaptchaService captchaService;

    /**
     * 获取图形验证码
     * 返回验证码 key 和 Base64 编码的图片
     */
    @GetMapping("/get")
    @Operation(summary = "获取图形验证码")
    public BaseResponse<Map<String, String>> getCaptcha() {
        String result = captchaService.generateCaptcha();
        String[] parts = result.split(":");

        Map<String, String> data = new HashMap<>();
        data.put("captchaKey", parts[0]);
        // 图片 Base64 可能包含 ":"，需要重新拼接
        String imageBase64 = String.join(":", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        data.put("captchaImage", imageBase64);

        return ResultUtils.success(data);
    }
}
