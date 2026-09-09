package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.usercenter.model.domain.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 笔记Mapper接口
 * @author zy
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 带条件的分页查询（含置顶排序）
     */
    IPage<Note> selectPageWithCondition(
        IPage<Note> page,
        @Param("keyword") String keyword,
        @Param("category") String category,
        @Param("contentType") String contentType,
        @Param("sortType") String sortType,
        @Param("userId") Long userId
    );

    /** 原子递增点赞数 */
    @Update("UPDATE note SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLikeCount(@Param("id") Long id);

    /** 原子递增浏览量 */
    @Update("UPDATE note SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /** 原子递增评论数 */
    @Update("UPDATE note SET comment_count = comment_count + 1 WHERE id = #{id}")
    int incrementCommentCount(@Param("id") Long id);

    /** 原子批量递增浏览量（用于 Redis 缓冲刷新） */
    @Update("UPDATE note SET view_count = view_count + #{delta} WHERE id = #{id}")
    int incrementViewCountByDelta(@Param("id") Long id, @Param("delta") long delta);
}
