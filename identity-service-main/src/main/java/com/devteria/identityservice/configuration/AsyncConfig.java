package com.devteria.identityservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-task-");
        // Khi hàng đợi bị đầy, hủy tác vụ dư thừa có kiểm soát để tránh crash ứng dụng
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Virtual Thread Executor (Java 21) cho các tác vụ AI Streaming I/O.
     */
    @Bean(name = "aiStreamingExecutor")
    public ExecutorService aiStreamingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Semaphore bảo vệ tài nguyên hệ thống, tối đa 50 luồng stream đồng thời.
     */
    @Bean(name = "aiStreamingSemaphore")
    public Semaphore aiStreamingSemaphore() {
        return new Semaphore(50);
    }
}
