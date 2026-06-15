package com.juyan.barracks.seir.controller;

import com.juyan.barracks.common.entity.ContactEdge;
import com.juyan.barracks.common.entity.SeirSimulation;
import com.juyan.barracks.common.entity.SeirTimePoint;
import com.juyan.barracks.seir.service.SeirSimulationService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class SeirSimulationController {

    private final SeirSimulationService seirSimulationService;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunSimulationRequest {
        private Long barracksId;
        private Integer initialInfected;
        private Integer days;
        private Double beta;
        private Double sigma;
        private Double gamma;
        private Integer quarantineStartDay;
        private Double isolationEffectiveness;
    }

    @GetMapping("/seir-simulations")
    public ResponseEntity<Map<String, Object>> getSimulations(
            @RequestParam(required = false) Long barracksId) {
        log.info("查询SEIR模拟列表, barracksId={}", barracksId);

        List<SeirSimulation> simulations;
        if (barracksId != null) {
            simulations = seirSimulationService.getSimulationsByBarracks(barracksId);
        } else {
            simulations = seirSimulationService.getRecentSimulations();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", simulations);
        result.put("total", simulations.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/seir-simulations/{id}")
    public ResponseEntity<Map<String, Object>> getSimulationDetail(@PathVariable Long id) {
        log.info("查询SEIR模拟详情, id={}", id);

        SeirSimulation simulation = seirSimulationService.getSimulationDetail(id);
        Map<String, Object> result = new HashMap<>();

        if (simulation == null) {
            result.put("success", false);
            result.put("message", "模拟记录不存在");
            return ResponseEntity.notFound().build();
        }

        result.put("success", true);
        result.put("data", simulation);

        List<Map<String, Object>> curveData = new java.util.ArrayList<>();
        if (simulation.getTimePoints() != null) {
            for (SeirTimePoint tp : simulation.getTimePoints()) {
                Map<String, Object> point = new HashMap<>();
                point.put("day", tp.getDay());
                point.put("S", tp.getSusceptibleCount());
                point.put("E", tp.getExposedCount());
                point.put("I", tp.getInfectedCount());
                point.put("R", tp.getRecoveredCount());
                point.put("Q", tp.getQuarantinedCount());
                curveData.add(point);
            }
        }
        result.put("curveData", curveData);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/seir-simulations/run")
    public ResponseEntity<Map<String, Object>> runSimulation(@RequestBody RunSimulationRequest request) {
        log.info("手动触发SEIR模拟: barracksId={}, initialInfected={}, days={}",
                request.getBarracksId(), request.getInitialInfected(), request.getDays());

        Map<String, Object> result = new HashMap<>();

        if (request.getBarracksId() == null) {
            result.put("success", false);
            result.put("message", "barracksId不能为空");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            SeirSimulationService.SimulationRequest simRequest = SeirSimulationService.SimulationRequest.builder()
                    .barracksId(request.getBarracksId())
                    .initialInfected(request.getInitialInfected())
                    .days(request.getDays())
                    .beta(request.getBeta())
                    .sigma(request.getSigma())
                    .gamma(request.getGamma())
                    .quarantineStartDay(request.getQuarantineStartDay())
                    .isolationEffectiveness(request.getIsolationEffectiveness())
                    .build();

            SeirSimulationService.SimulationComparison comparison = seirSimulationService.runSimulation(simRequest);

            if (comparison == null) {
                result.put("success", false);
                result.put("message", "模拟失败：兵营无士兵数据");
                return ResponseEntity.badRequest().body(result);
            }

            result.put("success", true);
            result.put("message", "SEIR模拟完成");
            result.put("noQuarantine", comparison.getNoQuarantineSimulation());
            result.put("withQuarantine", comparison.getWithQuarantineSimulation());
            result.put("infectionReductionPercent", comparison.getInfectionReductionPercent());
            result.put("peakDelayDays", comparison.getPeakDelayDays());
            result.put("summary", comparison.getSummary());

            log.info("SEIR模拟完成: 感染减少={}%, 峰值延迟={}天",
                    comparison.getInfectionReductionPercent(), comparison.getPeakDelayDays());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("SEIR模拟失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "模拟失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/contact-edges/{barracksId}")
    public ResponseEntity<Map<String, Object>> getContactEdges(@PathVariable Long barracksId) {
        log.info("查询兵营接触网络: barracksId={}", barracksId);

        List<ContactEdge> edges = seirSimulationService.getContactEdges(barracksId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", edges);
        result.put("total", edges.size());

        if (!edges.isEmpty()) {
            double avgFreq = edges.stream()
                    .mapToDouble(e -> e.getContactFrequencyPerDay() != null ? e.getContactFrequencyPerDay() : 0)
                    .average()
                    .orElse(0);
            long roommateCount = edges.stream()
                    .filter(e -> "ROOMMATE".equals(e.getContactType()))
                    .count();
            long tableCount = edges.stream()
                    .filter(e -> "TABLE".equals(e.getContactType()))
                    .count();
            result.put("avgContactFrequency", avgFreq);
            result.put("roommateEdgeCount", roommateCount);
            result.put("tableEdgeCount", tableCount);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/seir-simulations/{noQId}/compare/{withQId}")
    public ResponseEntity<Map<String, Object>> compareSimulations(
            @PathVariable Long noQId, @PathVariable Long withQId) {
        log.info("对比模拟效果: noQId={}, withQId={}", noQId, withQId);

        Map<String, Object> evaluation = seirSimulationService.evaluateQuarantineEffect(noQId, withQId);

        Map<String, Object> result = new HashMap<>();
        if (evaluation.containsKey("error")) {
            result.put("success", false);
            result.put("message", evaluation.get("error"));
            return ResponseEntity.badRequest().body(result);
        }

        result.put("success", true);
        result.put("data", evaluation);
        return ResponseEntity.ok(result);
    }
}
