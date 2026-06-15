package com.juyan.barracks.supply.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SupplyAnalysisThreadPoolConfigTest {

    @Test
    void testAprioriAnalysisExecutorCreation() {
        SupplyAnalysisThreadPoolConfig config = new SupplyAnalysisThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 20);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.aprioriAnalysisExecutor();

        assertNotNull(executor, "aprioriAnalysisExecutor should not be null");
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        executor.shutdown();
    }

    @Test
    void testAprioriAnalysisExecutorParallelProcessing() throws Exception {
        SupplyAnalysisThreadPoolConfig config = new SupplyAnalysisThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 20);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.aprioriAnalysisExecutor();
        int taskCount = 8;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(30);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All tasks should complete");
        assertEquals(taskCount, successCount.get(), "All tasks should succeed");
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
