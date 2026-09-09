package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Notification;

public interface NotificationService {

    /** 发送通知 */
    void send(Long receiverId, Long senderId, String senderName, String senderAvatar,
              String type, Long targetId, String targetTitle, String content);

    /** 获取通知列表（分页） */
    PageResult<Notification> getList(Long userId, int page, int pageSize);

    /** 获取未读数量 */
    long getUnreadCount(Long userId);

    /** 标记单条已读 */
    void markRead(Long notificationId, Long userId);

    /** 标记全部已读 */
    void markAllRead(Long userId);
}
