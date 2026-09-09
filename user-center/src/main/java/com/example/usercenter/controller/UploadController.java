package com.example.usercenter.controller;

import com.example.usercenter.annotation.LoginRequired;
import com.example.usercenter.common.BaseResponse;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.ResultUtils;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.dto.LoginUserDTO;
import com.example.usercenter.utils.AliyunOSSOperator;
import com.example.usercenter.utils.UserContext;
import com.example.usercenter.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * 文件上传
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UploadController {

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Autowired
    private UserService userService;

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp3", "mp4", "wav", "avi", "mov"
    );

    /** 最大文件大小：10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** 图片文件扩展名白名单（用于通用图片上传） */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    @PostMapping("/upload")
    @LoginRequired
    public BaseResponse<String> upload(MultipartFile file, HttpServletRequest request) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过10MB");
        }

        // 文件类型白名单校验
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        if (extension.isEmpty() || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的文件类型: " + extension);
        }

        // 文件名清洗：去除路径分隔符和特殊字符
        String safeFilename = sanitizeFilename(originalFilename);

        log.info("文件上传开始, originalName={}, safeName={}, size={}", originalFilename, safeFilename, file.getSize());
        String url = aliyunOSSOperator.upload(file.getBytes(), safeFilename);
        log.info("文件上传OSS成功，URL：{}", url);

        // 将URL写回当前登录用户的头像地址（使用 JWT UserContext）
        LoginUserDTO loginUser = UserContext.get();
        if (loginUser != null && loginUser.getUserId() != null) {
            User toUpdate = new User();
            toUpdate.setId(loginUser.getUserId());
            toUpdate.setAvatarUrl(url);
            boolean updated = userService.updateById(toUpdate);
            if (!updated) {
                log.warn("头像URL入库失败, userId={}, url={}", loginUser.getUserId(), url);
            }
        }

        return ResultUtils.success(url);
    }

    /**
     * 通用图片上传（仅上传并返回 URL，不修改用户头像等任何实体字段）
     * 用于星球封面、笔记封面等场景
     */
    @PostMapping("/upload/image")
    @LoginRequired
    public BaseResponse<String> uploadImage(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        if (extension.isEmpty() || !IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持图片格式: " + extension);
        }
        String safeFilename = sanitizeFilename(originalFilename);
        log.info("图片上传开始, originalName={}, safeName={}, size={}", originalFilename, safeFilename, file.getSize());
        String url = aliyunOSSOperator.upload(file.getBytes(), safeFilename);
        log.info("图片上传OSS成功，URL：{}", url);
        return ResultUtils.success(url);
    }

    /**
     * 提取文件扩展名（不含点号）
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    /**
     * 清洗文件名：移除路径分隔符和危险字符，只保留字母数字中文和部分安全符号
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        // 移除路径部分（防止路径穿越）
        String name = filename;
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        // 只保留安全字符：字母、数字、中文、点、连字符、下划线
        name = name.replaceAll("[^a-zA-Z0-9.\\-_\\u4e00-\\u9fa5]", "_");
        // 防止文件名为空或全是点号
        if (name.isBlank() || name.matches("^\\.+$")) {
            name = "unnamed_" + System.currentTimeMillis();
        }
        return name;
    }
}
