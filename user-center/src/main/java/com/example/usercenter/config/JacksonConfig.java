package com.example.usercenter.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 统一配置
 *统一 JSON 序列化/反序列化行为
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public ObjectMapper objectMapper() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        // LocalDateTime 序列化/反序列化
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

        return Jackson2ObjectMapperBuilder.json()
                .modules(javaTimeModule)
                .featuresToDisable(
                        // 禁止将日期序列化为时间戳（数字）
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        // 忽略未知属性（前端传多余字段不报错）
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                )
                .featuresToEnable(
                        // 允许空字符串反序列化为 null
                        DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
                )
                .dateFormat(new SimpleDateFormat(DATE_TIME_FORMAT))
                .build();
    }
}
