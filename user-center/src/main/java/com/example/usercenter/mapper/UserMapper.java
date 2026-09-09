package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper接口
 * @author zy
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}
