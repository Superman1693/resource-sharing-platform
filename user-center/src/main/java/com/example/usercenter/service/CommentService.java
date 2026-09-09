package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Comment;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 评论服务接口
 * @author zy
 */
public interface CommentService extends IService<Comment> {
    /**
     * 获取评论列表
     */
    List<Comment> getCommentList(Long noteId, String keyword, String status, Integer page, Integer pageSize, boolean isAdmin);

    /**
     * 添加评论
     */
    Comment addComment(Long noteId, String content, HttpServletRequest request);

    /**
     * 回复评论
     */
    Comment replyComment(Long commentId, Long noteId, String content, HttpServletRequest request);

    /**
     * 删除评论
     */
    boolean deleteComment(Long id, HttpServletRequest request);

    /**
     * 审核评论（管理员）
     */
    boolean approveComment(Long id, String status, HttpServletRequest request);

    /**
     * 点赞评论
     */
    boolean likeComment(Long id, HttpServletRequest request);

    /**
     * 举报评论
     */
    boolean reportComment(Long id, String reason, HttpServletRequest request);

    /**
     * 获取当前用户的评论（分页，"我的评论"专属页）
     */
    PageResult<Comment> getMyComments(Long userId, Integer page, Integer pageSize);

    /**
     * @提及通知：对评论中提及的用户发送 mention 通知
     * @param mentionedUserIds 被提及的用户ID列表
     * @param noteId 笔记ID
     * @param senderId 评论发送者ID
     * @param content 评论内容
     */
    void notifyMentioned(List<Long> mentionedUserIds, Long noteId, Long senderId, String content);
}
