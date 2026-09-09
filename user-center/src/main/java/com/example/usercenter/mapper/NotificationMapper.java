package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /** 批量标记某用户所有通知为已读 */
    @Update("UPDATE notification SET is_read = 1 WHERE receiver_id = #{userId} AND is_read = 0 AND is_delete = 0")
    int markAllRead(@Param("userId") Long userId);
}
