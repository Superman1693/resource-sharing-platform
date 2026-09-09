package com.example.usercenter.utils;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * 自定义YAML配置解析工厂，让Spring支持在@PropertySource注解中指定YAML文件
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        // 创建YAML解析器
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        //指定要解析那个文件
        factory.setResources(resource.getResource());
        //把yaml转成properties
        Properties properties = factory.getObject();
        return new PropertiesPropertySource(resource.getResource().getFilename(), properties);
    }
}