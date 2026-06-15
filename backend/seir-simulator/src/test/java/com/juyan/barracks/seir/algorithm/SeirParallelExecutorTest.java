package com.juyan.barracks.seir.algorithm;

import com.juyan.barracks.common.entity.ContactEdge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class SeirParallelExecutorTest {

    private SeirParallelExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new SeirParallelExecutor();
        setField(executor, "defaultBeta", 0.35);
        setField(executor, "defaultSigma", 0.6667);
        setField(executor, "defaultGamma", 0.2);
        executor.init();
    }

    @Test
    void testSimulateSingleWithParallelODE() {
        SeirModel.SimulationParams params = SeirModel.SimulationParams.builder()
                .beta(0.35).sigma(0.6667).gamma(0.2).build();

        SeirResult result = executor.simulateSingleWithParallelODE(
                100, 2, 30, params, null, null);

        assertNotNull(result, "ODE result should not be null");
        assertNotNull(result.getDayPoints(), "Day points should not be null");
        assertEquals(31, result.getDayPoints().size(), "Should have 31 day points (day 0-30)");
        assertTrue(result.getPeakInfected() > 0, "Should have infected people");
        assertTrue(result.getR0() > 0, "R0 should be positive");
        assertEquals(0.35 / 0.2, result.getR0(), 0.01, "R0 should be beta/gamma");
    }

    @Test
    void testSimulateSingleWithParallelODEWithQuarantine() {
        SeirModel.SimulationParams params = SeirModel.SimulationParams.builder()
                .beta(0.35).sigma(0.6667).gamma(0.2).build();

        SeirModel.QuarantineConfig quarantine = SeirModel.QuarantineConfig.builder()
                .quarantineStartDay(7)
                .isolationEffectiveness(0.6)
                .quarantineRate(0.5)
                .build();

        SeirResult noQ = executor.simulateSingleWithParallelODE(
                100, 2, 30, params, null, null);
        SeirResult withQ = executor.simulateSingleWithParallelODE(
                100, 2, 30, params, null, quarantine);

        assertTrue(withQ.getTotalInfected() <= noQ.getTotalInfected(),
                "Quarantine should reduce total infections");
    }

    @Test
    void testSimulateSingleWithParallelODEInvalidParams() {
        SeirResult result = executor.simulateSingleWithParallelODE(
                0, 2, 30, null, null, null);
        assertNotNull(result, "Should return empty result for invalid params");

        SeirResult result2 = executor.simulateSingleWithParallelODE(
                100, 0, 30, null, null, null);
        assertNotNull(result2, "Should handle zero initial infected");
    }

    @Test
    void testSimulateParallel() throws Exception {
        List<SeirModel.SimulationParams> paramSets = new ArrayList<>();
        for (double beta = 0.1; beta <= 0.5; beta += 0.1) {
            paramSets.add(SeirModel.SimulationParams.builder()
                    .beta(beta).sigma(0.6667).gamma(0.2).build());
        }

        List<Future<SeirResult>> futures = executor.simulateParallel(
                paramSets, 100, 2, 30, null, null);

        assertNotNull(futures, "Futures should not be null");
        assertEquals(5, futures.size(), "Should have 5 futures");

        for (Future<SeirResult> future : futures) {
            SeirResult result = future.get();
            assertNotNull(result, "Each result should not be null");
            assertTrue(result.getR0() > 0, "R0 should be positive");
        }
    }

    @Test
    void testParallelODEResultConsistency() {
        SeirModel.SimulationParams params = SeirModel.SimulationParams.builder()
                .beta(0.35).sigma(0.6667).gamma(0.2).build();

        SeirResult odeResult = executor.simulateSingleWithParallelODE(
                100, 2, 30, params, null, null);

        SeirModel seirModel = new SeirModel(0.35, 0.6667, 0.2);
        SeirResult eulerResult = seirModel.simulate(100, 2, 30, params, null, null);

        assertNotNull(odeResult);
        assertNotNull(eulerResult);

        double odeTotal = odeResult.getTotalInfected();
        double eulerTotal = eulerResult.getTotalInfected();
        double diff = Math.abs(odeTotal - eulerTotal) / Math.max(eulerTotal, 1);
        assertTrue(diff < 0.5,
                "ODE and Euler results should be within 50% of each other. ODE=" + odeTotal + ", Euler=" + eulerTotal);
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
