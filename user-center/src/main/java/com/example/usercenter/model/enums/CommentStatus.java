package com.example.usercenter.model.enums;

import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.exception.BusinessException;

import java.util.Set;

/**
 * 评论状态枚举
 * @author zy
 */
public enum CommentStatus {

    PENDING("pending", "待审核"),
    APPROVED("approved", "已通过"),
    REJECTED("rejected", "已拒绝"),
    HIDDEN("hidden", "已隐藏");

    private final String code;
    private final String desc;

    CommentStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * Controller 层审核接口可接受的状态（approved / rejected）
     */
    public static final Set<String> APPROVABLE = Set.of(APPROVED.code, REJECTED.code);

    /**
     * 根据 code 获取枚举，找不到时抛 BusinessException
     */
    public static CommentStatus fromCode(String code) {
        for (CommentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的评论状态: " + code);
    }
}
