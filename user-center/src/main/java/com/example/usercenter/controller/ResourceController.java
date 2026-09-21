package com.example.usercenter.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.example.usercenter.annotation.AdminRequired;
import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.annotation.PreventDuplicate;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.Resource;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.service.ResourceService;
import com.example.usercenter.utils.AliyunOSSOperator;
import com.example.usercenter.utils.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资源接口
 * 
 * @author zy
 */
@RestController
@RequestMapping("/resource")
@Slf4j
public class ResourceController extends BaseController {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    /**
     * 获取资源列表（免登录浏览）
     */
    @GetMapping("/list")
    public BaseResponse<List<Resource>> getResourceList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        // 浏览资源列表不需要登录

        List<Resource> resources = resourceService.getResourceList(keyword, tag, category, userId, status);
        return ResultUtils.success(resources);
    }

    /**
     * 获取当前用户上传的资源（分页，"我的资源"专属页）
     */
    @GetMapping("/my")
    @LoginRequired
    public BaseResponse<PageResult<Resource>> getMyResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }
        Long userId = UserContext.get().getUserId();
        PageResult<Resource> result = resourceService.getMyResources(userId, page, pageSize);
        return ResultUtils.success(result);
    }

    /**
     * 获取资源详情（免登录浏览）
     */
    @GetMapping("/{id}")
    public BaseResponse<Resource> getResourceDetail(@PathVariable Long id, HttpServletRequest request) {
        Resource resource = resourceService.getResourceDetail(id, request);
        return ResultUtils.success(resource);
    }

    /**
     * 添加资源（需要登录）
     */
    @PostMapping("/add")
    @LoginRequired
    @PreventDuplicate(waitTime = 0, leaseTime = 5, message = "提交过于频繁，请稍后再试")
    public BaseResponse<Resource> addResource(@RequestBody Resource resource, HttpServletRequest request) {
        // 参数校验
        if (resource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源信息不能为空");
        }
        if (StringUtils.isBlank(resource.getTitle())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源标题不能为空");
        }
        if (StringUtils.isBlank(resource.getCategory())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源分类不能为空");
        }
        Resource createdResource = resourceService.addResource(resource, request);
        return ResultUtils.success(createdResource);
    }

    /**
     * 更新资源（需要登录）
     */
    @PutMapping("/update/{id}")
    @LoginRequired
    public BaseResponse<Boolean> updateResource(@PathVariable Long id, @RequestBody Resource resource,
            HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不能为空");
        }

        if (resource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源信息不能为空");
        }

        boolean result = resourceService.updateResource(id, resource, request);
        return ResultUtils.success(result);
    }

    /**
     * 删除资源（需要登录）
     */
    @PostMapping("/delete")
    @LoginRequired
    public BaseResponse<Boolean> deleteResource(@RequestBody Map<String, Long> requestBody,
            HttpServletRequest request) {
        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = requestBody.get("id");
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不能为空");
        }

        boolean result = resourceService.deleteResource(id, request);
        return ResultUtils.success(result);
    }

    /**
     * 切换资源状态（需要管理员）
     */
    @PostMapping("/toggleStatus")
    @AdminRequired
    public BaseResponse<Boolean> toggleResourceStatus(@RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        // 仅管理员可操作
        requireAdmin();

        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = getLongValue(requestBody, "id");
        String status = (String) requestBody.get("status");

        if (id == null || status == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID和状态不能为空");
        }

        boolean result = resourceService.toggleResourceStatus(id, status, request);
        return ResultUtils.success(result);
    }

    /**
     * 审核资源（需要管理员）
     */
    @PostMapping("/review")
    @AdminRequired
    public BaseResponse<Boolean> reviewResource(@RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        requireAdmin();

        if (requestBody == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = getLongValue(requestBody, "id");
        String status = (String) requestBody.get("status");

        if (id == null || status == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID和审核状态不能为空");
        }

        boolean result = resourceService.reviewResource(id, status, request);
        return ResultUtils.success(result);
    }

    /**
     * 下载资源（累加下载次数 + 重定向到OSS URL触发浏览器原生下载）
     */
    @GetMapping("/download/{id}")
    public void downloadResource(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // 1. 参数校验 + 查询资源
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不能为空");
        }
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源不存在");
        }
        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        if (!"enabled".equals(resource.getStatus())
                && (loginUser == null || !Integer.valueOf(1).equals(loginUser.getUserRole()))) {
            throw new BusinessException(ErrorCode.NO_AUTH, "资源尚未公开，无法下载");
        }
        String ossDownloadUrl = resource.getDownloadUrl();
        if (StringUtils.isBlank(ossDownloadUrl)) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<h3 style='color:red;'>该资源未配置下载文件，无法下载！</h3>");
            response.getWriter().flush();
            return;
        }

        // 2. 累加下载次数
        boolean countIncreased = resourceService.increaseDownloadCount(id);
        if (!countIncreased) {
            log.warn("资源{}下载次数累加失败", id);
        }

        // 3. 修复中文文件名编码（关键）
        String fileName = resource.getName() != null ? resource.getName() : "resource-" + id;
        // 兼容不同浏览器的文件名编码
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);

        // 4. 重定向到OSS URL
        response.sendRedirect(ossDownloadUrl);
    }

    /**
     * 获取资源文本内容（用于 md/txt/code 在线预览，后端拉取 OSS 绕过前端 CORS 限制）
     * 仅 enabled 资源可预览（管理员可预览任意状态）
     */
    @GetMapping("/content/{id}")
    public BaseResponse<String> getResourceContent(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不能为空");
        }
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源不存在");
        }
        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        if (!"enabled".equals(resource.getStatus())
                && (loginUser == null || !Integer.valueOf(1).equals(loginUser.getUserRole()))) {
            throw new BusinessException(ErrorCode.NO_AUTH, "资源尚未公开，无法预览");
        }
        String url = resource.getDownloadUrl();
        if (StringUtils.isBlank(url)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该资源无内容可预览");
        }
        try {
            // 后端拉取 OSS 内容（无 CORS 限制），以 UTF-8 文本返回
            java.net.URL u = new java.net.URL(url);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(u.openStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return ResultUtils.success(sb.toString());
            }
        } catch (Exception e) {
            log.error("拉取资源内容失败 id={}: {}", id, e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "资源内容获取失败");
        }
    }

    /**
     * 在线预览资源（新标签/iframe 内嵌显示）：流式转发 OSS 内容并设 inline，
     * 解决 OSS 对象 Content-Disposition: attachment 导致浏览器直接下载而非预览的问题
     * （download 接口是 302 重定向到 OSS，后端设的头不生效；本接口流式转发，头真正可控）
     */
    @GetMapping("/preview/{id}")
    public void previewResource(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "资源ID不能为空");
        }
        Resource resource = resourceService.getById(id);
        if (resource == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "资源不存在");
        }
        LoginUserDTO loginUser = null;
        try {
            loginUser = getLoginUser();
        } catch (Exception ignored) {
        }
        if (!"enabled".equals(resource.getStatus())
                && (loginUser == null || !Integer.valueOf(1).equals(loginUser.getUserRole()))) {
            throw new BusinessException(ErrorCode.NO_AUTH, "资源尚未公开，无法预览");
        }
        String ossUrl = resource.getDownloadUrl();
        if (StringUtils.isBlank(ossUrl)) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write("<h3 style='color:red;'>该资源未配置文件，无法预览！</h3>");
            response.getWriter().flush();
            return;
        }
        String fileName = resource.getName() != null ? resource.getName() : "resource-" + id;
        String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
        // inline：浏览器内嵌显示（PDF 查看器/文本渲染），而非触发下载
        response.setHeader("Content-Disposition", "inline; filename=\"" + encodedFileName + "\"");
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedFileName);
        // 按文件名后缀推断 Content-Type（PDF 走浏览器查看器必须 application/pdf）；文件名无后缀时用 OSS URL 后缀兜底
        String typeSource = fileName.contains(".") ? fileName : ossUrl;
        response.setContentType(guessContentType(typeSource));
        // 流式转发 OSS 内容（不整文件缓存到内存；预览不计下载次数）
        try (java.io.InputStream in = new java.net.URL(ossUrl).openStream();
             java.io.OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            log.error("资源预览转发失败 id={}: {}", id, e.getMessage());
        }
    }

    /** 按文件名后缀推断 Content-Type（浏览器内嵌预览用） */
    private String guessContentType(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".md")) return "text/markdown; charset=utf-8";
        if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (n.endsWith(".html")) return "text/html; charset=utf-8";
        return "application/octet-stream";
    }

    /**
     * 从Map中获取Long值
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    /**
     * 上传文件到阿里云OSS（需要登录）
     * 
     * @param file    待上传的文件
     * @param request 请求对象（校验登录）
     * @return 包含OSS URL、文件名、文件大小的结果
     */
    @PostMapping("/upload")
    @LoginRequired
    public BaseResponse<Map<String, String>> uploadFileToOSS(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        // 1. 登录校验
        getLoginUser();

        // 2. 文件校验
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        // 大小校验（与前端 ResourceAdd 的 100MB 上限一致）
        if (file.getSize() > 100L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件大小不能超过 100MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }

        try {
            // 3. 上传文件到阿里云OSS
            byte[] fileContent = file.getBytes();
            String ossUrl = aliyunOSSOperator.upload(fileContent, originalFilename);

            // 4. 组装返回结果
            Map<String, String> result = new HashMap<>();
            result.put("downloadUrl", ossUrl); // OSS访问URL（核心返回值）
            result.put("fileName", originalFilename); // 原始文件名
            result.put("fileSize", String.format("%.2f KB", (double) fileContent.length / 1024)); // 文件大小

            return ResultUtils.success(result);
        } catch (Exception e) {
            log.error("文件上传到OSS失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败：" + e.getMessage());
        }
    }
}
