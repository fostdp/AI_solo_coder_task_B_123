package com.juyan.barracks.supply.controller;

import com.juyan.barracks.common.entity.SupplyDeficitRecord;
import com.juyan.barracks.supply.algorithm.AssociationRuleResult;
import com.juyan.barracks.supply.dto.*;
import com.juyan.barracks.supply.service.SupplyAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SupplyAnalysisController {

    private final SupplyAnalysisService supplyAnalysisService;

    @GetMapping("/deficit-records")
    public ResponseEntity<List<SupplyDeficitRecord>> getDeficitRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long barracksId) {
        log.info("查询短缺记录: startDate={}, endDate={}, barracksId={}", startDate, endDate, barracksId);
        List<SupplyDeficitRecord> records = supplyAnalysisService.getDeficitRecords(startDate, endDate, barracksId);
        return ResponseEntity.ok(records);
    }

    @GetMapping("/association-rules")
    public ResponseEntity<List<AssociationRuleResult>> getAllAssociationRules() {
        log.info("查询所有关联规则");
        List<AssociationRuleResult> rules = supplyAnalysisService.getAllAssociationRules();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/association-rules/significant")
    public ResponseEntity<List<AssociationRuleResult>> getSignificantRules() {
        log.info("查询显著关联规则(lift>1.5)");
        List<AssociationRuleResult> rules = supplyAnalysisService.getSignificantRules();
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/supply-analysis/run")
    public ResponseEntity<AnalysisResultDTO> runAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate == null) {
            endDate = LocalDate.now();
            startDate = endDate.minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        log.info("手动运行补给短缺分析: {} ~ {}", startDate, endDate);
        AnalysisResultDTO result = supplyAnalysisService.analyzeSupplyDeficits(startDate, endDate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/supply-analysis/top-deficits")
    public ResponseEntity<Map<String, Object>> getTopDeficits(
            @RequestParam(defaultValue = "5") int limit) {
        log.info("查询Top短缺物资统计: limit={}", limit);
        List<TopDeficitItem> items = supplyAnalysisService.getTopDeficitItems(limit);
        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", items.size());
        response.put("generatedAt", java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/supply-analysis/weather-impact")
    public ResponseEntity<Map<String, Object>> getWeatherImpact() {
        log.info("查询天气对补给的影响");
        List<WeatherImpactStat> stats = supplyAnalysisService.getWeatherImpactStats();
        List<RouteImpactStat> routeStats = supplyAnalysisService.getRouteImpactStats();
        Map<String, Object> response = new HashMap<>();
        response.put("weatherStats", stats);
        response.put("routeStats", routeStats);
        response.put("generatedAt", java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/supply-analysis/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "supply-apriori");
        response.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }
}
