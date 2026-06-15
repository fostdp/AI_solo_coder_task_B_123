package com.juyan.barracks.mealplanner.service;

import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.FoodItem;
import com.juyan.barracks.common.entity.MealPlan;
import com.juyan.barracks.common.entity.MealPlanItem;
import com.juyan.barracks.common.entity.PhysicalActivity;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.BarracksRepository;
import com.juyan.barracks.common.repository.FoodItemRepository;
import com.juyan.barracks.common.repository.MealPlanRepository;
import com.juyan.barracks.common.repository.PhysicalActivityRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import com.juyan.barracks.mealplanner.algorithm.MipMealPlanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealPlanningService {

    private final FoodItemRepository foodItemRepository;
    private final MealPlanRepository mealPlanRepository;
    private final BarracksRepository barracksRepository;
    private final SoldierRepository soldierRepository;
    private final PhysicalActivityRepository physicalActivityRepository;

    @Value("${meal-planner.target-protein:80}")
    private double targetProtein;

    @Value("${meal-planner.target-fat:50}")
    private double targetFat;

    @Value("${meal-planner.target-vitamin-c:100}")
    private double targetVitaminC;

    @Value("${meal-planner.calorie-min:2500}")
    private double calorieMin;

    @Value("${meal-planner.calorie-max:3500}")
    private double calorieMax;

    @Value("${meal-planner.training-boost:0.2}")
    private double trainingBoost;

    @Scheduled(cron = "${scheduling.meal-planning.cron:0 0 2 ? * MON}")
    @Transactional
    public void scheduledWeeklyPlanning() {
        log.info("开始执行定时每周食谱生成任务...");
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        List<Barracks> allBarracks = barracksRepository.findAll();
        int successCount = 0;
        int failCount = 0;

        for (Barracks barracks : allBarracks) {
            try {
                List<Soldier> soldiers = soldierRepository.findByBarracksId(barracks.getId());
                int soldierCount = soldiers.isEmpty() ? barracks.getCapacity() : soldiers.size();
                generateWeeklyPlan(barracks.getId(), nextMonday, soldierCount);
                successCount++;
            } catch (Exception e) {
                log.error("兵营 {} 食谱生成失败: {}", barracks.getId(), e.getMessage(), e);
                failCount++;
            }
        }

        log.info("定时每周食谱生成任务完成: 成功兵营数={}, 失败兵营数={}", successCount, failCount);
    }

    @Transactional
    public MealPlan generateWeeklyPlan(Long barracksId, LocalDate startDate, int soldierCount) {
        log.info("生成一周食谱: barracksId={}, startDate={}, soldierCount={}",
                barracksId, startDate, soldierCount);

        List<FoodItem> foodItems = foodItemRepository.findByIsAvailableTrue();
        if (foodItems.isEmpty()) {
            foodItems = foodItemRepository.findAll();
        }

        boolean highTraining = calculateIsHighTraining(barracksId, startDate);

        double adjustedProtein = highTraining ? targetProtein * (1 + trainingBoost) : targetProtein;
        double adjustedFat = highTraining ? targetFat * (1 + trainingBoost) : targetFat;
        double adjustedVitaminC = highTraining ? targetVitaminC * (1 + trainingBoost) : targetVitaminC;
        double adjustedCalorieMin = highTraining ? calorieMin * (1 + trainingBoost) : calorieMin;
        double adjustedCalorieMax = highTraining ? calorieMax * (1 + trainingBoost) : calorieMax;

        log.debug("营养目标调整: 高蛋白训练={}, 蛋白质={}g, 脂肪={}g, 维C={}mg, 热量={}-{}kcal",
                highTraining, adjustedProtein, adjustedFat, adjustedVitaminC,
                adjustedCalorieMin, adjustedCalorieMax);

        MipMealPlanner planner = new MipMealPlanner(
                foodItems, soldierCount,
                adjustedProtein, adjustedFat, adjustedVitaminC,
                adjustedCalorieMin, adjustedCalorieMax
        );

        MipMealPlanner.MealPlanResult planResult = planner.solve();
        LocalDate endDate = startDate.plusDays(6);

        MealPlan mealPlan = new MealPlan();
        mealPlan.setBarracksId(barracksId);
        mealPlan.setPlanName(buildPlanName(barracksId, startDate));
        mealPlan.setStartDate(startDate);
        mealPlan.setEndDate(endDate);
        mealPlan.setTargetSoldierCount(soldierCount);
        mealPlan.setTotalCost(planResult.getTotalCost());
        mealPlan.setDailyProteinG(planResult.getAvgDailyProtein().setScale(2, RoundingMode.HALF_UP));
        mealPlan.setDailyFatG(planResult.getAvgDailyFat().setScale(2, RoundingMode.HALF_UP));
        mealPlan.setDailyVitaminCMg(planResult.getAvgDailyVitaminC().setScale(2, RoundingMode.HALF_UP));
        mealPlan.setDailyCalorieKcal(planResult.getAvgDailyCalorie().setScale(2, RoundingMode.HALF_UP));
        mealPlan.setSolverStatus(planResult.getSolverStatus());
        mealPlan.setIsActive(true);
        mealPlan.setCreatedAt(LocalDateTime.now());

        List<MealPlanItem> items = convertToMealPlanItems(planResult.getMealItems(), mealPlan);
        mealPlan.setItems(items);

        mealPlanRepository.findActiveByBarracksId(barracksId).ifPresent(oldPlan -> {
            oldPlan.setIsActive(false);
            mealPlanRepository.save(oldPlan);
        });

        MealPlan saved = mealPlanRepository.save(mealPlan);

        log.info("食谱生成完成: id={}, 总成本={}, 求解状态={}, 食物项数={}",
                saved.getId(), saved.getTotalCost(), saved.getSolverStatus(), items.size());

        return saved;
    }

    public List<MealPlan> getMealPlansByBarracks(Long barracksId) {
        if (barracksId != null) {
            return mealPlanRepository.findByBarracksIdOrderByCreatedAtDesc(barracksId);
        }
        return mealPlanRepository.findTop10ByOrderByCreatedAtDesc();
    }

    public Optional<MealPlan> getMealPlanById(Long id) {
        return mealPlanRepository.findById(id);
    }

    public List<FoodItem> getAllFoodItems() {
        return foodItemRepository.findByIsAvailableTrue();
    }

    private boolean calculateIsHighTraining(Long barracksId, LocalDate startDate) {
        List<Soldier> soldiers = soldierRepository.findByBarracksId(barracksId);
        if (soldiers.isEmpty()) {
            return false;
        }

        LocalDate endDate = startDate.minusDays(1);
        LocalDate beginDate = endDate.minusDays(6);

        BigDecimal totalCalories = BigDecimal.ZERO;
        int countWithActivity = 0;

        for (Soldier soldier : soldiers) {
            BigDecimal burned = physicalActivityRepository.sumCalorieBurnedBySoldierAndDate(
                    soldier.getId(), beginDate, endDate);
            if (burned != null && burned.compareTo(BigDecimal.ZERO) > 0) {
                totalCalories = totalCalories.add(burned);
                countWithActivity++;
            }
        }

        if (countWithActivity == 0) {
            return false;
        }

        BigDecimal avgBurnedPerSoldier = totalCalories.divide(BigDecimal.valueOf(soldiers.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgBurnedPerDay = avgBurnedPerSoldier.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);

        boolean isHigh = avgBurnedPerDay.compareTo(BigDecimal.valueOf(600)) >= 0;
        log.debug("兵营 {} 训练强度评估: 人均日消耗={}kcal, 判定高强度={}",
                barracksId, avgBurnedPerDay, isHigh);
        return isHigh;
    }

    private String buildPlanName(Long barracksId, LocalDate startDate) {
        String barracksName = barracksRepository.findById(barracksId)
                .map(Barracks::getName)
                .orElse("兵营" + barracksId);
        LocalDate endDate = startDate.plusDays(6);
        return String.format("%s 食谱 %s ~ %s",
                barracksName,
                startDate.toString(),
                endDate.toString());
    }

    private List<MealPlanItem> convertToMealPlanItems(List<MipMealPlanner.MealItemDetail> details,
                                                      MealPlan mealPlan) {
        List<MealPlanItem> items = new ArrayList<>();
        for (MipMealPlanner.MealItemDetail detail : details) {
            MealPlanItem item = new MealPlanItem();
            item.setMealPlan(mealPlan);
            item.setDayOfWeek(detail.getDayOfWeek());
            item.setMealType(detail.getMealType());
            item.setFoodItemId(detail.getFoodItemId());
            item.setFoodName(detail.getFoodName());
            item.setQuantityGrams(detail.getQuantityGrams());
            item.setProteinG(detail.getProteinG());
            item.setFatG(detail.getFatG());
            item.setVitaminCMg(detail.getVitaminCMg());
            item.setCalorieKcal(detail.getCalorieKcal());
            item.setCost(detail.getCost());
            items.add(item);
        }
        return items;
    }

    public Map<String, Object> getPlanSummary(MealPlan plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", plan.getId());
        summary.put("barracksId", plan.getBarracksId());
        summary.put("planName", plan.getPlanName());
        summary.put("startDate", plan.getStartDate());
        summary.put("endDate", plan.getEndDate());
        summary.put("targetSoldierCount", plan.getTargetSoldierCount());
        summary.put("totalCost", plan.getTotalCost());
        summary.put("solverStatus", plan.getSolverStatus());
        summary.put("isActive", plan.getIsActive());
        summary.put("createdAt", plan.getCreatedAt());

        Map<String, BigDecimal> dailyAvg = new LinkedHashMap<>();
        dailyAvg.put("proteinG", plan.getDailyProteinG());
        dailyAvg.put("fatG", plan.getDailyFatG());
        dailyAvg.put("vitaminCMg", plan.getDailyVitaminCMg());
        dailyAvg.put("calorieKcal", plan.getDailyCalorieKcal());
        summary.put("dailyAverage", dailyAvg);

        if (plan.getItems() != null) {
            Map<Integer, Map<String, List<Map<String, Object>>>> groupedByDay = new TreeMap<>();
            for (MealPlanItem item : plan.getItems()) {
                groupedByDay
                        .computeIfAbsent(item.getDayOfWeek(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(item.getMealType(), k -> new ArrayList<>())
                        .add(convertItemToMap(item));
            }
            summary.put("weeklyPlan", groupedByDay);
        }

        return summary;
    }

    private Map<String, Object> convertItemToMap(MealPlanItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("foodItemId", item.getFoodItemId());
        map.put("foodName", item.getFoodName());
        map.put("quantityGrams", item.getQuantityGrams());
        map.put("proteinG", item.getProteinG());
        map.put("fatG", item.getFatG());
        map.put("vitaminCMg", item.getVitaminCMg());
        map.put("calorieKcal", item.getCalorieKcal());
        map.put("cost", item.getCost());
        return map;
    }
}
