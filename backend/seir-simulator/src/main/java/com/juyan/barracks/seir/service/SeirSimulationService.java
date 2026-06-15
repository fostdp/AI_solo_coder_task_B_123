package com.juyan.barracks.seir.service;

import com.juyan.barracks.common.entity.ContactEdge;
import com.juyan.barracks.common.entity.SeirSimulation;
import com.juyan.barracks.common.entity.SeirTimePoint;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.ContactEdgeRepository;
import com.juyan.barracks.common.repository.SeirSimulationRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import com.juyan.barracks.seir.algorithm.SeirModel;
import com.juyan.barracks.seir.algorithm.SeirParallelExecutor;
import com.juyan.barracks.seir.algorithm.SeirResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class SeirSimulationService {

    private final SoldierRepository soldierRepository;
    private final ContactEdgeRepository contactEdgeRepository;
    private final SeirSimulationRepository seirSimulationRepository;
    private final SeirParallelExecutor seirParallelExecutor;
    private final MeterRegistry meterRegistry;
    private final ThreadPoolTaskExecutor seirSimulationExecutor;

    @Value("${seir.default-beta:0.35}")
    private double defaultBeta;

    @Value("${seir.default-sigma:0.6667}")
    private double defaultSigma;

    @Value("${seir.default-gamma:0.2}")
    private double defaultGamma;

    @Value("${seir.default-quarantine-effectiveness:0.6}")
    private double defaultQuarantineEffectiveness;

    @Value("${seir.parallel.enabled:true}")
    private boolean parallelEnabled;

    public SeirSimulationService(SoldierRepository soldierRepository,
                                 ContactEdgeRepository contactEdgeRepository,
                                 SeirSimulationRepository seirSimulationRepository,
                                 SeirParallelExecutor seirParallelExecutor,
                                 MeterRegistry meterRegistry,
                                 @Qualifier("seirSimulationExecutor") ThreadPoolTaskExecutor seirSimulationExecutor) {
        this.soldierRepository = soldierRepository;
        this.contactEdgeRepository = contactEdgeRepository;
        this.seirSimulationRepository = seirSimulationRepository;
        this.seirParallelExecutor = seirParallelExecutor;
        this.meterRegistry = meterRegistry;
        this.seirSimulationExecutor = seirSimulationExecutor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationRequest {
        private Long barracksId;
        private Integer initialInfected;
        private Integer days;
        private Double beta;
        private Double sigma;
        private Double gamma;
        private Integer quarantineStartDay;
        private Double isolationEffectiveness;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationComparison {
        private SeirSimulation noQuarantineSimulation;
        private SeirSimulation withQuarantineSimulation;
        private Double infectionReductionPercent;
        private Integer peakDelayDays;
        private String summary;
    }

    @Transactional
    public SimulationComparison runSimulation(SimulationRequest request) {
        return meterRegistry.timer("seir.simulation.time").record(() -> runSimulationInternal(request));
    }

    private SimulationComparison runSimulationInternal(SimulationRequest request) {
        if (request.getBarracksId() == null) {
            throw new IllegalArgumentException("barracksId不能为空");
        }

        int initialInfected = request.getInitialInfected() != null ? request.getInitialInfected() : 1;
        int days = request.getDays() != null ? request.getDays() : 60;

        Double betaParam = request.getBeta();
        Double sigmaParam = request.getSigma();
        Double gammaParam = request.getGamma();

        Integer quarantineStartDay = request.getQuarantineStartDay();
        if (quarantineStartDay == null) {
            quarantineStartDay = 7;
        }
        Double isolationEffectiveness = request.getIsolationEffectiveness();
        if (isolationEffectiveness == null) {
            isolationEffectiveness = defaultQuarantineEffectiveness;
        }

        List<Soldier> soldiers = soldierRepository.findByBarracksId(request.getBarracksId());
        int population = soldiers.size();

        if (population == 0) {
            log.warn("兵营 {} 无士兵数据，无法进行SEIR模拟", request.getBarracksId());
            return null;
        }

        List<ContactEdge> contactEdges = contactEdgeRepository.findByBarracksIdAndIsActiveTrue(request.getBarracksId());
        if (contactEdges.isEmpty()) {
            contactEdges = buildDefaultContactEdges(soldiers);
        }

        SeirModel.SimulationParams params = SeirModel.SimulationParams.builder()
                .beta(betaParam != null ? betaParam : defaultBeta)
                .sigma(sigmaParam != null ? sigmaParam : defaultSigma)
                .gamma(gammaParam != null ? gammaParam : defaultGamma)
                .build();

        log.info("开始SEIR模拟: 兵营={}, 人口={}, 初始感染={}, 模拟天数={}, 并行={}",
                request.getBarracksId(), population, initialInfected, days, parallelEnabled);

        SeirModel.QuarantineConfig quarantineConfig = SeirModel.QuarantineConfig.builder()
                .quarantineStartDay(quarantineStartDay)
                .isolationEffectiveness(isolationEffectiveness)
                .quarantineRate(0.6)
                .build();

        SeirResult noQuarantineResult;
        SeirResult withQuarantineResult;

        if (parallelEnabled) {
            CompletableFuture<SeirResult> noQuarantineFuture = CompletableFuture.supplyAsync(() ->
                    seirParallelExecutor.simulateSingleWithParallelODE(
                            population, initialInfected, days, params, contactEdges, null),
                    seirSimulationExecutor);

            CompletableFuture<SeirResult> withQuarantineFuture = CompletableFuture.supplyAsync(() ->
                    seirParallelExecutor.simulateSingleWithParallelODE(
                            population, initialInfected, days, params, contactEdges, quarantineConfig),
                    seirSimulationExecutor);

            noQuarantineResult = noQuarantineFuture.join();
            withQuarantineResult = withQuarantineFuture.join();
        } else {
            noQuarantineResult = seirParallelExecutor.simulateSingleWithParallelODE(
                    population, initialInfected, days, params, contactEdges, null);
            withQuarantineResult = seirParallelExecutor.simulateSingleWithParallelODE(
                    population, initialInfected, days, params, contactEdges, quarantineConfig);
        }

        SeirSimulation noQuarantineSim = saveSimulation(
                request, noQuarantineResult, population, initialInfected, days,
                betaParam, sigmaParam, gammaParam, false, null, null,
                "无隔离措施_" + request.getBarracksId());

        SeirSimulation withQuarantineSim = saveSimulation(
                request, withQuarantineResult, population, initialInfected, days,
                betaParam, sigmaParam, gammaParam, true, quarantineStartDay, isolationEffectiveness,
                "隔离措施_" + request.getBarracksId());

        double infectionReduction = 0;
        int peakDelay = 0;
        if (noQuarantineResult.getTotalInfected() > 0) {
            infectionReduction = (double) (noQuarantineResult.getTotalInfected() - withQuarantineResult.getTotalInfected())
                    / noQuarantineResult.getTotalInfected() * 100;
        }
        peakDelay = withQuarantineResult.getPeakDay() - noQuarantineResult.getPeakDay();

        String summary = generateComparisonSummary(noQuarantineResult, withQuarantineResult,
                infectionReduction, peakDelay);

        log.info("SEIR模拟完成: 感染减少={}%, 峰值延迟={}天",
                String.format("%.2f", infectionReduction), peakDelay);

        return SimulationComparison.builder()
                .noQuarantineSimulation(noQuarantineSim)
                .withQuarantineSimulation(withQuarantineSim)
                .infectionReductionPercent(infectionReduction)
                .peakDelayDays(peakDelay)
                .summary(summary)
                .build();
    }

    private List<ContactEdge> buildDefaultContactEdges(List<Soldier> soldiers) {
        List<ContactEdge> edges = new ArrayList<>();
        int count = soldiers.size();

        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < Math.min(i + 4, count); j++) {
                ContactEdge edge = new ContactEdge();
                edge.setSoldierIdA(soldiers.get(i).getId());
                edge.setSoldierIdB(soldiers.get(j).getId());

                int distance = j - i;
                if (distance <= 1) {
                    edge.setContactType("ROOMMATE");
                    edge.setContactFrequencyPerDay(10.0);
                } else {
                    edge.setContactType("TABLE");
                    edge.setContactFrequencyPerDay(4.0);
                }
                edge.setIsActive(true);
                edge.setCreatedAt(LocalDateTime.now());
                edges.add(edge);
            }
        }
        return edges;
    }

    private SeirSimulation saveSimulation(SimulationRequest request, SeirResult result,
                                          int population, int initialInfected, int days,
                                          Double betaParam, Double sigmaParam, Double gammaParam,
                                          boolean withQuarantine, Integer quarantineStartDay,
                                          Double isolationEffectiveness, String simulationName) {
        SeirSimulation sim = new SeirSimulation();
        sim.setBarracksId(request.getBarracksId());
        sim.setSimulationName(simulationName);
        sim.setVirusType("诺如病毒");
        sim.setSimulationDays(days);
        sim.setInitialInfectedCount(initialInfected);
        sim.setTransmissionRateBeta(BigDecimal.valueOf(betaParam != null ? betaParam : defaultBeta)
                .setScale(4, RoundingMode.HALF_UP));
        sim.setLatentRateSigma(BigDecimal.valueOf(sigmaParam != null ? sigmaParam : defaultSigma)
                .setScale(4, RoundingMode.HALF_UP));
        sim.setRecoveryRateGamma(BigDecimal.valueOf(gammaParam != null ? gammaParam : defaultGamma)
                .setScale(4, RoundingMode.HALF_UP));

        if (withQuarantine) {
            sim.setIsolationEffectiveness(BigDecimal.valueOf(isolationEffectiveness)
                    .setScale(2, RoundingMode.HALF_UP));
            sim.setQuarantineStartDay(quarantineStartDay);
        }

        sim.setMaxInfectedCount(result.getPeakInfected());
        sim.setTotalInfectedCount(result.getTotalInfected());
        sim.setPeakDay(result.getPeakDay());
        sim.setIsCompleted(true);
        sim.setCreatedAt(LocalDateTime.now());

        List<SeirTimePoint> timePoints = new ArrayList<>();
        for (SeirResult.SeirDayPoint dp : result.getDayPoints()) {
            SeirTimePoint tp = new SeirTimePoint();
            tp.setSimulation(sim);
            tp.setDay(dp.getDay());
            tp.setSusceptibleCount(dp.getSusceptible());
            tp.setExposedCount(dp.getExposed());
            tp.setInfectedCount(dp.getInfected());
            tp.setRecoveredCount(dp.getRecovered());
            tp.setQuarantinedCount(dp.getQuarantined());
            timePoints.add(tp);
        }
        sim.setTimePoints(timePoints);

        return seirSimulationRepository.save(sim);
    }

    private String generateComparisonSummary(SeirResult noQ, SeirResult withQ,
                                             double reductionPercent, int peakDelay) {
        StringBuilder sb = new StringBuilder();
        sb.append("SEIR传播模拟对比分析报告。");
        sb.append("无隔离场景: 峰值感染人数=").append(noQ.getPeakInfected())
                .append("(第").append(noQ.getPeakDay()).append("天), ")
                .append("总感染人数=").append(noQ.getTotalInfected()).append("人, ")
                .append("持续周期=").append(noQ.getDurationDays()).append("天。");
        sb.append("隔离场景: 峰值感染人数=").append(withQ.getPeakInfected())
                .append("(第").append(withQ.getPeakDay()).append("天), ")
                .append("总感染人数=").append(withQ.getTotalInfected()).append("人, ")
                .append("持续周期=").append(withQ.getDurationDays()).append("天。");
        sb.append("隔离效果评估: 感染人数减少").append(String.format("%.2f", reductionPercent))
                .append("%，峰值延迟").append(peakDelay).append("天。");
        sb.append("R0=").append(String.format("%.4f", noQ.getR0())).append("。");
        if (reductionPercent >= 50) {
            sb.append("隔离措施效果显著，建议立即实施。");
        } else if (reductionPercent >= 30) {
            sb.append("隔离措施有一定效果，建议配合其他干预措施。");
        } else {
            sb.append("隔离措施效果有限，需考虑更严格的干预方案。");
        }
        return sb.toString();
    }

    public List<SeirSimulation> getSimulationsByBarracks(Long barracksId) {
        return seirSimulationRepository.findByBarracksIdOrderByCreatedAtDesc(barracksId);
    }

    public SeirSimulation getSimulationDetail(Long id) {
        return seirSimulationRepository.findById(id).orElse(null);
    }

    public List<SeirSimulation> getRecentSimulations() {
        return seirSimulationRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public List<ContactEdge> getContactEdges(Long barracksId) {
        List<ContactEdge> edges = contactEdgeRepository.findByBarracksIdAndIsActiveTrue(barracksId);
        if (edges.isEmpty()) {
            List<Soldier> soldiers = soldierRepository.findByBarracksId(barracksId);
            edges = buildDefaultContactEdges(soldiers);
        }
        return edges;
    }

    public Map<String, Object> evaluateQuarantineEffect(Long simulationIdNoQ, Long simulationIdWithQ) {
        SeirSimulation noQ = seirSimulationRepository.findById(simulationIdNoQ).orElse(null);
        SeirSimulation withQ = seirSimulationRepository.findById(simulationIdWithQ).orElse(null);

        Map<String, Object> result = new HashMap<>();
        if (noQ == null || withQ == null) {
            result.put("error", "模拟记录不存在");
            return result;
        }

        double reduction = 0;
        if (noQ.getTotalInfectedCount() != null && noQ.getTotalInfectedCount() > 0) {
            reduction = (double) (noQ.getTotalInfectedCount() -
                    (withQ.getTotalInfectedCount() != null ? withQ.getTotalInfectedCount() : 0))
                    / noQ.getTotalInfectedCount() * 100;
        }
        int peakDelay = (withQ.getPeakDay() != null ? withQ.getPeakDay() : 0) -
                (noQ.getPeakDay() != null ? noQ.getPeakDay() : 0);

        result.put("noQuarantineTotalInfected", noQ.getTotalInfectedCount());
        result.put("withQuarantineTotalInfected", withQ.getTotalInfectedCount());
        result.put("infectionReductionPercent", reduction);
        result.put("noQuarantinePeakDay", noQ.getPeakDay());
        result.put("withQuarantinePeakDay", withQ.getPeakDay());
        result.put("peakDelayDays", peakDelay);
        result.put("noQuarantinePeakInfected", noQ.getMaxInfectedCount());
        result.put("withQuarantinePeakInfected", withQ.getMaxInfectedCount());

        return result;
    }
}
