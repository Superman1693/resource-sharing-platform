package com.example.usercenter.model.domain.request;

import lombok.Data;

@Data
public class NoteQueryRequest {
    /** 页码，默认1 */
    private Integer page = 1;
    /** 每页数量，默认10，最大50 */
    private Integer pageSize = 10;
    /** 关键词 */
    private String keyword;
    /** 分类 */
    private String category;
    /** 内容类型 */
    private String contentType;
    /** 排序方式：hot（默认）/ latest */
    private String sortType = "hot";
    /** 作者用户ID（筛选指定用户的笔记） */
    private Long userId;
}
