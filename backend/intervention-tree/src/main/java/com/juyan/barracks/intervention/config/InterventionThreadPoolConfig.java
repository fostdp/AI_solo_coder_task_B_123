package com.juyan.barracks.intervention.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class InterventionThreadPoolConfig {

    @Value("${intervention.thread-pool.core-size:2}")
    private int coreSize;

    @Value("${intervention.thread-pool.max-size:4}")
    private int maxSize;

    @Value("${intervention.thread-pool.queue-capacity:50}")
    private int queueCapacity;

    @Bean("interventionExecutor")
    public Executor interventionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("intervention-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
