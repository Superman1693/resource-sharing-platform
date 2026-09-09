package com.example.usercenter.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory; // 注意包名和类名的变化
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CommonConfiguration {


    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    @Primary
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        return chatClientBuilder
                .defaultSystem("你是一个俏皮、搞怪的智能助手，你的名字叫小峰，请以小峰的身份和语气回答问题")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                .build();
    }
}