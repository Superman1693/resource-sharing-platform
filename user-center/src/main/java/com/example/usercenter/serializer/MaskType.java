package com.example.usercenter.serializer;

/**
 * 脱敏类型枚举
 */
public enum MaskType {
    /** 手机号: 138****5678 */
    PHONE,
    /** 邮箱: z***@qq.com */
    EMAIL,
    /** 身份证: 110***********1234 */
    ID_CARD,
    /** 银行卡: **** **** **** 1234 */
    BANK_CARD,
    /** 姓名: 张*三 / 张* */
    NAME
}
