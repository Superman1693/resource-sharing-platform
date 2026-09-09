package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.NoteCollection;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记收藏 Mapper
 */
@Mapper
public interface NoteCollectionMapper extends BaseMapper<NoteCollection> {
}
