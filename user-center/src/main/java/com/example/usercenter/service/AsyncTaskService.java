package com.example.usercenter.service;

/**
 * 异步任务服务接口
 */
public interface AsyncTaskService {
    /** 异步更新笔记评论数 */
    void updateNoteCommentCount(Long noteId);
    /** 异步记录浏览历史 */
    void recordViewHistory(Long noteId, Long userId);
}
