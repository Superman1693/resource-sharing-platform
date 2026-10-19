package com.example.usercenter.model.enums;

/**
 * 评论状态枚举。
 *
 * <p>评论**不再经过人工审核**：发布时只做敏感词过滤（命中即拒绝发布），
 * 通过校验的评论立即可见。因此只保留「正常 / 已隐藏」两种状态，
 * 原先的 pending（待审核）、rejected（已拒绝）已废弃。</p>
 *
 * @author zy
 */
public enum CommentStatus {

    /** 正常可见 */
    APPROVED("approved", "正常"),

    /** 已隐藏（管理员屏蔽，或因举报被冻结） */
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
}
