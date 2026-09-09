package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 查询未读消息数（参数化查询，避免 SQL 注入）
     */
    @Select("SELECT COUNT(*) FROM message m " +
            "INNER JOIN message_conversation mc ON m.conversation_id = mc.id " +
            "WHERE (mc.user1_id = #{userId} OR mc.user2_id = #{userId}) " +
            "AND mc.is_delete = 0 " +
            "AND m.is_read = 0 " +
            "AND m.sender_id != #{userId}")
    long selectUnreadCount(@Param("userId") Long userId);
}
