package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 评论Mapper接口
 * @author zy
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 原子递增评论点赞数（避免并发丢失更新）
     */
    @Update("UPDATE comment SET like_count = like_count + 1, update_time = NOW() WHERE id = #{id}")
    int incrementLikeCount(@Param("id") Long id);
}
