package com.example.usercenter.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 统计数据Mapper接口
 * 
 * @author zy
 */
@Mapper
public interface StatsMapper {
    // 统计数据查询方法在Service层直接使用SQL查询，不需要Mapper方法
}
