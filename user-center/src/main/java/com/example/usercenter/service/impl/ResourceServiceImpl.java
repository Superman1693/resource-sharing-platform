package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.ResourceMapper;
import com.example.usercenter.model.domain.Resource;
import com.example.usercenter.utils.SensitiveWordChecker;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.service.EsSearchService;
import com.example.usercenter.service.ResourceService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.example.usercenter.contant.UserConstant.ADMIN_ROLE;
import static com.example.usercenter.contant.UserConstant.USER_LOGIN_STATE;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.UserContext;

/**
 * 资源服务实现类
 * @author zy
 */
@Service
@Slf4j
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private EsSearchService esSearchService;

    @Autowired
    private SensitiveWordChecker sensitiveWordChecker;

    @Override
    public List<Resource> getResourceList(String keyword, String tag, String category, Long userId, String status) {
        QueryWrapper<Resource> queryWrapper = new QueryWrapper<>();

        if (StringUtils.isNotBlank(keyword)) {
            queryWrapper.and(wrapper -> wrapper.like("name", keyword).or().like("title", keyword).or().like("description", keyword));
        }
        // 类型筛选：数据实际存于 resource_type 列（tag 列已废弃、全为 NULL）
        if (StringUtils.isNotBlank(tag)) {
            queryWrapper.eq("resource_type", tag);
        }
        if (StringUtils.isNotBlank(category)) {
            queryWrapper.eq("category", category);
        }
        if (userId != null) {
            queryWrapper.eq("uploader_id", userId);
        }
        if (StringUtils.isNotBlank(status)) {
            queryWrapper.eq("status", status);
        } else {
            queryWrapper.eq("status", "enabled"); // 默认只查询启用的
        }

        queryWrapper.orderByDesc("create_time");

        List<Resource> resources = this.list(queryWrapper);
        // 处理标签
        return resources.stream().map(resource -> {
            processResourceTags(resource);
            resource.setFileSize(formatFileSize(resource.getFileSizeBytes()));
            return resource;
        }).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Resource getResourceDetail(Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不能为空");
        }

        Resource resource = this.getById(id);
        if (resource == null || (resource.getIsDelete() != null && resource.getIsDelete() == 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源不存在");
        }

        User loginUser = null;
        try {
            loginUser = getLoginUser(request);
        } catch (Exception ignored) {
        }
        if ((loginUser == null || !isAdmin(loginUser)) && !"enabled".equals(resource.getStatus())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "资源尚未公开");
        }

        processResourceTags(resource);
        resource.setFileSize(formatFileSize(resource.getFileSizeBytes()));
        return resource;
    }


    @Override
    public Resource addResource(Resource resource, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }

        // 参数校验（原有逻辑保留）
        if (resource == null || StringUtils.isBlank(resource.getTitle()) || StringUtils.isBlank(resource.getDescription())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题和描述不能为空");
        }
        if (StringUtils.isBlank(resource.getResourceType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源类型不能为空");
        }
        if (StringUtils.isBlank(resource.getCategory())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类不能为空");
        }
        // 敏感词校验（名称/标题/描述）
        sensitiveWordChecker.check("资源名称或描述",
                resource.getName(), resource.getTitle(), resource.getDescription());

        // 设置默认值（原有逻辑保留）
        if (StringUtils.isBlank(resource.getName())) {
            resource.setName(resource.getTitle());
        }
        resource.setDownloadCount(0);
        resource.setStatus(StringUtils.isBlank(resource.getStatus()) ? "pending" : resource.getStatus());
        resource.setIsPublic(resource.getIsPublic() != null ? resource.getIsPublic() : true);
        // 记录上传者归属（用于"我的资源"查询与统计）
        resource.setUploaderId(loginUser.getId());
        resource.setCreateTime(new Date());
        resource.setUpdateTime(new Date());

        // ========== 修复标签存储逻辑 ==========
        // 处理标签：兼容tag（单个标签）和tagList（标签列表）
        List<String> finalTagList = new ArrayList<>();

        // 1. 优先使用tagList（前端传入的标签列表）
        if (resource.getTagList() != null && !resource.getTagList().isEmpty()) {
            finalTagList.addAll(resource.getTagList());
        }
        // 2. 兼容单个tag字段（如果有值）
        if (StringUtils.isNotBlank(resource.getTag())) {
            finalTagList.add(resource.getTag().trim());
        }

        // 3. 统一序列化为JSON数组字符串（关键：保证存储格式一致）
        if (!finalTagList.isEmpty()) {
            try {
                resource.setTags(objectMapper.writeValueAsString(finalTagList));
            } catch (Exception e) {
                log.error("标签序列化失败", e);
                // 序列化失败时，存储为普通字符串（兜底）
                resource.setTags(String.join(",", finalTagList));
            }
        } else {
            // 无标签时，设置为空字符串（避免null）
            resource.setTags("");
        }

        // 保存资源（原有逻辑）
        boolean saved = this.save(resource);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "添加资源失败");
        }

        // 处理标签返回（修复后的方法）
        processResourceTags(resource);
        resource.setFileSize(formatFileSize(resource.getFileSizeBytes()));
        // 同步到 ES
        esSearchService.indexResource(resource);
        return resource;
    }

    // ResourceServiceImpl.java - updateResource方法
    @Override
    public boolean updateResource(Long id, Resource resource, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null || !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        Resource oldResource = this.getById(id);
        if (oldResource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源不存在");
        }

        // 敏感词校验（仅校验本次提交的字段）
        sensitiveWordChecker.check("资源名称或描述",
                resource.getName(), resource.getTitle(), resource.getDescription());

        // 原有更新逻辑...
        if (StringUtils.isNotBlank(resource.getTitle())) {
            oldResource.setTitle(resource.getTitle());
        }
        // ... 省略其他字段更新

        // ========== 修复标签更新逻辑 ==========
        List<String> finalTagList = new ArrayList<>();
        // 1. 优先使用tagList
        if (resource.getTagList() != null && !resource.getTagList().isEmpty()) {
            finalTagList.addAll(resource.getTagList());
        }
        // 2. 兼容单个tag字段
        if (StringUtils.isNotBlank(resource.getTag())) {
            finalTagList.add(resource.getTag().trim());
        }
        // 3. 统一序列化为JSON数组
        if (!finalTagList.isEmpty()) {
            try {
                oldResource.setTags(objectMapper.writeValueAsString(finalTagList));
            } catch (Exception e) {
                log.error("标签序列化失败", e);
                oldResource.setTags(String.join(",", finalTagList));
            }
        } else if (resource.getTag() == null && resource.getTagList() == null) {
            // 未传标签时，保留原有值
        } else {
            // 主动清空标签
            oldResource.setTags("");
        }

        oldResource.setUpdateTime(new Date());
        return this.updateById(oldResource);
    }

    @Override
    public boolean deleteResource(Long id, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null || !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        Resource resource = this.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源不存在");
        }

        boolean result = this.removeById(id);
        // 从 ES 删除
        if (result) esSearchService.deleteResource(id);
        return result;
    }

    @Override
    public boolean toggleResourceStatus(Long id, String status, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null || !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        Resource resource = this.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源不存在");
        }

        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态值不正确");
        }

        resource.setStatus(status);
        resource.setUpdateTime(new Date());
        return this.updateById(resource);
    }

    @Override
    public boolean reviewResource(Long id, String status, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        if (loginUser == null || !isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH);
        }

        Resource resource = this.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源不存在");
        }

        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态只能是 enabled 或 disabled");
        }

        resource.setStatus(status);
        resource.setUpdateTime(new Date());
        return this.updateById(resource);
    }

    @Override
    public boolean increaseDownloadCount(Long id) {
        // 使用原子递增避免并发丢失更新
        return resourceMapper.incrementDownloadCount(id) > 0;
    }

    /**
     * 获取当前用户上传的资源（分页，用于"我的资源"专属页）
     */
    @Override
    public PageResult<Resource> getMyResources(Long userId, Integer page, Integer pageSize) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        int pageNum = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);

        Page<Resource> pageParam = new Page<>(pageNum, size);
        QueryWrapper<Resource> wrapper = new QueryWrapper<>();
        wrapper.eq("uploader_id", userId).orderByDesc("create_time");
        IPage<Resource> result = this.page(pageParam, wrapper);

        List<Resource> records = result.getRecords();
        records.forEach(resource -> {
            processResourceTags(resource);
            resource.setFileSize(formatFileSize(resource.getFileSizeBytes()));
        });
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }



    /**
     * 处理标签（JSON字符串和List互转）
     */
    // ResourceServiceImpl.java
    private void processResourceTags(Resource resource) {
        if (StringUtils.isNotBlank(resource.getTags())) {
            try {
                // 方案1：兼容普通字符串和JSON数组
                String tagsStr = resource.getTags().trim();
                List<String> tagList = new ArrayList<>();

                // 判断是否为JSON数组格式（以[开头，以]结尾）
                if (tagsStr.startsWith("[") && tagsStr.endsWith("]")) {
                    // 是JSON数组，正常反序列化
                    tagList = objectMapper.readValue(tagsStr, new TypeReference<List<String>>() {});
                } else {
                    // 是普通字符串，直接作为单个标签存入列表
                    tagList.add(tagsStr);
                }
                resource.setTagList(tagList);
            } catch (Exception e) {
                log.error("标签反序列化失败", e);
                // 解析失败时，将原字符串作为单个标签返回，避免空列表
                resource.setTagList(Collections.singletonList(resource.getTags()));
            }
        } else {
            resource.setTagList(Collections.emptyList());
        }
    }

    private String formatFileSize(Long sizeBytes) {
        if (sizeBytes == null || sizeBytes <= 0) {
            return "--";
        }
        double size = sizeBytes.doubleValue();
        if (size < 1024) {
            return String.format("%.0f B", size);
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024);
        }
        if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / 1024 / 1024);
        }
        return String.format("%.1f GB", size / 1024 / 1024 / 1024);
    }

    /**
     * 获取登录用户
     */
    private User getLoginUser(HttpServletRequest request) {
        LoginUserDTO dto = UserContext.get();
        if (dto == null) return null;
        User user = new User();
        user.setId(dto.getUserId());
        user.setUserRole(dto.getUserRole());
        return user;
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(User user) {
        return user != null && user.getUserRole() != null && user.getUserRole() == ADMIN_ROLE;
    }
}
