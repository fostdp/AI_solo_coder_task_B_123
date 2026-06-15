package com.juyan.barracks.epidemic.service;

import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.EpidemicAlert;
import com.juyan.barracks.common.entity.FecalSensorData;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.event.EpidemicAlertTriggeredEvent;
import com.juyan.barracks.common.repository.BarracksRepository;
import com.juyan.barracks.common.repository.EpidemicAlertRepository;
import com.juyan.barracks.common.repository.FecalSensorDataRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import com.juyan.barracks.epidemic.algorithm.SatscanEpidemicDetector;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpidemicDetectionService {

    private final BarracksRepository barracksRepository;
    private final SoldierRepository soldierRepository;
    private final FecalSensorDataRepository fecalSensorDataRepository;
    private final EpidemicAlertRepository epidemicAlertRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${algorithm.epidemic.satscan.spatial-radius-meters:100.0}")
    private double spatialRadiusMeters;

    @Value("${algorithm.epidemic.satscan.max-window-days:7}")
    private int maxWindowDays;

    @Value("${algorithm.epidemic.satscan.significance-level:0.05}")
    private double significanceLevel;

    @Value("${algorithm.epidemic.positive-rate-threshold:0.20}")
    private double positiveRateThreshold;

    private SatscanEpidemicDetector detector;

    @PostConstruct
    public void init() {
        detector = new SatscanEpidemicDetector(spatialRadiusMeters, maxWindowDays, significanceLevel);
        log.info("SaTScan流行病检测器初始化完成: 空间半径={}m, 时间窗口={}天, 显著性水平={}",
                spatialRadiusMeters, maxWindowDays, significanceLevel);
    }

    @Scheduled(cron = "${scheduling.epidemic-scan.cron:0 30 */2 * * ?}")
    @Transactional
    public void runEpidemicScan() {
        log.info("开始执行流行病时空扫描...");
        List<Barracks> barracksList = barracksRepository.findAll();
        int alertCount = 0;

        for (Barracks barracks : barracksList) {
            try {
                SatscanEpidemicDetector.ScanResult result = scanBarracks(barracks);

                if (result.isHasSignificantCluster() && result.getPositiveRate() > positiveRateThreshold) {
                    EpidemicAlert alert = createAlert(barracks, result);
                    epidemicAlertRepository.save(alert);
                    eventPublisher.publishEvent(new EpidemicAlertTriggeredEvent(this, alert));
                    alertCount++;
                    log.warn("兵营 [{}] 检测到显著聚集性疫情: 阳性率={}%, p值={}",
                            barracks.getName(),
                            String.format("%.2f", result.getPositiveRate() * 100),
                            String.format("%.4f", result.getPValue()));
                }
            } catch (Exception e) {
                log.error("扫描兵营 {} 疫情失败: {}", barracks.getId(), e.getMessage(), e);
            }
        }

        log.info("流行病时空扫描完成: 扫描兵营数={}, 新告警数={}", barracksList.size(), alertCount);
    }

    public SatscanEpidemicDetector.ScanResult scanBarracks(Barracks barracks) {
        LocalDateTime scanEndTime = LocalDateTime.now();
        LocalDateTime scanStartTime = scanEndTime.minusDays(maxWindowDays);

        List<FecalSensorData> sensorDataList = fecalSensorDataRepository.findByBarracksIdAndTimeRange(
                barracks.getId(), scanStartTime, scanEndTime);

        List<SatscanEpidemicDetector.ClusterPoint> clusterPoints = convertToClusterPoints(
                barracks.getId(), sensorDataList);

        if (clusterPoints.isEmpty()) {
            return SatscanEpidemicDetector.ScanResult.builder()
                    .hasSignificantCluster(false)
                    .pValue(1.0)
                    .logLikelihoodRatio(0.0)
                    .affectedPoints(new ArrayList<>())
                    .build();
        }

        return detector.scanSpatiotemporal(clusterPoints, scanEndTime);
    }

    private List<SatscanEpidemicDetector.ClusterPoint> convertToClusterPoints(
            Long barracksId, List<FecalSensorData> sensorDataList) {

        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();
        List<Soldier> soldiers = soldierRepository.findByBarracksId(barracksId);
        Map<Long, Soldier> soldierMap = new HashMap<>();
        for (Soldier s : soldiers) {
            soldierMap.put(s.getId(), s);
        }

        Random random = new Random(42);

        for (FecalSensorData data : sensorDataList) {
            double x, y;
            Long soldierId = data.getSoldierId();

            if (soldierId != null && soldierMap.containsKey(soldierId)) {
                Soldier soldier = soldierMap.get(soldierId);
                if (soldier.getPosition() != null) {
                    x = soldier.getPosition().getX();
                    y = soldier.getPosition().getY();
                } else {
                    x = soldier.getPositionX();
                    y = soldier.getPositionY();
                }
            } else {
                x = 100.27 + (random.nextDouble() - 0.5) * 0.01;
                y = 41.85 + (random.nextDouble() - 0.5) * 0.01;
            }

            SatscanEpidemicDetector.ClusterPoint point = SatscanEpidemicDetector.ClusterPoint.builder()
                    .x(x)
                    .y(y)
                    .time(data.getSampleTime())
                    .isCase(Boolean.TRUE.equals(data.getIsPositive()))
                    .soldierId(soldierId)
                    .build();

            points.add(point);
        }

        return points;
    }

    private EpidemicAlert createAlert(Barracks barracks, SatscanEpidemicDetector.ScanResult result) {
        EpidemicAlert alert = new EpidemicAlert();
        alert.setBarracksId(barracks.getId());
        alert.setAlertType("INTESTINAL_INFECTION_CLUSTER");

        if (result.getPositiveRate() > 0.4) {
            alert.setAlertLevel("CRITICAL");
        } else if (result.getPositiveRate() > 0.25) {
            alert.setAlertLevel("HIGH");
        } else {
            alert.setAlertLevel("MEDIUM");
        }

        alert.setPositiveRate(BigDecimal.valueOf(result.getPositiveRate())
                .setScale(4, RoundingMode.HALF_UP));
        alert.setAffectedCount(result.getCasesInCluster());
        alert.setTotalCount(result.getTotalInCluster());

        if (result.getClusterCenter() != null) {
            alert.setClusterCenter(result.getClusterCenter());
        }
        if (result.getClusterRadius() > 0) {
            alert.setClusterRadius(BigDecimal.valueOf(result.getClusterRadius())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        alert.setStartTime(result.getStartTime() != null ? result.getStartTime() : LocalDateTime.now().minusDays(1));
        alert.setDescription(generateAlertDescription(barracks, result));
        alert.setStatus("ACTIVE");

        return alert;
    }

    private String generateAlertDescription(Barracks barracks, SatscanEpidemicDetector.ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("兵营 [").append(barracks.getName()).append("] 检测到肠道感染聚集性疫情。");
        sb.append("统计时间范围: ").append(result.getStartTime() != null ?
                result.getStartTime().toLocalDate() : "未知").append(" 至 ")
                .append(LocalDate.now()).append("。");
        sb.append("检测样本 ").append(result.getTotalInCluster()).append(" 份，")
                .append("阳性 ").append(result.getCasesInCluster()).append(" 份，")
                .append("阳性率 ").append(String.format("%.2f%%", result.getPositiveRate() * 100)).append("。");
        sb.append("对数似然比(LLR): ").append(String.format("%.4f", result.getLogLikelihoodRatio()))
                .append("，p值: ").append(String.format("%.4f", result.getPValue())).append("。");
        sb.append("建议立即采取隔离措施，加强饮水和食品安全监管，对受影响区域进行消毒处理。");
        return sb.toString();
    }

    public List<EpidemicAlert> getActiveAlerts() {
        return epidemicAlertRepository.findByStatus("ACTIVE");
    }

    public List<EpidemicAlert> getAlertsByBarracks(Long barracksId) {
        return epidemicAlertRepository.findByBarracksId(barracksId);
    }

    public Map<String, Object> getBarracksInfectionStats(Long barracksId) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(7);
        LocalDateTime endTime = LocalDateTime.now();

        Long total = fecalSensorDataRepository.countByBarracksIdAndSampleTimeBetween(
                barracksId, startTime, endTime);
        Long positive = fecalSensorDataRepository.countPositiveByBarracksIdAndSampleTimeBetween(
                barracksId, startTime, endTime);

        double rate = total > 0 ? (double) positive / total : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("barracksId", barracksId);
        stats.put("totalSamples", total);
        stats.put("positiveCount", positive);
        stats.put("positiveRate", rate);
        stats.put("positiveRatePercent", String.format("%.2f%%", rate * 100));
        stats.put("thresholdExceeded", rate > positiveRateThreshold);
        stats.put("periodDays", 7);

        return stats;
    }
}
