package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.NoteTagMapper;
import com.example.usercenter.mapper.TagMapper;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.NoteTag;
import com.example.usercenter.model.domain.Tag;
import com.example.usercenter.service.TagService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签服务实现
 * 方案B：note.tags JSON 保留（兼容现有 processNoteTags 读取），关联表用于广场聚合与按标签检索
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;
    private final NoteTagMapper noteTagMapper;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Tag> listTags() {
        return tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .gt(Tag::getUsageCount, 0)
                .orderByDesc(Tag::getUsageCount));
    }

    @Override
    public PageResult<Note> getNotesByTag(String name, int page, int pageSize) {
        if (StringUtils.isBlank(name)) {
            return new PageResult<>(0, page, pageSize, Collections.emptyList());
        }
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name.trim()));
        if (tag == null) {
            return new PageResult<>(0, page, pageSize, Collections.emptyList());
        }
        IPage<NoteTag> ntPage = noteTagMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<NoteTag>()
                        .eq(NoteTag::getTagId, tag.getId())
                        .orderByDesc(NoteTag::getCreateTime));
        List<Long> noteIds = ntPage.getRecords().stream().map(NoteTag::getNoteId).collect(Collectors.toList());
        if (noteIds.isEmpty()) {
            return new PageResult<>(0, page, pageSize, Collections.emptyList());
        }
        List<Note> notes = noteMapper.selectBatchIds(noteIds).stream()
                .filter(n -> "published".equals(n.getStatus()))
                .collect(Collectors.toList());
        Map<Long, Note> map = notes.stream().collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));
        // 按关联时间倒序重排
        List<Note> ordered = noteIds.stream().filter(map::containsKey).map(map::get).collect(Collectors.toList());
        return new PageResult<>(ntPage.getTotal(), page, pageSize, ordered);
    }

    @Override
    public List<String> suggest(String q) {
        if (StringUtils.isBlank(q)) return Collections.emptyList();
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>()
                .likeRight(Tag::getName, q.trim())
                .orderByDesc(Tag::getUsageCount)
                .last("LIMIT 10"));
        return tags.stream().map(Tag::getName).collect(Collectors.toList());
    }

    /**
     * diff 新旧标签列表，增删 note_tag + 调整 tag.usage_count
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncNoteTags(Long noteId, List<String> oldTags, List<String> newTags) {
        if (noteId == null) return;
        Set<String> oldSet = oldTags == null ? Collections.emptySet() : new HashSet<>(oldTags);
        Set<String> newSet = newTags == null ? Collections.emptySet() : new HashSet<>(newTags);

        // 移除的标签：删关联 -1 usage_count
        Set<String> toRemove = new HashSet<>(oldSet);
        toRemove.removeAll(newSet);
        for (String name : toRemove) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (tag == null) continue;
            noteTagMapper.delete(new LambdaQueryWrapper<NoteTag>()
                    .eq(NoteTag::getNoteId, noteId).eq(NoteTag::getTagId, tag.getId()));
            tag.setUsageCount(Math.max(0, (tag.getUsageCount() == null ? 0 : tag.getUsageCount()) - 1));
            tagMapper.updateById(tag);
        }

        // 新增的标签：upsert tag +1 usage_count + 插关联（UNIQUE 防重）
        Set<String> toAdd = new HashSet<>(newSet);
        toAdd.removeAll(oldSet);
        for (String name : toAdd) {
            if (StringUtils.isBlank(name)) continue;
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tag.setUsageCount(1);
                tag.setCreateTime(new Date());
                tagMapper.insert(tag);
            } else {
                tag.setUsageCount((tag.getUsageCount() == null ? 0 : tag.getUsageCount()) + 1);
                tagMapper.updateById(tag);
            }
            try {
                NoteTag nt = new NoteTag();
                nt.setNoteId(noteId);
                nt.setTagId(tag.getId());
                nt.setCreateTime(new Date());
                noteTagMapper.insert(nt);
            } catch (DuplicateKeyException e) { /* 关联已存在，忽略 */ }
        }
    }

    /**
     * 从 note.tags JSON 回填到 tag + note_tag（幂等：先清后建）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateFromJson() {
        // 清空关联表与标签表，确保幂等
        noteTagMapper.delete(new LambdaQueryWrapper<>());
        tagMapper.delete(new LambdaQueryWrapper<>());

        List<Note> notes = noteMapper.selectList(new LambdaQueryWrapper<Note>().eq(Note::getStatus, "published"));
        int count = 0;
        for (Note note : notes) {
            List<String> tags = parseTagsJson(note.getTags());
            if (tags.isEmpty()) continue;
            syncNoteTags(note.getId(), Collections.emptyList(), tags);
            count++;
        }
        log.info("标签回填完成，共 {} 篇笔记", count);
        return count;
    }

    private List<String> parseTagsJson(String tagsJson) {
        if (StringUtils.isBlank(tagsJson)) return Collections.emptyList();
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("解析 tags JSON 失败: {}", tagsJson, e);
            return Collections.emptyList();
        }
    }
}
