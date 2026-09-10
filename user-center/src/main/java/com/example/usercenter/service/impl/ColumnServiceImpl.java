package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.NoteColumnMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.StarMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.NoteColumn;
import com.example.usercenter.model.domain.Star;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.ColumnService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 笔记专栏服务实现（星球内合集：作者把系列笔记串成专栏）
 * @author zy
 */
@Service
@Slf4j
public class ColumnServiceImpl extends ServiceImpl<NoteColumnMapper, NoteColumn> implements ColumnService {

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StarMapper starMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public NoteColumn createColumn(NoteColumn column, Long userId) {
        if (column == null || StringUtils.isBlank(column.getTitle())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "专栏标题不能为空");
        }
        if (column.getStarId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "专栏需归属一个星球");
        }
        if (column.getTitle().length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "专栏标题不能超过100字符");
        }
        column.setAuthorId(userId);
        column.setNoteCount(0);
        column.setStatus("active");
        column.setCreateTime(new Date());
        column.setUpdateTime(new Date());
        if (!this.save(column)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建专栏失败");
        }
        return column;
    }

    @Override
    public boolean updateColumn(Long id, NoteColumn column, Long userId) {
        NoteColumn exist = this.getById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "专栏不存在");
        }
        if (!exist.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只能修改自己的专栏");
        }
        if (StringUtils.isNotBlank(column.getTitle())) {
            exist.setTitle(column.getTitle());
        }
        if (column.getDescription() != null) {
            exist.setDescription(column.getDescription());
        }
        if (column.getCoverImage() != null) {
            exist.setCoverImage(column.getCoverImage());
        }
        if (StringUtils.isNotBlank(column.getStatus())) {
            exist.setStatus(column.getStatus());
        }
        exist.setUpdateTime(new Date());
        return this.updateById(exist);
    }

    @Override
    public boolean deleteColumn(Long id, Long userId) {
        NoteColumn exist = this.getById(id);
        if (exist == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "专栏不存在");
        }
        if (!exist.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只能删除自己的专栏");
        }
        // 解绑专栏下所有笔记（collection_id 置空）
        noteMapper.update(null, new UpdateWrapper<Note>()
                .eq("collection_id", id)
                .set("collection_id", null)
                .set("collection_sort", 0));
        return this.removeById(id);
    }

    @Override
    public List<NoteColumn> listColumns(Long starId, Long authorId, boolean includeArchived) {
        QueryWrapper<NoteColumn> wrapper = new QueryWrapper<>();
        if (starId != null) {
            wrapper.eq("star_id", starId);
        }
        if (authorId != null) {
            wrapper.eq("author_id", authorId);
        }
        if (!includeArchived) {
            wrapper.eq("status", "active");
        }
        wrapper.orderByDesc("update_time");
        List<NoteColumn> columns = this.list(wrapper);
        fillColumnInfoBatch(columns);
        return columns;
    }

    @Override
    public NoteColumn getColumnDetail(Long id) {
        NoteColumn column = this.getById(id);
        if (column == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "专栏不存在");
        }
        fillColumnInfoBatch(List.of(column));
        // 查专栏下 published 笔记，按章节序号升序
        List<Note> notes = noteMapper.selectList(new QueryWrapper<Note>()
                .eq("collection_id", id)
                .eq("status", "published")
                .eq("is_delete", 0)
                .orderByAsc("collection_sort"));
        fillNoteBatch(notes);
        column.setNotes(notes);
        return column;
    }

    @Override
    public boolean addNoteToColumn(Long collectionId, Long noteId, Integer sortOrder, Long userId) {
        NoteColumn column = this.getById(collectionId);
        if (column == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "专栏不存在");
        }
        if (!column.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只能往自己的专栏添加笔记");
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        if (!note.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "只能添加自己的笔记");
        }
        if (note.getCollectionId() != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该笔记已归属其他专栏，请先移出");
        }
        if (!"published".equals(note.getStatus())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只有已发布笔记可加入专栏");
        }
        int sort = sortOrder == null ? calcNextSort(collectionId) : sortOrder;
        noteMapper.update(null, new UpdateWrapper<Note>()
                .eq("id", noteId)
                .set("collection_id", collectionId)
                .set("collection_sort", sort));
        recalcNoteCount(collectionId);
        return true;
    }

    @Override
    public boolean removeNoteFromNote(Long noteId, Long userId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "笔记不存在");
        }
        if (note.getCollectionId() == null) {
            return true;
        }
        Long collectionId = note.getCollectionId();
        // 校验权限：笔记作者或专栏作者
        NoteColumn column = this.getById(collectionId);
        if (column != null && !note.getAuthorId().equals(userId) && !column.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH, "无权限移出该笔记");
        }
        noteMapper.update(null, new UpdateWrapper<Note>()
                .eq("id", noteId)
                .set("collection_id", null)
                .set("collection_sort", 0));
        recalcNoteCount(collectionId);
        return true;
    }

    @Override
    public List<NoteColumn> listMyColumns(Long userId) {
        return listColumns(null, userId, true);
    }

    /** 重算专栏 published 笔记数 */
    private void recalcNoteCount(Long collectionId) {
        long count = noteMapper.selectCount(new QueryWrapper<Note>()
                .eq("collection_id", collectionId)
                .eq("status", "published")
                .eq("is_delete", 0));
        NoteColumn update = new NoteColumn();
        update.setId(collectionId);
        update.setNoteCount((int) count);
        this.updateById(update);
    }

    /** 计算专栏末尾序号 + 1 */
    private int calcNextSort(Long collectionId) {
        List<Note> notes = noteMapper.selectList(new QueryWrapper<Note>()
                .eq("collection_id", collectionId)
                .eq("is_delete", 0)
                .orderByDesc("collection_sort")
                .last("LIMIT 1"));
        if (notes.isEmpty()) {
            return 1;
        }
        Integer last = notes.get(0).getCollectionSort();
        return (last == null ? 0 : last) + 1;
    }

    /** 批量填充专栏作者名 + 星球名 */
    private void fillColumnInfoBatch(List<NoteColumn> columns) {
        if (columns == null || columns.isEmpty()) {
            return;
        }
        Set<Long> authorIds = columns.stream().map(NoteColumn::getAuthorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> starIds = columns.stream().map(NoteColumn::getStarId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = authorIds.isEmpty() ? Map.of() :
                userMapper.selectByIds(authorIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Star> starMap = starIds.isEmpty() ? Map.of() :
                starMapper.selectByIds(starIds).stream().collect(Collectors.toMap(Star::getId, s -> s));
        for (NoteColumn c : columns) {
            User u = c.getAuthorId() == null ? null : userMap.get(c.getAuthorId());
            if (u != null) {
                c.setAuthorName(u.getUsername());
            }
            Star s = c.getStarId() == null ? null : starMap.get(c.getStarId());
            if (s != null) {
                c.setStarName(s.getName());
            }
        }
    }

    /** 批量填充笔记作者信息 + 标签列表 */
    private void fillNoteBatch(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Set<Long> authorIds = notes.stream().map(Note::getAuthorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = authorIds.isEmpty() ? Map.of() :
                userMapper.selectByIds(authorIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        for (Note note : notes) {
            User u = note.getAuthorId() == null ? null : userMap.get(note.getAuthorId());
            if (u != null) {
                if (StringUtils.isBlank(note.getAuthor())) {
                    note.setAuthor(u.getUsername());
                }
                note.setAuthorAvatar(u.getAvatarUrl());
            }
            if (StringUtils.isNotBlank(note.getTags())) {
                try {
                    note.setTagList(objectMapper.readValue(note.getTags(), new TypeReference<List<String>>() {}));
                } catch (Exception e) {
                    note.setTagList(java.util.Collections.emptyList());
                }
            } else {
                note.setTagList(java.util.Collections.emptyList());
            }
        }
    }
}
