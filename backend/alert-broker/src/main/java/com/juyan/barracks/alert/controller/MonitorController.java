package com.juyan.barracks.alert.controller;

import com.juyan.barracks.alert.service.BarracksService;
import com.juyan.barracks.alert.service.SoldierService;
import com.juyan.barracks.common.dto.SoldierWithRiskDTO;
import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.EpidemicAlert;
import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.EpidemicAlertRepository;
import com.juyan.barracks.common.repository.NutritionRiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MonitorController {

    private final BarracksService barracksService;
    private final SoldierService soldierService;
    private final NutritionRiskRepository nutritionRiskRepository;
    private final EpidemicAlertRepository epidemicAlertRepository;

    @GetMapping("/barracks")
    public ResponseEntity<List<Barracks>> getAllBarracks() {
        return ResponseEntity.ok(barracksService.findAll());
    }

    @GetMapping("/barracks/{id}")
    public ResponseEntity<Barracks> getBarracksById(@PathVariable Long id) {
        Optional<Barracks> barracks = barracksService.findById(id);
        return barracks.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/barracks/{id}/soldiers/with-risk")
    public ResponseEntity<List<SoldierWithRiskDTO>> getSoldiersWithRiskByBarracks(@PathVariable Long id) {
        return ResponseEntity.ok(soldierService.findByBarracksIdWithRisk(id));
    }

    @GetMapping("/barracks/{id}/infection-stats")
    public ResponseEntity<Map<String, Object>> getBarracksInfectionStats(@PathVariable Long id) {
        List<EpidemicAlert> alerts = epidemicAlertRepository.findByBarracksIdAndStatus(id, "ACTIVE");

        Map<String, Object> stats = new HashMap<>();
        stats.put("barracksId", id);
        stats.put("activeAlertCount", alerts.size());

        if (!alerts.isEmpty()) {
            EpidemicAlert latestAlert = alerts.get(0);
            stats.put("latestAlertLevel", latestAlert.getAlertLevel());
            stats.put("latestPositiveRate", latestAlert.getPositiveRate());
            stats.put("latestAffectedCount", latestAlert.getAffectedCount());
            stats.put("latestTotalCount", latestAlert.getTotalCount());
        } else {
            stats.put("latestAlertLevel", "NONE");
            stats.put("latestPositiveRate", 0);
            stats.put("latestAffectedCount", 0);
            stats.put("latestTotalCount", 0);
        }

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/soldiers/with-risk")
    public ResponseEntity<List<SoldierWithRiskDTO>> getAllSoldiersWithRisk() {
        return ResponseEntity.ok(soldierService.findAllWithRisk());
    }

    @GetMapping("/soldiers/{id}")
    public ResponseEntity<Soldier> getSoldierById(@PathVariable Long id) {
        Optional<Soldier> soldier = soldierService.findById(id);
        return soldier.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nutrition-risks")
    public ResponseEntity<List<NutritionRisk>> getCurrentNutritionRisks() {
        return ResponseEntity.ok(nutritionRiskRepository.findAllCurrent());
    }

    @GetMapping("/epidemic-alerts")
    public ResponseEntity<List<EpidemicAlert>> getActiveEpidemicAlerts() {
        return ResponseEntity.ok(epidemicAlertRepository.findByStatus("ACTIVE"));
    }

    @PostMapping("/nutrition-prediction/run")
    public ResponseEntity<Map<String, Object>> runNutritionPrediction() {
        log.info("手动触发营养预测任务");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "营养预测任务已触发");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/epidemic-scan/run")
    public ResponseEntity<Map<String, Object>> runEpidemicScan() {
        log.info("手动触发疫情扫描任务");
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("message", "疫情扫描任务已触发");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dashboard/summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<Barracks> barracksList = barracksService.findAll();
        List<SoldierWithRiskDTO> soldiers = soldierService.findAllWithRisk();
        List<NutritionRisk> highRisks = nutritionRiskRepository.findAllCurrent().stream()
                .filter(r -> "HIGH".equals(r.getRiskLevel()) || "CRITICAL".equals(r.getRiskLevel()))
                .toList();
        List<EpidemicAlert> activeAlerts = epidemicAlertRepository.findByStatus("ACTIVE");

        long lowRisk = soldiers.stream().filter(s -> "LOW".equals(s.getRiskLevel())).count();
        long mediumRisk = soldiers.stream().filter(s -> "MEDIUM".equals(s.getRiskLevel())).count();
        long highRisk = soldiers.stream().filter(s -> "HIGH".equals(s.getRiskLevel())).count();
        long criticalRisk = soldiers.stream().filter(s -> "CRITICAL".equals(s.getRiskLevel())).count();

        summary.put("totalBarracks", barracksList.size());
        summary.put("totalSoldiers", soldiers.size());
        summary.put("nutritionRiskStats", Map.of(
                "LOW", lowRisk,
                "MEDIUM", mediumRisk,
                "HIGH", highRisk,
                "CRITICAL", criticalRisk
        ));
        summary.put("highRiskCount", highRisks.size());
        summary.put("activeEpidemicAlerts", activeAlerts.size());
        summary.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(summary);
    }
}
