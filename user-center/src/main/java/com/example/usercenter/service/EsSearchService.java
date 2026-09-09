package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Note;
import com.example.usercenter.model.es.NoteDocument;
import com.example.usercenter.model.es.ResourceDocument;

public interface EsSearchService {

    // ── 笔记 ──────────────────────────────────────────────

    PageResult<NoteDocument> searchNotes(String keyword, String category,
                                         String contentType, String sortType,
                                         int page, int pageSize);

    void indexNote(Note note);

    void deleteNote(Long noteId);

    int syncAllNotes();

    // ── 资源 ──────────────────────────────────────────────

    PageResult<ResourceDocument> searchResources(String keyword, String tag,
                                                  String category, int page, int pageSize);

    /** 使用全限定名避免与 jakarta.annotation.Resource 冲突 */
    void indexResource(com.example.usercenter.model.domain.Resource resource);

    void deleteResource(Long resourceId);

    int syncAllResources();
}
