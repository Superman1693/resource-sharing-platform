package com.example.usercenter.model.es;

import com.example.usercenter.model.domain.Resource;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 资源 ES 文档
 * 索引名：resource_index
 */
@Data
@NoArgsConstructor
public class ResourceDocument {

    private Long id;
    private String name;
    private String title;
    private String description;
    private String resourceType;
    private String category;
    private String tag;
    private String status;
    private Integer downloadCount;
    private String downloadUrl;
    private String coverImage;
    private String fileSize;
    private Long fileSizeBytes;
    private List<String> tagList;
    private Date createTime;
    private Date updateTime;
    private Map<String, List<String>> highlights;

    public static ResourceDocument from(Resource resource) {
        ResourceDocument doc = new ResourceDocument();
        doc.setId(resource.getId());
        doc.setName(resource.getName());
        doc.setTitle(resource.getTitle());
        doc.setDescription(resource.getDescription());
        doc.setResourceType(resource.getResourceType());
        doc.setCategory(resource.getCategory());
        doc.setTag(resource.getTag());
        doc.setStatus(resource.getStatus());
        doc.setDownloadCount(resource.getDownloadCount());
        doc.setDownloadUrl(resource.getDownloadUrl());
        doc.setCoverImage(resource.getCoverImage());
        doc.setFileSize(resource.getFileSize());
        doc.setFileSizeBytes(resource.getFileSizeBytes());
        doc.setTagList(resource.getTagList());
        doc.setCreateTime(resource.getCreateTime());
        doc.setUpdateTime(resource.getUpdateTime());
        return doc;
    }
}
