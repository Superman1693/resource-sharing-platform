package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.SignInRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SignInRecordMapper extends BaseMapper<SignInRecord> {
}
