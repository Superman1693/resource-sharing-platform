package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.StarMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 星球成员Mapper接口
 * @author zy
 */
@Mapper
public interface StarMemberMapper extends BaseMapper<StarMember> {

    /**
     * 物理删除星球成员关系（绕过 @TableLogic 逻辑删除），释放 (star_id, user_id) 唯一键，
     * 以便用户在退出或被移除后可以重新加入（逻辑删除会保留行并占用唯一键，导致重新加入时唯一键冲突）。
     */
    @Delete("DELETE FROM star_member WHERE star_id = #{starId} AND user_id = #{userId}")
    int deletePhysical(@Param("starId") Long starId, @Param("userId") Long userId);
}
