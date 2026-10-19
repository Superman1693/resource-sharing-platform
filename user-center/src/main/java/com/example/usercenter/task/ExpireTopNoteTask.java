package com.example.usercenter.task;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.model.domain.Note;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 自助置顶到期自动下架。
 *
 * <p>用户用积分自助置顶的笔记会带 {@code top_until}（到期时间），到点后需要把
 * {@code is_top} 置回 0；管理员手动置顶的 {@code top_until} 为 NULL，不受影响。</p>
 *
 * <p>用一条 UPDATE 批量处理，避免逐条查询。{@code top_until} 同时清空，
 * 保证数据干净（MyBatis-Plus 的 {@code updateById} 会忽略 null 字段，
 * 所以这里必须用 {@link UpdateWrapper} 显式 set null）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpireTopNoteTask {

    private final NoteMapper noteMapper;

    /** 每 10 分钟检查一次（置顶精度到 10 分钟足够） */
    @Scheduled(cron = "0 */10 * * * ?")
    @CacheEvict(value = {"noteList", "noteDetail"}, allEntries = true)
    public void expireTopNotes() {
        try {
            int updated = noteMapper.update(null, new UpdateWrapper<Note>()
                    .eq("is_top", 1)
                    .isNotNull("top_until")
                    .lt("top_until", new Date())
                    .set("is_top", 0)
                    .set("top_until", null));
            if (updated > 0) {
                log.info("自助置顶到期下架完成，共 {} 篇", updated);
            }
        } catch (Exception e) {
            log.error("置顶到期下架失败: {}", e.getMessage(), e);
        }
    }
}
