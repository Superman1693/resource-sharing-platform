package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.mapper.CommentMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.AsyncTaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AsyncTaskServiceImpl implements AsyncTaskService {

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private CommentMapper commentMapper;

    @Override
    @Async("asyncTaskExecutor")
    public void updateNoteCommentCount(Long noteId) {
        try {
            // 统计该笔记的评论数并更新
            long count = commentMapper.selectCount(
                    new QueryWrapper<com.example.usercenter.model.domain.Comment>()
                            .eq("note_id", noteId).ne("status", "rejected")
            );
            Note note = new Note();
            note.setId(noteId);
            note.setCommentCount((int) count);
            noteMapper.updateById(note);
        } catch (Exception e) {
            log.error("异步更新笔记评论数失败, noteId={}, error={}", noteId, e.getMessage(), e);
        }
    }

    @Override
    @Async("asyncTaskExecutor")
    public void recordViewHistory(Long noteId, Long userId) {
        try {
            // 记录浏览历史（简单实现，可扩展）
            log.debug("记录浏览历史: noteId={}, userId={}", noteId, userId);
        } catch (Exception e) {
            log.error("异步记录浏览历史失败, noteId={}, userId={}, error={}", noteId, userId, e.getMessage(), e);
        }
    }
}
