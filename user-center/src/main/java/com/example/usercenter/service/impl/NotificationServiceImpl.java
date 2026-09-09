package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.NotificationMapper;
import com.example.usercenter.model.domain.Notification;
import com.example.usercenter.service.NotificationService;
import com.example.usercenter.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final WebSocketService webSocketService;

    @Override
    @Async("asyncTaskExecutor")
    public void send(Long receiverId, Long senderId, String senderName, String senderAvatar,
                     String type, Long targetId, String targetTitle, String content) {
        // 不给自己发通知
        if (receiverId == null || receiverId.equals(senderId)) return;
        try {
            Notification n = new Notification();
            n.setReceiverId(receiverId);
            n.setSenderId(senderId);
            n.setSenderName(senderName);
            n.setSenderAvatar(senderAvatar);
            n.setType(type);
            n.setTargetId(targetId);
            n.setTargetTitle(targetTitle);
            n.setContent(content);
            n.setIsRead(0);
            n.setCreateTime(new Date());
            this.save(n);

            // 通过 WebSocket 实时推送给接收者
            webSocketService.sendToUser(receiverId, Map.of(
                    "type", type,
                    "content", content,
                    "senderName", senderName != null ? senderName : "",
                    "senderAvatar", senderAvatar != null ? senderAvatar : "",
                    "targetId", targetId != null ? targetId : 0,
                    "targetTitle", targetTitle != null ? targetTitle : "",
                    "notificationId", n.getId(),
                    "createTime", n.getCreateTime().getTime()
            ));
        } catch (Exception e) {
            log.error("发送通知失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public PageResult<Notification> getList(Long userId, int page, int pageSize) {
        log.info("查询通知列表, userId={}, page={}, pageSize={}", userId, page, pageSize);
        IPage<Notification> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsDelete, 0)
                .orderByDesc(Notification::getCreateTime);
        IPage<Notification> result = this.page(pageParam, wrapper);
        log.info("查询通知列表结果, total={}, records={}", result.getTotal(), result.getRecords().size());
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public long getUnreadCount(Long userId) {
        log.info("查询未读通知数, userId={}", userId);
        long count = this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getIsDelete, 0)
                .eq(Notification::getIsRead, 0));
        log.info("未读通知数={}", count);
        return count;
    }

    @Override
    public void markRead(Long notificationId, Long userId) {
        Notification n = this.getById(notificationId);
        if (n == null || !n.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权操作");
        }
        n.setIsRead(1);
        this.updateById(n);
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }
}
