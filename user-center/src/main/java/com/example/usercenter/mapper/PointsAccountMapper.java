package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.PointsAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PointsAccountMapper extends BaseMapper<PointsAccount> {

    /**
     * 原子累加积分（不存在则插入）。
     *
     * <p>用于替代「selectById → updateById(balance + change)」这种读-改-写写法：
     * 后者在并发（点赞/评论/发布同时加分）时会丢失更新。</p>
     *
     * <p>余额用 {@code GREATEST(balance + change, 0)} 兜底，防止历史脏数据被扣成负数；
     * {@code total_earned} 只累加正数部分。</p>
     *
     * @param userId 用户ID
     * @param change 变动值（正为增加，负为扣减）
     * @return 受影响行数
     */
    @Insert("INSERT INTO points_account (user_id, balance, total_earned, update_time) "
            + "VALUES (#{userId}, GREATEST(#{change}, 0), GREATEST(#{change}, 0), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "balance = GREATEST(balance + #{change}, 0), "
            + "total_earned = total_earned + GREATEST(#{change}, 0), "
            + "update_time = NOW()")
    int incrementAtomically(@Param("userId") Long userId, @Param("change") int change);
}
