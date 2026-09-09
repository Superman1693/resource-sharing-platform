package com.example.usercenter.annotation;

import com.example.usercenter.serializer.MaskType;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义数据脱敏注解
 * 标注在需要脱敏的字段上，配合 MaskSensitiveSerializer 使用
 *
 * 用法:
 *   @MaskSensitive(MaskType.PHONE)
 *   private String phone;
 */
//注解只能加在字段上
@Target(ElementType.FIELD)
//程序运行时，这个注解还存在，能被读到
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = com.example.usercenter.serializer.MaskSensitiveSerializer.class)
public @interface MaskSensitive {
    /**
     * 脱敏类型
     */
    MaskType value();
}
