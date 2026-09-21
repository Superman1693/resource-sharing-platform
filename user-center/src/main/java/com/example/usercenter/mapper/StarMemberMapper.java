package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.StarMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /**
     * 查询用户的「默认星球」ID：取最早加入的那个星球，用户未加入任何星球时返回 null。
     *
     * <p>背景：{@code user} 表并没有 star_id 字段，且一个用户可以同时加入多个星球
     * （star_member 是多对多关系），因此不存在「用户唯一的星球」这一属性。
     * 为了让 JWT 能携带一个稳定的租户标识，这里约定「最早加入的星球」为默认星球。</p>
     *
     * <p>注意：{@code StarMember.isDelete} 带 {@code @TableLogic}，但原生 @Select
     * 不会自动追加逻辑删除条件，所以这里显式带上 {@code is_delete = 0}。</p>
     */
    @Select("SELECT star_id FROM star_member WHERE user_id = #{userId} AND is_delete = 0 "
            + "ORDER BY join_time ASC, id ASC LIMIT 1")
    Long selectPrimaryStarId(@Param("userId") Long userId);
}
