package com.example.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.usercenter.model.domain.NoteColumn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 笔记专栏 Mapper（专栏合集表 note_column，区别于收藏表 note_collection 的 NoteCollectionMapper）
 * @author zy
 */
@Mapper
public interface NoteColumnMapper extends BaseMapper<NoteColumn> {
}
