package com.example.usercenter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池的配置类
 */
@Configuration
@EnableAsync //开启异步任务
@EnableScheduling //开启定时任务
@Slf4j
public class AsyncConfig {

    @Bean("asyncTaskExecutor")  //将方法返回值注册为Spring Bean，名称指定为asyncTaskExecutor
    public Executor asyncTaskExecutor() {
        //1. 创建线程池对象
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //线程池最小线程数
        executor.setCorePoolSize(5);
        //队列满后，最多新建的线程数（总线程数）
        executor.setMaxPoolSize(20);
        //线程池队列大小
        executor.setQueueCapacity(100);
        //线程名前缀，方便在日志中排查问题
        executor.setThreadNamePrefix("async-task-");
        //拒绝策略:最大线程数+队列都满了，新任务如何处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //Spring容器关闭时。等待已提交的任务执行完成再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //最大等待时间，超时强制关闭
        executor.setAwaitTerminationSeconds(60);
        //初始化线程池
        executor.initialize();
        return executor;
    }
}
