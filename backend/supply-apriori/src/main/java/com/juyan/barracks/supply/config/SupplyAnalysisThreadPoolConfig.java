package com.juyan.barracks.supply.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class SupplyAnalysisThreadPoolConfig {

    @Value("${apriori.thread-pool.core-size:2}")
    private int coreSize;

    @Value("${apriori.thread-pool.max-size:4}")
    private int maxSize;

    @Value("${apriori.thread-pool.queue-capacity:20}")
    private int queueCapacity;

    @Bean("aprioriAnalysisExecutor")
    public Executor aprioriAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("apriori-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
