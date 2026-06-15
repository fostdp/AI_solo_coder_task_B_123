package com.juyan.barracks.seir.algorithm;

import com.juyan.barracks.common.entity.ContactEdge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeirModelTest {

    private static final double DEFAULT_BETA = 0.35;
    private static final double DEFAULT_SIGMA = 0.6667;
    private static final double DEFAULT_GAMMA = 0.2;
    private static final int DEFAULT_POPULATION = 100;
    private static final int DEFAULT_INITIAL_INFECTED = 2;
    private static final int DEFAULT_DAYS = 30;

    private SeirModel createDefaultModel() {
        return new SeirModel(DEFAULT_BETA, DEFAULT_SIGMA, DEFAULT_GAMMA);
    }

    private SeirModel.SimulationParams createDefaultParams() {
        return SeirModel.SimulationParams.builder()
                .beta(DEFAULT_BETA)
                .sigma(DEFAULT_SIGMA)
                .gamma(DEFAULT_GAMMA)
                .build();
    }

    @Test
    void testEmptyPopulation() {
        SeirModel model = createDefaultModel();
        SeirModel.SimulationParams params = createDefaultParams();
        List<ContactEdge> edges = new ArrayList<>();

        SeirResult resultZeroPop = model.simulate(0, DEFAULT_INITIAL_INFECTED, DEFAULT_DAYS,
                params, edges, null);
        assertNotNull(resultZeroPop, "人口为0时结果不应为空");
        assertTrue(resultZeroPop.getDayPoints().isEmpty(),
                "人口为0时日点列表应为空");
        assertEquals(0, resultZeroPop.getPeakDay(),
                "人口为0时峰值日应为0");
        assertEquals(0, resultZeroPop.getPeakInfected(),
                "人口为0时峰值感染应为0");
        assertEquals(0, resultZeroPop.getTotalInfected(),
                "人口为0时总感染应为0");
        assertEquals(0.0, resultZeroPop.getR0(), 0.0001,
                "人口为0时R0应为0");

        SeirResult resultNegativePop = model.simulate(-5, DEFAULT_INITIAL_INFECTED, DEFAULT_DAYS,
                params, edges, null);
        assertNotNull(resultNegativePop, "人口为负时结果不应为空");
        assertTrue(resultNegativePop.getDayPoints().isEmpty(),
                "人口为负时日点列表应为空");

        SeirResult resultZeroDays = model.simulate(DEFAULT_POPULATION, DEFAULT_INITIAL_INFECTED, 0,
                params, edges, null);
        assertNotNull(resultZeroDays, "模拟天数为0时结果不应为空");
        assertTrue(resultZeroDays.getDayPoints().isEmpty(),
                "模拟天数为0时日点列表应为空");

        SeirResult resultZeroInfected = model.simulate(DEFAULT_POPULATION, 0, DEFAULT_DAYS,
                params, edges, null);
        assertNotNull(resultZeroInfected, "初始感染为0时结果不应为空");
        assertTrue(resultZeroInfected.getDayPoints().isEmpty(),
                "初始感染为0时日点列表应为空");
    }

    @Test
    void testSeirPopulationConservation() {
        SeirModel model = createDefaultModel();
        SeirModel.SimulationParams params = createDefaultParams();
        List<ContactEdge> edges = new ArrayList<>();

        SeirResult result = model.simulate(DEFAULT_POPULATION, DEFAULT_INITIAL_INFECTED,
                DEFAULT_DAYS, params, edges, null);

        assertNotNull(result, "模拟结果不应为空");
        assertNotNull(result.getDayPoints(), "日点列表不应为空");
        assertFalse(result.getDayPoints().isEmpty(), "日点列表不应为空");

        for (SeirResult.SeirDayPoint point : result.getDayPoints()) {
            int total = point.getSusceptible() + point.getExposed() +
                    point.getInfected() + point.getRecovered() + point.getQuarantined();
            assertEquals(DEFAULT_POPULATION, total,
                    "第" + point.getDay() + "天人口应守恒: S+E+I+R+Q=" + total +
                            " 应等于 N=" + DEFAULT_POPULATION);
        }

        SeirResult.SeirDayPoint firstPoint = result.getDayPoints().get(0);
        assertEquals(0, firstPoint.getDay(), "首日应为第0天");
        assertEquals(DEFAULT_POPULATION - DEFAULT_INITIAL_INFECTED, firstPoint.getSusceptible(),
                "初始易感者应为人口减初始感染");
        assertEquals(0, firstPoint.getExposed(), "初始潜伏者应为0");
        assertEquals(DEFAULT_INITIAL_INFECTED, firstPoint.getInfected(),
                "初始感染者应为" + DEFAULT_INITIAL_INFECTED);
        assertEquals(0, firstPoint.getRecovered(), "初始康复者应为0");
        assertEquals(0, firstPoint.getQuarantined(), "初始隔离者应为0");
    }

    @Test
    void testQuarantineEffectiveness() {
        SeirModel model = createDefaultModel();
        SeirModel.SimulationParams params = createDefaultParams();
        List<ContactEdge> edges = new ArrayList<>();

        SeirResult noQuarantineResult = model.simulate(DEFAULT_POPULATION, DEFAULT_INITIAL_INFECTED,
                DEFAULT_DAYS, params, edges, null);

        SeirModel.QuarantineConfig quarantineConfig = SeirModel.QuarantineConfig.builder()
                .quarantineStartDay(3)
                .isolationEffectiveness(0.7)
                .quarantineRate(0.6)
                .build();

        SeirResult withQuarantineResult = model.simulate(DEFAULT_POPULATION, DEFAULT_INITIAL_INFECTED,
                DEFAULT_DAYS, params, edges, quarantineConfig);

        assertNotNull(noQuarantineResult, "无隔离模拟结果不应为空");
        assertNotNull(withQuarantineResult, "有隔离模拟结果不应为空");

        int noQuarantinePeak = noQuarantineResult.getPeakInfected();
        int withQuarantinePeak = withQuarantineResult.getPeakInfected();

        assertTrue(withQuarantinePeak <= noQuarantinePeak,
                "隔离措施的峰值感染人数(" + withQuarantinePeak +
                        ")应不高于无隔离的峰值(" + noQuarantinePeak + ")");

        if (noQuarantinePeak > 0) {
            double reductionRate = (double) (noQuarantinePeak - withQuarantinePeak) / noQuarantinePeak;
            assertTrue(reductionRate >= 0,
                    "隔离应至少不增加峰值感染，实际降低率: " + String.format("%.2f%%", reductionRate * 100));
        }
    }

    @Test
    void testR0Calculation() {
        double beta = 0.5;
        double gamma = 0.2;
        double expectedR0 = beta / gamma;

        SeirModel model = new SeirModel(beta, DEFAULT_SIGMA, gamma);
        SeirModel.SimulationParams params = SeirModel.SimulationParams.builder()
                .beta(beta)
                .sigma(DEFAULT_SIGMA)
                .gamma(gamma)
                .build();
        List<ContactEdge> edges = new ArrayList<>();

        SeirResult result = model.simulate(500, 5, 60, params, edges, null);

        assertNotNull(result, "模拟结果不应为空");
        assertEquals(expectedR0, result.getR0(), 0.01,
                "R0应等于beta/gamma=" + expectedR0 + "，实际: " + result.getR0());

        assertTrue(result.getR0() > 1.0,
                "当R0>1时应发生暴发，实际R0=" + result.getR0());

        assertTrue(result.getTotalInfected() > 5,
                "R0>1时总感染人数(" + result.getTotalInfected() +
                        ")应超过初始感染人数(5)");

        assertTrue(result.getPeakInfected() > 5,
                "R0>1时峰值感染人数(" + result.getPeakInfected() +
                        ")应超过初始感染人数(5)");

        SeirModel lowR0Model = new SeirModel(0.1, DEFAULT_SIGMA, 0.5);
        SeirModel.SimulationParams lowR0Params = SeirModel.SimulationParams.builder()
                .beta(0.1)
                .sigma(DEFAULT_SIGMA)
                .gamma(0.5)
                .build();
        SeirResult lowR0Result = lowR0Model.simulate(500, 5, 60, lowR0Params, edges, null);

        assertTrue(lowR0Result.getR0() < 1.0,
                "低传播场景R0应<1，实际: " + lowR0Result.getR0());
    }
}
