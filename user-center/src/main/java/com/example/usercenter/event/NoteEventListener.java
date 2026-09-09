package com.example.usercenter.event;

import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.HotRankService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 笔记热度分事件监听器
 * <p>
 * 独立于 NoteServiceImpl 的 Bean，@Async 注解通过 Spring 代理正常生效，
 * 解决了原 NoteServiceImpl 中 @Async 自调用失效的问题。
 */
@Component
@Slf4j
public class NoteEventListener {

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private HotRankService hotRankService;

    @Async("asyncTaskExecutor")
    @EventListener
    public void handleHotScoreEvent(NoteHotScoreEvent event) {
        try {
            Note note = noteMapper.selectById(event.getNoteId());
            if (note != null) {
                double score = note.getViewCount() + note.getLikeCount() * 3.0 + note.getCommentCount() * 2.0;
                hotRankService.updateHotScore(event.getNoteId(), score);
            }
        } catch (Exception e) {
            log.warn("更新热度分失败, noteId={}: {}", event.getNoteId(), e.getMessage());
        }
    }
}
