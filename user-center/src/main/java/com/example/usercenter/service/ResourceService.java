package com.example.usercenter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.Resource;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 资源服务接口
 * @author zy
 */
public interface ResourceService extends IService<Resource> {
    /**
     * 获取资源列表
     */
    List<Resource> getResourceList(String keyword, String tag, String category, Long userId, String status);

    /**
     * 获取资源详情
     */
    Resource getResourceDetail(Long id, HttpServletRequest request);

    /**
     * 添加资源
     */
    Resource addResource(Resource resource, HttpServletRequest request);

    /**
     * 更新资源
     */
    boolean updateResource(Long id, Resource resource, HttpServletRequest request);

    /**
     * 删除资源
     */
    boolean deleteResource(Long id, HttpServletRequest request);

    /**
     * 切换资源状态
     */
    boolean toggleResourceStatus(Long id, String status, HttpServletRequest request);

    /**
     * 审核资源（管理员）
     */
    boolean reviewResource(Long id, String status, HttpServletRequest request);




    /**
     * 累加下载次数+返回完整资源对象
     * @param id
     * @return
     */
    boolean increaseDownloadCount(Long id);

    /**
     * 获取当前用户上传的资源（分页，"我的资源"专属页）
     */
    PageResult<Resource> getMyResources(Long userId, Integer page, Integer pageSize);
}
