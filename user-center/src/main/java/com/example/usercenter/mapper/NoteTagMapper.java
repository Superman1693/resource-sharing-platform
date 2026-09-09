package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.NoteTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记-标签关联 Mapper
 */
@Mapper
public interface NoteTagMapper extends BaseMapper<NoteTag> {
}
