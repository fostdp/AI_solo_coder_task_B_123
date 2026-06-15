package com.juyan.barracks.mealplanner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class MealPlannerThreadPoolConfig {

    @Value("${meal-planner.thread-pool.core-size:2}")
    private int coreSize;

    @Value("${meal-planner.thread-pool.max-size:4}")
    private int maxSize;

    @Value("${meal-planner.thread-pool.queue-capacity:10}")
    private int queueCapacity;

    @Bean("mipSolverExecutor")
    public ThreadPoolTaskExecutor mipSolverExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("mip-solver-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
