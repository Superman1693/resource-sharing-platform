package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.Knowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识地图Mapper接口
 * @author zy
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {
}
