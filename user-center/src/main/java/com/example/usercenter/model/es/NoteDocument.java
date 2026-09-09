package com.example.usercenter.model.es;

import com.example.usercenter.model.domain.Note;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 笔记 ES 文档
 * 索引名：note_index
 */
@Data
@NoArgsConstructor
public class NoteDocument {

    private Long id;
    private String title;
    private String summary;
    /** 内容截取前 2000 字，避免 ES 文档过大 */
    private String content;
    private String category;
    private String contentType;
    private String author;
    private Long authorId;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private String status;
    private List<String> tagList;
    private Long starId;
    private String coverImage;
    private Date publishTime;
    private Date createTime;
    private Integer isTop;
    private Map<String, List<String>> highlights;

    public static NoteDocument from(Note note) {
        NoteDocument doc = new NoteDocument();
        doc.setId(note.getId());
        doc.setTitle(note.getTitle());
        doc.setSummary(note.getSummary());
        // 内容截取，防止文档过大
        String content = note.getContent();
        doc.setContent(content != null && content.length() > 2000 ? content.substring(0, 2000) : content);
        doc.setCategory(note.getCategory());
        doc.setContentType(note.getContentType());
        doc.setAuthor(note.getAuthor());
        doc.setAuthorId(note.getAuthorId());
        doc.setViewCount(note.getViewCount());
        doc.setLikeCount(note.getLikeCount());
        doc.setCommentCount(note.getCommentCount());
        doc.setStatus(note.getStatus());
        doc.setTagList(note.getTagList());
        doc.setStarId(note.getStarId());
        doc.setCoverImage(note.getCoverImage());
        doc.setPublishTime(note.getPublishTime());
        doc.setCreateTime(note.getCreateTime());
        doc.setIsTop(note.getIsTop());
        return doc;
    }
}
