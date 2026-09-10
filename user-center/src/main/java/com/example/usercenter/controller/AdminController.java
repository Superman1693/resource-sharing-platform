package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.*;
import com.example.usercenter.model.domain.*;
import com.example.usercenter.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@AdminRequired
@Slf4j
public class AdminController extends BaseController {

    @Resource private UserMapper userMapper;
    @Resource private NoteMapper noteMapper;
    @Resource private CommentMapper commentMapper;
    @Resource private AdminLogMapper adminLogMapper;
    @Resource
    private StarMapper starMapper;
    @Resource
    private NoteService noteService;
    /** 封禁用户 */
    @PostMapping("/user/ban/{id}")
    public BaseResponse<Boolean> banUser(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");

        User update = new User();
        update.setId(id);
        update.setUserStatus(1);
        userMapper.updateById(update);

        saveAdminLog(getLoginUser().getUserId(), id, "ban", "user", "封禁用户");
        return ResultUtils.success(true);
    }

    /** 解封用户 */
    @PostMapping("/user/unban/{id}")
    public BaseResponse<Boolean> unbanUser(@PathVariable Long id) {
        User update = new User();
        update.setId(id);
        update.setUserStatus(0);
        userMapper.updateById(update);

        saveAdminLog(getLoginUser().getUserId(), id, "unban", "user", "解封用户");
        return ResultUtils.success(true);
    }

    /** 获取待审核内容列表 */
    @GetMapping("/content/pending")
    public BaseResponse<Map<String, Object>> getPendingContent() {
        List<Note> pendingNotes = noteMapper.selectList(
            new QueryWrapper<Note>().eq("status", "pending").eq("is_delete", 0)
        );
        List<Comment> pendingComments = commentMapper.selectList(
            new QueryWrapper<Comment>().eq("status", "pending")
        );
        Map<String, Object> result = new HashMap<>();
        result.put("notes", pendingNotes);
        result.put("comments", pendingComments);
        return ResultUtils.success(result);
    }

    /** 审核通过笔记（走 NoteService，统一同步 ES/布隆/content_count/积分/publish_time） */
    @PostMapping("/note/approve/{id}")
    public BaseResponse<Boolean> approveNote(@PathVariable Long id) {
        noteService.approveNote(id);
        saveAdminLog(getLoginUser().getUserId(), id, "approve", "note", "审核通过笔记");
        return ResultUtils.success(true);
    }

    /** 拒绝笔记（走 NoteService，统一从 ES 移除并维护 content_count） */
    @PostMapping("/note/reject/{id}")
    public BaseResponse<Boolean> rejectNote(@PathVariable Long id) {
        noteService.rejectNote(id);
        saveAdminLog(getLoginUser().getUserId(), id, "reject", "note", "拒绝笔记");
        return ResultUtils.success(true);
    }

    /** 记录管理员操作日志 */
    private void saveAdminLog(Long operatorId, Long targetId, String action, String targetType, String remark) {
        try {
            AdminLog adminLog = new AdminLog();
            adminLog.setOperatorId(operatorId);
            adminLog.setTargetId(targetId);
            adminLog.setAction(action);
            adminLog.setTargetType(targetType);
            adminLog.setRemark(remark);
            adminLog.setCreateTime(new Date());
            adminLogMapper.insert(adminLog);
        } catch (Exception e) {
            log.warn("记录管理员操作日志失败: {}", e.getMessage());
        }
    }
    /** 管理员获取所有星球（含停用） */
    @GetMapping("/star/list")
    public BaseResponse<List<Star>> adminListStars(
            @RequestParam(required = false) String keyword) {
        requireAdmin();
        QueryWrapper<Star> wrapper = new QueryWrapper<>();
        wrapper.eq("is_delete", 0);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("name", keyword).or().like("description", keyword));
        }
        wrapper.orderByDesc("id");
        return ResultUtils.success(starMapper.selectList(wrapper));
    }

    /** 管理员创建星球 */
    @PostMapping("/star/create")
    public BaseResponse<Star> createStar(@RequestBody Star star) {
        requireAdmin();
        if (star.getName() == null || star.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "星球名称不能为空");
        }
        star.setOwnerId(getLoginUser().getUserId());
        star.setMemberCount(0);
        star.setContentCount(0);
        star.setStatus("active");
        star.setCreateTime(new Date());
        star.setUpdateTime(new Date());
        starMapper.insert(star);
        return ResultUtils.success(star);
    }

    /** 管理员更新星球 */
    @PutMapping("/star/update/{id}")
    public BaseResponse<Boolean> updateStar(@PathVariable Long id, @RequestBody Star star) {
        requireAdmin();
        Star exist = starMapper.selectById(id);
        if (exist == null || exist.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }
        star.setId(id);
        star.setUpdateTime(new Date());
        starMapper.updateById(star);
        return ResultUtils.success(true);
    }

    /** 管理员删除星球（逻辑删除） */
    @DeleteMapping("/star/delete/{id}")
    public BaseResponse<Boolean> deleteStar(@PathVariable Long id) {
        requireAdmin();
        Star exist = starMapper.selectById(id);
        if (exist == null || exist.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }
        starMapper.deleteById(id);
        return ResultUtils.success(true);
    }

    /** 管理员切换星球状态 */
    @PostMapping("/star/toggle/{id}")
    public BaseResponse<Boolean> toggleStarStatus(@PathVariable Long id) {
        requireAdmin();
        Star exist = starMapper.selectById(id);
        if (exist == null || exist.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "星球不存在");
        }
        Star update = new Star();
        update.setId(id);
        update.setStatus("active".equals(exist.getStatus()) ? "inactive" : "active");
        update.setUpdateTime(new Date());
        starMapper.updateById(update);
        return ResultUtils.success(true);
    }
}
