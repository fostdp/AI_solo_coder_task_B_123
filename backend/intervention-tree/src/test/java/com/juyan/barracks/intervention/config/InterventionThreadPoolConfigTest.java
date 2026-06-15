package com.juyan.barracks.intervention.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InterventionThreadPoolConfigTest {

    @Test
    void testInterventionExecutorCreation() {
        InterventionThreadPoolConfig config = new InterventionThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 50);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.interventionExecutor();

        assertNotNull(executor, "interventionExecutor should not be null");
        assertEquals(2, executor.getCorePoolSize());
        assertEquals(4, executor.getMaxPoolSize());
        executor.shutdown();
    }

    @Test
    void testInterventionExecutorParallelProcessing() throws Exception {
        InterventionThreadPoolConfig config = new InterventionThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 50);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.interventionExecutor();
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(50);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All tasks should complete");
        assertEquals(taskCount, successCount.get(), "All tasks should succeed");
        executor.shutdown();
    }

    @Test
    void testInterventionConfigDefaults() {
        InterventionConfig config = new InterventionConfig();
        assertEquals(0.5, config.getHighRiskThreshold(), 0.001);
        assertEquals(14, config.getDefaultDurationDays());
        assertEquals(10, config.getTreeMaxDepth());
        assertEquals(5, config.getTreeMinSamplesSplit());
        assertEquals(5, config.getCrossValidationFolds());
        assertEquals(50, config.getMinHistorySamplesForTraining());
        assertTrue(config.isEnableBuiltinRulesFallback());
    }

    @Test
    void testInterventionConfigCustomValues() {
        InterventionConfig config = new InterventionConfig();
        config.setHighRiskThreshold(0.7);
        config.setDefaultDurationDays(21);
        config.setTreeMaxDepth(15);
        config.setTreeMinSamplesSplit(10);

        assertEquals(0.7, config.getHighRiskThreshold(), 0.001);
        assertEquals(21, config.getDefaultDurationDays());
        assertEquals(15, config.getTreeMaxDepth());
        assertEquals(10, config.getTreeMinSamplesSplit());
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
