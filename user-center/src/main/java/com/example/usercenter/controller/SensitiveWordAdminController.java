package com.example.usercenter.controller;

import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.utils.SensitiveWordChecker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词库管理接口（仅管理员）
 *
 * 支持热更新：更新后立即对全站生效，无需重启服务
 */
@RestController
@RequestMapping("/admin/sensitive")
@AdminRequired
@Slf4j
@Tag(name = "敏感词库管理")
public class SensitiveWordAdminController extends BaseController {

    @Resource
    private SensitiveWordChecker sensitiveWordChecker;

    /**
     * 查看当前生效的词库
     */
    @GetMapping("/words")
    @Operation(summary = "查看当前敏感词库")
    public BaseResponse<Map<String, Object>> getWords() {
        List<String> words = sensitiveWordChecker.getWords();
        Map<String, Object> result = new HashMap<>();
        result.put("total", words.size());
        result.put("words", words);
        return ResultUtils.success(result);
    }

    /**
     * 全量更新词库（每行一个词的文本或字符串数组均可），写入外部文件并立即生效
     */
    @PutMapping("/words")
    @Operation(summary = "全量更新敏感词库（热生效）")
    public BaseResponse<Map<String, Object>> updateWords(@RequestBody UpdateWordsRequest body) {
        if (body == null || body.getWords() == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "词库内容不能为空", "");
        }
        // 兼容两种传法：字符串数组，或整段文本（按行拆分）
        List<String> words = body.getWords();
        int total = sensitiveWordChecker.updateWords(words);
        log.info("管理员更新敏感词库，共 {} 个词", total);
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        return ResultUtils.success(result);
    }

    /**
     * 从词库文件重新加载（编辑服务器上的词库文件后调用）
     */
    @PostMapping("/reload")
    @Operation(summary = "重载敏感词库（热生效）")
    public BaseResponse<Map<String, Object>> reload() {
        int total = sensitiveWordChecker.reload();
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        return ResultUtils.success(result);
    }

    @Data
    public static class UpdateWordsRequest {
        /** 词列表：["词1", "词2"] 或 ["词1\n词2"]（整段文本按行拆） */
        private List<String> words;
    }
}
