package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.NoteCollectionMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.NoteCollection;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 笔记收藏服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionServiceImpl extends ServiceImpl<NoteCollectionMapper, NoteCollection> implements CollectionService {

    private final NoteMapper noteMapper;
    private final UserMapper userMapper;

    @Override
    public boolean toggleCollect(Long userId, Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "笔记ID非法");
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null || !"published".equals(note.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在或已下架");
        }
        boolean exists = this.count(new LambdaQueryWrapper<NoteCollection>()
                .eq(NoteCollection::getUserId, userId)
                .eq(NoteCollection::getNoteId, noteId)) > 0;
        if (exists) {
            this.remove(new LambdaQueryWrapper<NoteCollection>()
                    .eq(NoteCollection::getUserId, userId)
                    .eq(NoteCollection::getNoteId, noteId));
            return false;
        }
        NoteCollection c = new NoteCollection();
        c.setUserId(userId);
        c.setNoteId(noteId);
        c.setCreateTime(new Date());
        this.save(c);
        return true;
    }

    @Override
    public boolean isCollected(Long userId, Long noteId) {
        if (userId == null || noteId == null) return false;
        return this.count(new LambdaQueryWrapper<NoteCollection>()
                .eq(NoteCollection::getUserId, userId)
                .eq(NoteCollection::getNoteId, noteId)) > 0;
    }

    @Override
    public PageResult<Note> getMyCollected(Long userId, int page, int pageSize) {
        IPage<NoteCollection> cPage = this.page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<NoteCollection>()
                        .eq(NoteCollection::getUserId, userId)
                        .orderByDesc(NoteCollection::getCreateTime));
        List<Long> noteIds = cPage.getRecords().stream()
                .map(NoteCollection::getNoteId).collect(Collectors.toList());
        if (noteIds.isEmpty()) {
            return new PageResult<>(0, page, pageSize, Collections.emptyList());
        }
        // selectBatchIds 已被 @TableLogic 过滤逻辑删除；再过滤未发布
        List<Note> notes = noteMapper.selectBatchIds(noteIds);
        Map<Long, Note> map = notes.stream()
                .filter(n -> "published".equals(n.getStatus()))
                .collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));
        // 按收藏时间顺序重排
        List<Note> ordered = noteIds.stream()
                .filter(map::containsKey)
                .map(map::get)
                .collect(Collectors.toList());
        fillAuthorInfoBatch(ordered);
        return new PageResult<>(cPage.getTotal(), page, pageSize, ordered);
    }

    /**
     * 批量填充作者信息（消除 N+1）
     */
    private void fillAuthorInfoBatch(List<Note> notes) {
        if (notes == null || notes.isEmpty()) return;
        Set<Long> authorIds = new HashSet<>();
        notes.forEach(n -> { if (n.getAuthorId() != null) authorIds.add(n.getAuthorId()); });
        if (authorIds.isEmpty()) return;
        Map<Long, User> userMap = userMapper.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        for (Note note : notes) {
            if (note.getAuthorId() == null) continue;
            User user = userMap.get(note.getAuthorId());
            if (user == null) continue;
            if (StringUtils.isBlank(note.getAuthor())) note.setAuthor(user.getUsername());
            if (StringUtils.isBlank(note.getAuthorAvatar())) note.setAuthorAvatar(user.getAvatarUrl());
        }
    }
}
