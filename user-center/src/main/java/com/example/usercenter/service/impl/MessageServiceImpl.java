package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.MessageConversationMapper;
import com.example.usercenter.mapper.MessageMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Message;
import com.example.usercenter.model.domain.MessageConversation;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.dto.ConversationVO;
import com.example.usercenter.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageServiceImpl extends ServiceImpl<MessageConversationMapper, MessageConversation> implements MessageService {

    private final MessageConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageConversation getOrCreateConversation(Long user1Id, Long user2Id) {
        Long smallId = Math.min(user1Id, user2Id);
        Long bigId = Math.max(user1Id, user2Id);
        MessageConversation conv = this.getOne(new LambdaQueryWrapper<MessageConversation>()
                .eq(MessageConversation::getUser1Id, smallId)
                .eq(MessageConversation::getUser2Id, bigId));
        if (conv == null) {
            conv = new MessageConversation();
            conv.setUser1Id(smallId);
            conv.setUser2Id(bigId);
            conv.setCreateTime(new Date());
            try {
                this.save(conv);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发创建同一会话时，数据库唯一约束兜底，重新查询
                conv = this.getOne(new LambdaQueryWrapper<MessageConversation>()
                        .eq(MessageConversation::getUser1Id, smallId)
                        .eq(MessageConversation::getUser2Id, bigId));
            }
        }
        return conv;
    }

    @Override
    public PageResult<ConversationVO> getConversationList(Long userId, int page, int pageSize) {
        IPage<MessageConversation> pageResult = this.page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<MessageConversation>()
                        .and(w -> w.eq(MessageConversation::getUser1Id, userId)
                                .or().eq(MessageConversation::getUser2Id, userId))
                        .orderByDesc(MessageConversation::getLastMessageTime));

        List<ConversationVO> voList = pageResult.getRecords().stream().map(conv -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(conv.getId());
            vo.setLastMessage(conv.getLastMessageContent());
            vo.setLastMessageTime(conv.getLastMessageTime());

            // 确定对方用户ID
            Long otherUserId = conv.getUser1Id().equals(userId) ? conv.getUser2Id() : conv.getUser1Id();
            vo.setOtherUserId(otherUserId);

            // 查询对方用户信息
            User otherUser = userMapper.selectById(otherUserId);
            if (otherUser != null) {
                vo.setOtherUsername(otherUser.getUsername());
                vo.setOtherAvatarUrl(otherUser.getAvatarUrl());
            }

            // 查询该会话的未读消息数
            long unreadCount = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                    .eq(Message::getConversationId, conv.getId())
                    .eq(Message::getIsRead, 0)
                    .ne(Message::getSenderId, userId));
            vo.setUnreadCount((int) unreadCount);

            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize(), voList);
    }

    @Override
    public PageResult<Message> getMessageHistory(Long conversationId, Long userId, int page, int pageSize) {
        MessageConversation conv = this.getById(conversationId);
        if (conv == null || (!conv.getUser1Id().equals(userId) && !conv.getUser2Id().equals(userId))) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权访问该会话");
        }
        IPage<Message> pageResult = messageMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, conversationId)
                        .orderByDesc(Message::getCreateTime));
        return new PageResult<>(pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize(), pageResult.getRecords());
    }

    @Override
    @Transactional
    public Message sendMessage(Long senderId, Long receiverId, String content) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能给自己发消息");
        }
        MessageConversation conv = getOrCreateConversation(senderId, receiverId);
        Message msg = new Message();
        msg.setConversationId(conv.getId());
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setIsRead(0);
        msg.setCreateTime(new Date());
        messageMapper.insert(msg);
        conv.setLastMessageContent(content.length() > 100 ? content.substring(0, 100) + "..." : content);
        conv.setLastMessageTime(new Date());
        this.updateById(conv);
        return msg;
    }

    @Override
    public long getUnreadCount(Long userId) {
        return messageMapper.selectUnreadCount(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markConversationRead(Long conversationId, Long userId) {
        MessageConversation conv = this.getById(conversationId);
        if (conv == null || (!conv.getUser1Id().equals(userId) && !conv.getUser2Id().equals(userId))) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权访问该会话");
        }
        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .eq(Message::getIsRead, 0)
                .ne(Message::getSenderId, userId)
                .set(Message::getIsRead, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationVO getOrCreateConversationVO(Long userId, Long otherUserId) {
        if (otherUserId == null || otherUserId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标用户ID不能为空");
        }
        User otherUser = userMapper.selectById(otherUserId);
        if (otherUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "目标用户不存在");
        }
        MessageConversation conv = getOrCreateConversation(userId, otherUserId);
        ConversationVO vo = new ConversationVO();
        vo.setId(conv.getId());
        vo.setLastMessage(conv.getLastMessageContent());
        vo.setLastMessageTime(conv.getLastMessageTime());
        vo.setOtherUserId(otherUserId);
        vo.setOtherUsername(otherUser.getUsername());
        vo.setOtherAvatarUrl(otherUser.getAvatarUrl());
        long unreadCount = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conv.getId())
                .eq(Message::getIsRead, 0)
                .ne(Message::getSenderId, userId));
        vo.setUnreadCount((int) unreadCount);
        return vo;
    }
}
