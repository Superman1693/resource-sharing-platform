package com.example.usercenter.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.List;

/**
 * tags 字段自定义反序列化器
 * 兼容前端传入数组 ["tag1","tag2"] 或字符串 "[\"tag1\",\"tag2\"]"
 */
public class TagsDeserializer extends StdDeserializer<String> {

    public TagsDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isArray()) {
            // 前端传入数组，序列化为 JSON 字符串存储
            ObjectMapper mapper = (ObjectMapper) p.getCodec();
            List<String> list = mapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            try {
                return mapper.writeValueAsString(list);
            } catch (Exception e) {
                return node.toString();
            }
        } else if (node.isTextual()) {
            // 前端传入字符串，直接返回
            return node.asText();
        } else if (node.isNull()) {
            return null;
        }

        return node.toString();
    }
}
