package com.example.usercenter.controller;

import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.NoteService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内容管理接口（管理员）
 */
@RestController
@RequestMapping("/content")
@Slf4j
public class ContentController extends BaseController {

    @Resource
    private NoteService noteService;

    /**
     * 搜索内容（管理员）
     */
    @GetMapping("/search")
    public BaseResponse<List<Note>> searchContent(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long authorId,
            HttpServletRequest request) {
        requireAdmin();
        List<Note> notes = noteService.searchContent(keyword, contentType, status, authorId);
        return ResultUtils.success(notes);
    }
}
