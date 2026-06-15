package com.juyan.barracks.seir.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.*;

class SeirThreadPoolConfigTest {

    @Test
    void testSeirSimulationExecutorCreation() {
        SeirThreadPoolConfig config = new SeirThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 8);
        setField(config, "queueCapacity", 20);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.seirSimulationExecutor();

        assertNotNull(executor, "seirSimulationExecutor should not be null");
        assertEquals(2, executor.getCorePoolSize(), "Core pool size should be 2");
        assertEquals(8, executor.getMaxPoolSize(), "Max pool size should be 8");
        executor.shutdown();
    }

    @Test
    void testExecutorConcurrency() throws Exception {
        SeirThreadPoolConfig config = new SeirThreadPoolConfig();
        setField(config, "coreSize", 2);
        setField(config, "maxSize", 4);
        setField(config, "queueCapacity", 10);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.seirSimulationExecutor();

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(4);
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < 4; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "All tasks should complete within timeout");
        assertEquals(4, counter.get(), "All 4 tasks should have executed");

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
