package com.example.usercenter.contant;

public interface NoteConstant {
    /** 笔记状态：已发布 */
    String STATUS_PUBLISHED = "published";
    /** 笔记状态：草稿 */
    String STATUS_DRAFT = "draft";
    /** 笔记状态：待审核 */
    String STATUS_PENDING = "pending";
    /** 笔记状态：已拒绝 */
    String STATUS_REJECTED = "rejected";
    /** 最大置顶数量 */
    int MAX_TOP_COUNT = 10;
}
