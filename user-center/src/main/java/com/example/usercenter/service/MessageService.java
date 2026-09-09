package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Message;
import com.example.usercenter.model.domain.MessageConversation;
import com.example.usercenter.model.dto.ConversationVO;

import java.util.List;

public interface MessageService {
    MessageConversation getOrCreateConversation(Long user1Id, Long user2Id);
    PageResult<ConversationVO> getConversationList(Long userId, int page, int pageSize);
    PageResult<Message> getMessageHistory(Long conversationId, Long userId, int page, int pageSize);
    Message sendMessage(Long senderId, Long receiverId, String content);
    long getUnreadCount(Long userId);

    /**
     * 标记会话中对方发来的消息为已读
     */
    void markConversationRead(Long conversationId, Long userId);

    /**
     * 获取或创建与指定用户的会话（用于从用户主页等入口发起私信）
     */
    ConversationVO getOrCreateConversationVO(Long userId, Long otherUserId);
}
