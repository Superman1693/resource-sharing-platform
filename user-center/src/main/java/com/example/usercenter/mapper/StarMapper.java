package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.Star;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 星球Mapper接口
 * @author zy
 */
@Mapper
public interface StarMapper extends BaseMapper<Star> {

    /**
     * 原子地更新星球内容数量（content_count +/- delta），避免先读后写带来的并发不一致；
     * 使用 GREATEST 兜底，确保不会出现负数。
     */
    @Update("UPDATE star SET content_count = GREATEST(content_count + #{delta}, 0) WHERE id = #{starId}")
    int updateContentCount(@Param("starId") Long starId, @Param("delta") int delta);
}
