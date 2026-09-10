package com.example.usercenter.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 定时发布任务：每分钟扫描 scheduled 且到点的笔记，自动转 published
 * 仿 FlushViewCountTask 模式（DB 轮询），宕机重启不丢任务（状态持久在 DB）
 * @author zy
 */
@Component
@Slf4j
public class ScheduledPublishTask {

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private NoteService noteService;

    /** 单次最多发布数量，防积压一次性打爆下游 */
    private static final int BATCH_LIMIT = 200;

    @Scheduled(fixedDelay = 60000)
    public void publishScheduledNotes() {
        try {
            List<Note> due = noteMapper.selectList(new QueryWrapper<Note>()
                    .eq("status", "scheduled")
                    .le("publish_time", new Date())
                    .eq("is_delete", 0)
                    .orderByAsc("publish_time")
                    .last("LIMIT " + BATCH_LIMIT));
            if (due.isEmpty()) {
                return;
            }
            log.info("定时发布任务：发现 {} 篇到点笔记", due.size());
            int ok = 0;
            for (Note note : due) {
                try {
                    if (noteService.publishScheduledNote(note.getId())) {
                        ok++;
                    }
                } catch (Exception e) {
                    log.error("定时发布笔记失败 noteId={}: {}", note.getId(), e.getMessage());
                }
            }
            log.info("定时发布任务：成功发布 {} 篇", ok);
        } catch (Exception e) {
            log.error("定时发布任务异常: {}", e.getMessage(), e);
        }
    }
}
