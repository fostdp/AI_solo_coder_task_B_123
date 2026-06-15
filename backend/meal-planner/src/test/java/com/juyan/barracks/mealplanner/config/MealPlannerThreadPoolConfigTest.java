package com.juyan.barracks.mealplanner.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

class MealPlannerThreadPoolConfigTest {

    @Test
    void testMipSolverExecutorCreation() {
        MealPlannerThreadPoolConfig config = new MealPlannerThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 10);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.mipSolverExecutor();

        assertNotNull(executor, "mipSolverExecutor should not be null");
        assertEquals(2, executor.getCorePoolSize(), "Core pool size should be 2");
        assertEquals(4, executor.getMaxPoolSize(), "Max pool size should be 4");
        executor.shutdown();
    }

    @Test
    void testExecutorRejectPolicy() {
        MealPlannerThreadPoolConfig config = new MealPlannerThreadPoolConfig();
        setField(config, "coreSize", 1);
        setField(config, "maxSize", 2);
        setField(config, "queueCapacity", 5);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.mipSolverExecutor();
        assertNotNull(executor);
        executor.shutdown();
    }

    @Test
    void testExecutorSubmitTask() throws Exception {
        MealPlannerThreadPoolConfig config = new MealPlannerThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 10);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.mipSolverExecutor();

        java.util.concurrent.Future<String> future = executor.submit(() -> "test-result");
        assertEquals("test-result", future.get());

        executor.shutdown();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
