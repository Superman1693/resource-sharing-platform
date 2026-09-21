package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.ErrorLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 前端错误日志 Mapper
 *
 * <p>该表为全局表，不参与星球租户隔离。</p>
 */
@Mapper
public interface ErrorLogMapper extends BaseMapper<ErrorLog> {
}
