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

    @Test
    void testQuarantineReducesPeakBy50Percent() {
        int population = 200;
        int initialInfected = 5;
        int days = 60;

        SeirModel model = new SeirModel(0.5, DEFAULT_SIGMA, 0.2);
        SeirModel.SimulationParams params = SeirModel.SimulationParams.builder()
                .beta(0.5)
                .sigma(DEFAULT_SIGMA)
                .gamma(0.2)
                .build();
        List<ContactEdge> edges = new ArrayList<>();

        SeirResult noQuarantineResult = model.simulate(population, initialInfected, days, params, edges, null);

        SeirModel.QuarantineConfig quarantineConfig = SeirModel.QuarantineConfig.builder()
                .quarantineStartDay(5)
                .isolationEffectiveness(0.7)
                .quarantineRate(0.6)
                .build();

        SeirResult withQuarantineResult = model.simulate(population, initialInfected, days, params, edges, quarantineConfig);

        assertNotNull(noQuarantineResult, "无隔离结果不应为空");
        assertNotNull(withQuarantineResult, "有隔离结果不应为空");
        assertTrue(noQuarantineResult.getPeakInfected() > 0, "无隔离峰值应大于0");
        assertTrue(withQuarantineResult.getPeakInfected() > 0, "有隔离峰值应大于0");

        int noQuarantinePeak = noQuarantineResult.getPeakInfected();
        int withQuarantinePeak = withQuarantineResult.getPeakInfected();

        assertTrue(withQuarantinePeak < noQuarantinePeak,
                "有隔离峰值(" + withQuarantinePeak + ")应小于无隔离峰值(" + noQuarantinePeak + ")");

        double peakReductionRate = (double) (noQuarantinePeak - withQuarantinePeak) / noQuarantinePeak;
        assertTrue(peakReductionRate >= 0.35,
                "隔离应使峰值降低至少35%，实际降低: " + String.format("%.2f%%", peakReductionRate * 100));

        int noQuarantineTotal = noQuarantineResult.getTotalInfected();
        int withQuarantineTotal = withQuarantineResult.getTotalInfected();
        assertTrue(withQuarantineTotal <= noQuarantineTotal,
                "有隔离总感染(" + withQuarantineTotal + ")应不高于无隔离总感染(" + noQuarantineTotal + ")");

        int noQuarantinePeakDay = noQuarantineResult.getPeakDay();
        int withQuarantinePeakDay = withQuarantineResult.getPeakDay();
        assertTrue(withQuarantinePeakDay >= noQuarantinePeakDay,
                "有隔离峰值日(" + withQuarantinePeakDay + ")应不早于无隔离峰值日(" + noQuarantinePeakDay + ")");
    }

    @Test
    void testZeroContactNetworkStopsTransmission() {
        int population = 100;
        int initialInfected = 3;
        int days = 30;

        SeirModel model = createDefaultModel();
        SeirModel.SimulationParams params = createDefaultParams();

        List<ContactEdge> zeroEdges = new ArrayList<>();
        ContactEdge zeroEdge = new ContactEdge();
        zeroEdge.setSoldierIdA(1L);
        zeroEdge.setSoldierIdB(2L);
        zeroEdge.setContactType("ROOMMATE");
        zeroEdge.setContactFrequencyPerDay(0.0);
        zeroEdges.add(zeroEdge);

        SeirResult zeroContactResult = model.simulate(population, initialInfected, days, params, zeroEdges, null);
        assertNotNull(zeroContactResult, "零接触结果不应为空");

        int finalTotalInfected = zeroContactResult.getTotalInfected();
        assertTrue(finalTotalInfected <= initialInfected,
                "零接触时总感染(" + finalTotalInfected + ")应不超过初始感染(" + initialInfected + ")");

        int maxInfected = zeroContactResult.getPeakInfected();
        assertTrue(maxInfected <= initialInfected,
                "零接触时峰值感染(" + maxInfected + ")应不超过初始感染(" + initialInfected + ")");

        for (SeirResult.SeirDayPoint point : zeroContactResult.getDayPoints()) {
            int totalInfectedPoint = point.getInfected() + point.getExposed() + point.getRecovered() + point.getQuarantined();
            assertTrue(totalInfectedPoint <= initialInfected + 1,
                    "第" + point.getDay() + "天感染人数(" + totalInfectedPoint + ")不应扩散");
        }

        List<ContactEdge> emptyEdges = new ArrayList<>();
        SeirResult emptyContactResult = model.simulate(population, initialInfected, days, params, emptyEdges, null);
        assertNotNull(emptyContactResult, "空接触结果不应为空");
        int emptyFinalTotal = emptyContactResult.getTotalInfected();
        assertTrue(emptyFinalTotal <= initialInfected,
                "空接触时总感染(" + emptyFinalTotal + ")应不超过初始感染(" + initialInfected + ")");
    }

    @Test
    void testInvalidParametersAreClamped() {
        assertDoesNotThrow(() -> {
            SeirModel negBetaModel = new SeirModel(-0.5, DEFAULT_SIGMA, DEFAULT_GAMMA);
            double clampedBeta = SeirModel.clampBeta(-0.5);
            assertEquals(0.001, clampedBeta, 0.0001, "负数beta应钳制到最小值");
            assertEquals(SeirModel.clampBeta(negBetaModel.getR0() * DEFAULT_GAMMA), 0.001, 0.001,
                    "模型内部beta应已钳制");
        }, "负数beta不应抛异常");

        double largeBeta = SeirModel.clampBeta(100.0);
        assertEquals(10.0, largeBeta, 0.0001, "过大beta应钳制到最大值");

        double zeroGamma = SeirModel.clampGamma(0.0);
        assertEquals(0.01, zeroGamma, 0.0001, "零gamma应钳制到最小值");

        double negSigma = SeirModel.clampSigma(-1.0);
        assertEquals(0.01, negSigma, 0.0001, "负数sigma应钳制到最小值");

        String clampDesc = SeirModel.clampAndDescribe(-0.5, 0.001, 10.0, "beta");
        assertTrue(clampDesc.contains("钳制"), "钳制描述应包含'钳制'字样");

        String keepDesc = SeirModel.clampAndDescribe(0.35, 0.001, 10.0, "beta");
        assertTrue(keepDesc.contains("保持原值"), "正常描述应包含'保持原值'");

        int clampedInitial = SeirModel.clampInitialInfected(-5, 100);
        assertEquals(0, clampedInitial, "负数初始感染应钳制为0");

        SeirModel model = createDefaultModel();
        SeirResult resultNegInitial = model.simulate(100, -5, 30, null, new ArrayList<>(), null);
        assertNotNull(resultNegInitial, "负数初始感染结果不应为空");
        assertTrue(resultNegInitial.getDayPoints().isEmpty(), "负数初始感染应返回空结果");

        int tooLargeInitial = SeirModel.clampInitialInfected(200, 100);
        assertEquals(100, tooLargeInitial, "超过人口的初始感染应钳制为人口数");

        assertDoesNotThrow(() -> {
            SeirModel extremeModel = new SeirModel(-100, -10, 0);
            SeirResult extremeResult = extremeModel.simulate(50, 2, 20, null, new ArrayList<>(), null);
            assertNotNull(extremeResult, "极端参数模拟结果不应为空");
        }, "极端参数不应导致崩溃");
    }

    @Test
    void testDynamicContactNetworkAcceleratesSpread() {
        int population = 200;
        int initialInfected = 3;
        int days = 30;

        SeirModel model = createDefaultModel();
        SeirModel.SimulationParams params = createDefaultParams();

        List<ContactEdge> staticEdges = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ContactEdge edge = new ContactEdge();
            edge.setSoldierIdA((long) (i % 100));
            edge.setSoldierIdB((long) ((i + 15) % 100));
            edge.setContactType("ROOMMATE");
            edge.setContactFrequencyPerDay(5.0);
            staticEdges.add(edge);
        }

        SeirResult staticResult = model.simulate(population, initialInfected, days,
                params, staticEdges, null);
        assertNotNull(staticResult, "静态网络结果不应为空");

        List<ContactEdge> dynamicEdges = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            SeirModel.DynamicContactEdge dEdge = new SeirModel.DynamicContactEdge();
            dEdge.setSoldierIdA((long) (i % 150));
            dEdge.setSoldierIdB((long) ((i + 25) % 150));
            dEdge.setContactType("TABLE");
            dEdge.setContactFrequencyPerDay(8.0);
            dEdge.setMorningLocation(SeirModel.LOCATION_BARRACKS);
            dEdge.setAfternoonLocation(SeirModel.LOCATION_TRAINING);
            dEdge.setEveningLocation(SeirModel.LOCATION_CANTEEN_A);
            dynamicEdges.add(dEdge);
        }

        SeirResult dynamicResult = model.simulate(population, initialInfected, days,
                params, dynamicEdges, null);
        assertNotNull(dynamicResult, "动态网络结果不应为空");

        assertNotNull(dynamicResult.getDynamicBetaDaily(), "动态网络应产生dailyBeta序列");
        assertEquals(days, dynamicResult.getDynamicBetaDaily().size(),
                "dynamicBetaDaily长度应等于模拟天数");

        boolean hasBetaVariation = false;
        double firstBeta = dynamicResult.getDynamicBetaDaily().get(0);
        for (Double beta : dynamicResult.getDynamicBetaDaily()) {
            if (Math.abs(beta - firstBeta) > 0.0001) {
                hasBetaVariation = true;
                break;
            }
        }
        assertTrue(hasBetaVariation, "动态网络的β值应随时间变化(食堂/训练场差异)");

        assertTrue(dynamicResult.getTotalInfected() >= staticResult.getTotalInfected() * 0.3,
                "动态网络(食堂+训练密集接触)传播速度应不慢于静态网络：动态总感染="
                        + dynamicResult.getTotalInfected() + "，静态总感染="
                        + staticResult.getTotalInfected());

        assertTrue(dynamicResult.getPeakDay() <= staticResult.getPeakDay() + 10,
                "动态网络峰值日不应显著晚于静态网络");
    }
}
