package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.model.domain.NoteColumn;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.ColumnService;
import com.example.usercenter.utils.UserContext;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 笔记专栏接口（星球内合集：作者把系列笔记串成专栏）
 * @author zy
 */
@RestController
@RequestMapping("/column")
public class ColumnController {

    @Resource
    private ColumnService columnService;

    /** 创建专栏 */
    @LoginRequired
    @PostMapping("/add")
    public BaseResponse<NoteColumn> createColumn(@RequestBody NoteColumn column) {
        Long userId = currentUserId();
        return ResultUtils.success(columnService.createColumn(column, userId));
    }

    /** 更新专栏 */
    @LoginRequired
    @PutMapping("/update/{id}")
    public BaseResponse<Boolean> updateColumn(@PathVariable Long id, @RequestBody NoteColumn column) {
        Long userId = currentUserId();
        return ResultUtils.success(columnService.updateColumn(id, column, userId));
    }

    /** 删除专栏 */
    @LoginRequired
    @DeleteMapping("/delete/{id}")
    public BaseResponse<Boolean> deleteColumn(@PathVariable Long id) {
        Long userId = currentUserId();
        return ResultUtils.success(columnService.deleteColumn(id, userId));
    }

    /** 查询专栏列表（免登录浏览星球/作者专栏） */
    @GetMapping("/list")
    public BaseResponse<List<NoteColumn>> listColumns(
            @RequestParam(required = false) Long starId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return ResultUtils.success(columnService.listColumns(starId, authorId, includeArchived));
    }

    /** 专栏详情（含章节笔记列表，按章节序号升序） */
    @GetMapping("/{id}")
    public BaseResponse<NoteColumn> getColumnDetail(@PathVariable Long id) {
        return ResultUtils.success(columnService.getColumnDetail(id));
    }

    /** 把笔记加入专栏 */
    @LoginRequired
    @PostMapping("/note/add")
    public BaseResponse<Boolean> addNoteToColumn(@RequestBody AddNoteToColumnRequest req) {
        Long userId = currentUserId();
        return ResultUtils.success(columnService.addNoteToColumn(req.getCollectionId(), req.getNoteId(), req.getSortOrder(), userId));
    }

    /** 把笔记移出专栏 */
    @LoginRequired
    @PostMapping("/note/remove")
    public BaseResponse<Boolean> removeNoteFromColumn(@RequestBody RemoveNoteRequest req) {
        Long userId = currentUserId();
        return ResultUtils.success(columnService.removeNoteFromNote(req.getNoteId(), userId));
    }

    /** 我的专栏 */
    @LoginRequired
    @GetMapping("/my")
    public BaseResponse<List<NoteColumn>> myColumns() {
        Long userId = currentUserId();
        return ResultUtils.success(columnService.listMyColumns(userId));
    }

    /** 取当前登录用户ID（@LoginRequired 已保证登录） */
    private Long currentUserId() {
        LoginUserDTO dto = UserContext.get();
        return dto == null ? null : dto.getUserId();
    }

    /** 加入专栏请求体 */
    @Data
    public static class AddNoteToColumnRequest {
        private Long collectionId;
        private Long noteId;
        private Integer sortOrder;
    }

    /** 移出专栏请求体 */
    @Data
    public static class RemoveNoteRequest {
        private Long noteId;
    }
}
