package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.NoteCollection;

/**
 * 笔记收藏服务
 */
public interface CollectionService extends IService<NoteCollection> {

    /**
     * 切换收藏状态（已收藏则取消，未收藏则收藏）
     * @return true=已收藏，false=已取消
     */
    boolean toggleCollect(Long userId, Long noteId);

    /**
     * 是否已收藏
     */
    boolean isCollected(Long userId, Long noteId);

    /**
     * 我的收藏列表（分页，按收藏时间倒序）
     */
    PageResult<Note> getMyCollected(Long userId, int page, int pageSize);
}
