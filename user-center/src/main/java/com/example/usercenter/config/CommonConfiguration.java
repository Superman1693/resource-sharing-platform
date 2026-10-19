package com.example.usercenter.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory; // 注意包名和类名的变化
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class CommonConfiguration {


    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * AI 调用专用线程池（用于给「非流式」AI 调用加超时）。
     *
     * <p>Spring AI 的 {@code call().content()} 是阻塞调用，上游（如 DashScope）挂起时会
     * 长期占用 Tomcat 工作线程，少量并发即可把线程池打满。放到独立线程池并用
     * {@code get(timeout)} 取结果后，超时即释放，不再拖垮 Web 容器。</p>
     */
    @Bean("aiCallExecutor")
    public ExecutorService aiCallExecutor() {
        return Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "ai-call");
            t.setDaemon(true);
            return t;
        });
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