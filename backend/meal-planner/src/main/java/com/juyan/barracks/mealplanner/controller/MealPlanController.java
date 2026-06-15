package com.juyan.barracks.mealplanner.controller;

import com.juyan.barracks.common.entity.FoodItem;
import com.juyan.barracks.common.entity.MealPlan;
import com.juyan.barracks.mealplanner.service.MealPlanningService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MealPlanController {

    private final MealPlanningService mealPlanningService;

    @GetMapping("/meal-plans")
    public ResponseEntity<List<Map<String, Object>>> getMealPlans(
            @RequestParam(required = false) Long barracksId) {
        log.debug("查询食谱列表: barracksId={}", barracksId);
        List<MealPlan> plans = mealPlanningService.getMealPlansByBarracks(barracksId);
        List<Map<String, Object>> result = plans.stream()
                .map(mealPlanningService::getPlanSummary)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/meal-plans/{id}")
    public ResponseEntity<Map<String, Object>> getMealPlanById(@PathVariable Long id) {
        log.debug("查询食谱详情: id={}", id);
        Optional<MealPlan> planOpt = mealPlanningService.getMealPlanById(id);
        return planOpt
                .map(plan -> ResponseEntity.ok(mealPlanningService.getPlanSummary(plan)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/meal-plans/generate")
    public ResponseEntity<Map<String, Object>> generateMealPlan(
            @RequestBody GenerateMealPlanRequest request) {
        log.info("手动触发生成食谱: barracksId={}, startDate={}, soldierCount={}",
                request.getBarracksId(), request.getStartDate(), request.getSoldierCount());

        try {
            if (request.getBarracksId() == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("status", "error");
                error.put("message", "barracksId不能为空");
                return ResponseEntity.badRequest().body(error);
            }

            LocalDate startDate = request.getStartDate() != null ?
                    request.getStartDate() : LocalDate.now();

            Integer soldierCount = request.getSoldierCount();
            if (soldierCount == null || soldierCount <= 0) {
                soldierCount = 100;
            }

            MealPlan saved = mealPlanningService.generateWeeklyPlan(
                    request.getBarracksId(), startDate, soldierCount);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "食谱生成成功");
            result.put("data", mealPlanningService.getPlanSummary(saved));
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("生成食谱失败: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "生成食谱失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/food-items")
    public ResponseEntity<List<FoodItem>> getAllFoodItems() {
        log.debug("查询食材列表");
        List<FoodItem> items = mealPlanningService.getAllFoodItems();
        return ResponseEntity.ok(items);
    }

    @Data
    public static class GenerateMealPlanRequest {
        private Long barracksId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;
        private Integer soldierCount;
    }
}
