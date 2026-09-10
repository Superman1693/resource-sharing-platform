package com.example.usercenter.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.request.NoteQueryRequest;
import com.example.usercenter.service.NoteService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动预热：ApplicationReadyEvent 后台异步调热接口，
 * 触发 MyBatis Mapper XML 首次解析 + JIT 编译 + Jackson 序列化器构建 + 缓存填充，
 * 消除首次请求冷启动延迟（500ms → 80ms 级）
 * @author zy
 */
@Component
@Slf4j
public class WarmUpRunner {

    @Resource
    private NoteService noteService;

    @Resource
    private NoteMapper noteMapper;

    /** 预热热门笔记数量 */
    private static final int WARMUP_NOTE_COUNT = 5;

    @Async("asyncTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        long start = System.currentTimeMillis();
        try {
            // 1. 预热 noteList 缓存 + Mapper XML 首次解析
            noteService.getNoteList(new NoteQueryRequest());
            log.info("[预热] noteList 缓存已填充");

            // 2. 预热热门笔记详情缓存（like_count 降序取 5 篇）
            List<Note> hotNotes = noteMapper.selectList(new QueryWrapper<Note>()
                    .eq("status", "published").eq("is_delete", 0)
                    .orderByDesc("like_count").last("LIMIT " + WARMUP_NOTE_COUNT));
            int ok = 0;
            for (Note n : hotNotes) {
                try {
                    noteService.getNoteDetailPublished(n.getId());
                    ok++;
                } catch (Exception e) {
                    log.warn("[预热] 笔记 {} 预热失败: {}", n.getId(), e.getMessage());
                }
            }
            log.info("[预热] 完成，预热 {}/{} 篇笔记详情，总耗时 {}ms", ok, hotNotes.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("[预热] 异常: {}", e.getMessage());
        }
    }
}
