package com.juyan.barracks.seir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class SeirThreadPoolConfig {

    @Value("${seir.thread-pool.core-size:2}")
    private int coreSize;

    @Value("${seir.thread-pool.max-size:8}")
    private int maxSize;

    @Value("${seir.thread-pool.queue-capacity:20}")
    private int queueCapacity;

    @Bean("seirSimulationExecutor")
    public ThreadPoolTaskExecutor seirSimulationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("seir-sim-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
