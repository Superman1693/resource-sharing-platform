package com.example.usercenter.serializer;

import com.example.usercenter.annotation.MaskSensitive;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.util.Objects;

/**
 * 数据脱敏序列化器，在接口返回JSON时，自动给敏感信息脱敏
 * 根据 MaskType 对敏感数据进行脱敏处理
 * JsonSerializer：Jackson 序列化器，负责把 Java 对象 → JSON
 * ContextualSerializer：让序列化器能拿到字段上的注解
 */
public class MaskSensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private MaskType maskType;

    public MaskSensitiveSerializer() {
    }

    public MaskSensitiveSerializer(MaskType maskType) {
        this.maskType = maskType;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        //调用脱敏方法
        gen.writeString(mask(value, maskType));
    }

    /**
     *
     * 读取注解
     * @param prov
     * @param property
     * @return
     * @throws JsonMappingException
     */
    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) throws JsonMappingException {
        if (property != null) {
            //获取字典上的@MaskSensitive 注解
            MaskSensitive annotation = property.getAnnotation(MaskSensitive.class);
            if (annotation != null) {
                // 根据注解类型，创建对应的序列化器
                return new MaskSensitiveSerializer(annotation.value());
            }
        }
        return this;
    }

    /**
     * 执行脱敏
     */
    private static String mask(String value, MaskType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case NAME -> maskName(value);
        };
    }

    /**
     * 手机号脱敏: 13812345678 → 138****5678
     */
    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏: zhangsan@qq.com → z***@qq.com
     */
    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    /**
     * 身份证脱敏: 110105199001011234 → 110***********1234
     */
    private static String maskIdCard(String idCard) {
        if (idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 3) + "*".repeat(idCard.length() - 7) + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡脱敏: 6222021234567890123 → **** **** **** 0123
     */
    private static String maskBankCard(String bankCard) {
        if (bankCard.length() < 4) {
            return bankCard;
        }
        return "**** **** **** " + bankCard.substring(bankCard.length() - 4);
    }

    /**
     * 姓名脱敏: 张三 → 张* / 张三四 → 张*三
     */
    private static String maskName(String name) {
        if (name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.substring(0, 1) + "*";
        }
        return name.substring(0, 1) + "*".repeat(name.length() - 2) + name.substring(name.length() - 1);
    }
}
