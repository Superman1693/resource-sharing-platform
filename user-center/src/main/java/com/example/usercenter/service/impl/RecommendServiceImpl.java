package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.usercenter.mapper.LikeRecordMapper;
import com.example.usercenter.mapper.NoteMapper;
import com.example.usercenter.mapper.NoteTagMapper;
import com.example.usercenter.mapper.TagMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.mapper.ViewRecordMapper;
import com.example.usercenter.model.domain.LikeRecord;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.NoteTag;
import com.example.usercenter.model.domain.Tag;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.ViewRecord;
import com.example.usercenter.service.RecommendService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 个性化推荐实现：标签偏好 + 热门兜底
 * 有行为记录：按用户历史浏览/点赞笔记的标签偏好推荐同标签笔记，排除已浏览/已点赞
 * 新用户/无行为/结果不足/异常：兜底热门（like_count desc, view_count desc）
 * @author zy
 */
@Service
@Slf4j
public class RecommendServiceImpl implements RecommendService {

    @Resource
    private NoteMapper noteMapper;

    @Resource
    private ViewRecordMapper viewRecordMapper;

    @Resource
    private LikeRecordMapper likeRecordMapper;

    @Resource
    private NoteTagMapper noteTagMapper;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private UserMapper userMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 行为样本上限 */
    private static final int INTERACTION_LIMIT = 100;
    /** 偏好标签数量上限 */
    private static final int PREFERRED_TAG_LIMIT = 5;

    @Override
    public List<Note> recommend(Long userId, int size) {
        if (size <= 0) {
            return Collections.emptyList();
        }
        try {
            // 已交互的笔记 ID（用于排除重复推荐）
            Set<Long> interactedIds = collectInteractedNoteIds(userId);
            List<Note> result = new ArrayList<>();

            // 有行为 → 标签偏好推荐
            if (userId != null && !interactedIds.isEmpty()) {
                List<String> preferredTags = extractPreferredTags(interactedIds);
                if (!preferredTags.isEmpty()) {
                    List<Note> tagNotes = findNotesByTags(preferredTags, size, interactedIds);
                    result.addAll(tagNotes);
                }
            }

            // 不足 → 热门兜底（排除已选 + 已交互）
            if (result.size() < size) {
                Set<Long> exclude = result.stream().map(Note::getId).collect(Collectors.toSet());
                exclude.addAll(interactedIds);
                List<Note> hot = findHotNotes(size - result.size(), exclude);
                result.addAll(hot);
            }

            fillAuthorInfoBatch(result);
            return result;
        } catch (Exception e) {
            log.error("推荐失败，降级热门: userId={}, err={}", userId, e.getMessage());
            return findHotNotes(size, Collections.emptySet());
        }
    }

    /** 收集用户最近浏览/点赞的笔记 ID */
    private Set<Long> collectInteractedNoteIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        // 浏览记录（target_type=note）
        try {
            List<ViewRecord> views = viewRecordMapper.selectList(new QueryWrapper<ViewRecord>()
                    .eq("user_id", userId)
                    .eq("target_type", "note")
                    .eq("is_delete", 0)
                    .orderByDesc("create_time")
                    .last("LIMIT " + INTERACTION_LIMIT));
            views.stream().map(ViewRecord::getNoteId).filter(Objects::nonNull).forEach(ids::add);
        } catch (Exception e) {
            log.warn("查浏览记录失败: {}", e.getMessage());
        }
        // 点赞记录（target_type=note，target_id 即 noteId）
        try {
            List<LikeRecord> likes = likeRecordMapper.selectList(new QueryWrapper<LikeRecord>()
                    .eq("user_id", userId)
                    .eq("target_type", "note")
                    .orderByDesc("create_time")
                    .last("LIMIT " + INTERACTION_LIMIT));
            likes.stream().map(LikeRecord::getTargetId).filter(Objects::nonNull).forEach(ids::add);
        } catch (Exception e) {
            log.warn("查点赞记录失败: {}", e.getMessage());
        }
        return ids;
    }

    /** 从交互笔记的 tags 提取偏好标签（按频次降序取 top N） */
    private List<String> extractPreferredTags(Set<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Note> notes = noteMapper.selectByIds(noteIds);
        Map<String, Integer> freq = new HashMap<>();
        for (Note note : notes) {
            for (String tag : parseTags(note.getTags())) {
                freq.merge(tag, 1, Integer::sum);
            }
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(PREFERRED_TAG_LIMIT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /** 通过偏好标签查 published 笔记，排除已交互，按 like_count 降序 */
    private List<Note> findNotesByTags(List<String> tagNames, int size, Set<Long> excludeIds) {
        // 标签名 → tag_id
        List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>().in("name", tagNames));
        if (tags.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> tagIds = tags.stream().map(Tag::getId).collect(Collectors.toSet());
        // note_tag 关联查 note_id
        List<NoteTag> rels = noteTagMapper.selectList(new QueryWrapper<NoteTag>().in("tag_id", tagIds));
        if (rels.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> noteIds = rels.stream().map(NoteTag::getNoteId).filter(Objects::nonNull).collect(Collectors.toSet());
        // 排除已交互
        noteIds.removeAll(excludeIds);
        if (noteIds.isEmpty()) {
            return Collections.emptyList();
        }
        return noteMapper.selectList(new QueryWrapper<Note>()
                .in("id", noteIds)
                .eq("status", "published")
                .eq("is_delete", 0)
                .orderByDesc("like_count")
                .last("LIMIT " + size));
    }

    /** 热门兜底：like_count desc, view_count desc，排除 excludeIds */
    private List<Note> findHotNotes(int size, Set<Long> excludeIds) {
        QueryWrapper<Note> wrapper = new QueryWrapper<Note>()
                .eq("status", "published")
                .eq("is_delete", 0)
                .orderByDesc("like_count")
                .orderByDesc("view_count")
                .last("LIMIT " + size);
        if (!excludeIds.isEmpty()) {
            wrapper.notIn("id", excludeIds);
        }
        return noteMapper.selectList(wrapper);
    }

    /** 解析 tags JSON 字符串为列表 */
    private List<String> parseTags(String tagsJson) {
        if (StringUtils.isBlank(tagsJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** 批量填充作者信息 + 标签列表 + 发布时间戳 */
    private void fillAuthorInfoBatch(List<Note> notes) {
        if (notes == null || notes.isEmpty()) {
            return;
        }
        Set<Long> authorIds = notes.stream().map(Note::getAuthorId).filter(Objects::nonNull).collect(Collectors.toSet());
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
            note.setTagList(parseTags(note.getTags()));
            if (note.getPublishTime() != null) {
                note.setPublishTimestamp(note.getPublishTime().getTime());
            }
        }
    }
}
