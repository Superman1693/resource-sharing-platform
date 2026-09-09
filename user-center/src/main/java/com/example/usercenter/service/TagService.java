package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.domain.Tag;

import java.util.List;

/**
 * 标签服务
 */
public interface TagService extends IService<Tag> {

    /** 标签广场（usage_count>0，按使用次数降序） */
    List<Tag> listTags();

    /** 某标签下的笔记列表（分页） */
    PageResult<Note> getNotesByTag(String name, int page, int pageSize);

    /** 标签联想（前缀匹配，返回 name 列表） */
    List<String> suggest(String q);

    /** 同步笔记的标签关联（diff 新旧标签列表，增删 note_tag + 调整 usage_count） */
    void syncNoteTags(Long noteId, List<String> oldTags, List<String> newTags);

    /** 从 note.tags JSON 回填到 tag + note_tag 关联表（管理员，幂等：先清后建） */
    int migrateFromJson();
}
