package com.example.usercenter.task;

import com.example.usercenter.utils.RedisChatOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理 30 天未活跃的 AI 聊天会话
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CleanInactiveSessionTask {

    private final RedisChatOperator redisChatOperator;

    /** 每天凌晨 3 点执行 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanInactiveSessions() {
        try {
            int cleaned = redisChatOperator.cleanInactiveSessions(30);
            log.info("清理不活跃会话完成，共清理 {} 个", cleaned);
        } catch (Exception e) {
            log.error("清理不活跃会话失败: {}", e.getMessage(), e);
        }
    }
}
