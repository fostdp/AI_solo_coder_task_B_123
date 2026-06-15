package com.juyan.barracks.mealplanner.algorithm;

import com.juyan.barracks.common.entity.FoodItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MipMealPlannerTest {

    private FoodItem createFoodItem(Long id, String name, String category,
                                     double protein, double fat, double vitaminC,
                                     double calorie, double costPerKg) {
        FoodItem item = new FoodItem();
        item.setId(id);
        item.setName(name);
        item.setCategory(category);
        item.setProteinPer100g(BigDecimal.valueOf(protein));
        item.setFatPer100g(BigDecimal.valueOf(fat));
        item.setVitaminCPer100gMg(BigDecimal.valueOf(vitaminC));
        item.setCaloriePer100gKcal(BigDecimal.valueOf(calorie));
        item.setCostPerKg(BigDecimal.valueOf(costPerKg));
        item.setIsAvailable(true);
        return item;
    }

    private List<FoodItem> createBasicFoodItems() {
        List<FoodItem> items = new ArrayList<>();
        items.add(createFoodItem(1L, "粟米", "主食", 9.7, 3.5, 0, 360, 8.0));
        items.add(createFoodItem(2L, "小麦", "主食", 11.9, 1.3, 0, 339, 7.5));
        items.add(createFoodItem(3L, "大豆", "豆制品", 35.0, 16.0, 0, 359, 6.0));
        items.add(createFoodItem(4L, "牛肉", "肉类", 20.2, 2.3, 0, 125, 80.0));
        items.add(createFoodItem(5L, "鸡蛋", "蛋类", 13.3, 8.8, 0, 144, 12.0));
        items.add(createFoodItem(6L, "葵菜", "蔬菜", 1.5, 0.2, 28, 23, 6.0));
        return items;
    }

    private List<FoodItem> createRichFoodItems() {
        List<FoodItem> items = createBasicFoodItems();
        items.add(createFoodItem(7L, "白菜", "蔬菜", 1.5, 0.1, 28, 17, 5.0));
        items.add(createFoodItem(8L, "干枣", "干果", 3.2, 0.5, 14, 247, 30.0));
        items.add(createFoodItem(9L, "羊肉", "肉类", 28.8, 4.2, 0, 158, 70.0));
        items.add(createFoodItem(10L, "韭菜", "蔬菜", 2.4, 0.4, 24, 26, 12.0));
        items.add(createFoodItem(11L, "豆腐", "豆制品", 8.1, 3.7, 0, 81, 4.0));
        items.add(createFoodItem(12L, "菠菜", "蔬菜", 2.6, 0.3, 32, 24, 7.0));
        return items;
    }

    @Test
    void testEmptyFoodItems() {
        List<FoodItem> emptyList = new ArrayList<>();
        MipMealPlanner planner = new MipMealPlanner(emptyList, 100,
                80, 50, 100, 2000, 3000);

        MipMealPlanner.MealPlanResult result = planner.solve();

        assertNotNull(result, "结果不应为空");
        assertFalse(result.isFeasible(), "空食材列表应返回不可行状态（默认食谱）");
        assertEquals("FALLBACK_DEFAULT", result.getSolverStatus(),
                "空食材列表应标记为FALLBACK_DEFAULT");
        assertNotNull(result.getDailyNutritionMap(), "每日营养映射不应为空");
        assertNotNull(result.getMealItems(), "餐单项列表不应为空");
        assertFalse(result.getMealItems().isEmpty(), "默认食谱应包含餐单项");
        assertNotNull(result.getTotalCost(), "总成本不应为空");
    }

    @Test
    void testBasicFeasibility() {
        List<FoodItem> basicItems = createBasicFoodItems();
        MipMealPlanner planner = new MipMealPlanner(basicItems, 50,
                80, 50, 100, 1800, 3500);

        MipMealPlanner.MealPlanResult result = planner.solve();

        assertNotNull(result, "求解结果不应为空");
        assertNotNull(result.getSolverStatus(), "求解器状态不应为空");
        assertNotNull(result.getDailyNutritionMap(), "每日营养映射不应为空");
        assertEquals(7, result.getDailyNutritionMap().size(), "应包含7天的营养数据");
        assertNotNull(result.getMealItems(), "餐单项列表不应为空");
        assertNotNull(result.getTotalCost(), "总成本不应为空");
        assertNotNull(result.getAvgDailyProtein(), "日均蛋白质不应为空");
        assertNotNull(result.getAvgDailyFat(), "日均脂肪不应为空");
        assertNotNull(result.getAvgDailyVitaminC(), "日均维C不应为空");
        assertNotNull(result.getAvgDailyCalorie(), "日均热量不应为空");

        if (!result.isFeasible()) {
            assertEquals("FALLBACK_DEFAULT", result.getSolverStatus(),
                    "不可行时应回退到默认食谱");
        }
    }

    @Test
    void testNutritionConstraintsMet() {
        List<FoodItem> richItems = createRichFoodItems();
        MipMealPlanner planner = new MipMealPlanner(richItems, 100,
                80, 50, 100, 2000, 3000);

        MipMealPlanner.MealPlanResult result = planner.solve();

        assertNotNull(result, "求解结果不应为空");
        assertNotNull(result.getDailyNutritionMap(), "每日营养映射不应为空");

        Map<Integer, MipMealPlanner.DailyNutrition> nutritionMap = result.getDailyNutritionMap();
        for (Map.Entry<Integer, MipMealPlanner.DailyNutrition> entry : nutritionMap.entrySet()) {
            MipMealPlanner.DailyNutrition dn = entry.getValue();
            assertNotNull(dn, "第" + entry.getKey() + "天的营养数据不应为空");

            if (result.isFeasible()) {
                assertTrue(dn.getProtein().compareTo(BigDecimal.valueOf(80)) >= 0,
                        "第" + entry.getKey() + "天蛋白质应≥80g，实际: " + dn.getProtein());
                assertTrue(dn.getFat().compareTo(BigDecimal.valueOf(50)) >= 0,
                        "第" + entry.getKey() + "天脂肪应≥50g，实际: " + dn.getFat());
                assertTrue(dn.getVitaminC().compareTo(BigDecimal.valueOf(100)) >= 0,
                        "第" + entry.getKey() + "天维C应≥100mg，实际: " + dn.getVitaminC());
            }
        }

        if (result.isFeasible()) {
            assertTrue(result.getAvgDailyProtein().compareTo(BigDecimal.valueOf(80)) >= 0,
                    "日均蛋白质应≥80g，实际: " + result.getAvgDailyProtein());
            assertTrue(result.getAvgDailyFat().compareTo(BigDecimal.valueOf(50)) >= 0,
                    "日均脂肪应≥50g，实际: " + result.getAvgDailyFat());
            assertTrue(result.getAvgDailyVitaminC().compareTo(BigDecimal.valueOf(100)) >= 0,
                    "日均维C应≥100mg，实际: " + result.getAvgDailyVitaminC());
        }
    }

    @Test
    void testSolverResultFields() {
        List<FoodItem> items = createRichFoodItems();
        MipMealPlanner planner = new MipMealPlanner(items, 80,
                70, 40, 80, 1800, 3200);

        MipMealPlanner.MealPlanResult result = planner.solve();

        assertNotNull(result, "求解结果不应为空");

        assertNotNull(result.getDailyNutritionMap(), "dailyNutritionMap字段不应为空");
        assertFalse(result.getDailyNutritionMap().isEmpty(),
                "dailyNutritionMap应包含7天数据");
        assertEquals(7, result.getDailyNutritionMap().size(),
                "dailyNutritionMap应恰好7天");

        assertNotNull(result.getMealItems(), "mealItems字段不应为空");
        assertFalse(result.getMealItems().isEmpty(),
                "mealItems应包含至少一些餐单项");

        for (MipMealPlanner.MealItemDetail item : result.getMealItems()) {
            assertNotNull(item.getDayOfWeek(), "餐单项的dayOfWeek不应为空");
            assertNotNull(item.getMealType(), "餐单项的mealType不应为空");
            assertNotNull(item.getFoodName(), "餐单项的foodName不应为空");
            assertNotNull(item.getQuantityGrams(), "餐单项的quantityGrams不应为空");
            assertTrue(item.getQuantityGrams().compareTo(BigDecimal.ZERO) > 0,
                    "餐单项数量应>0");
        }

        assertNotNull(result.getTotalCost(), "totalCost字段不应为空");
        assertTrue(result.getTotalCost().compareTo(BigDecimal.ZERO) >= 0,
                "总成本应非负");

        assertNotNull(result.getAvgDailyProtein(), "avgDailyProtein字段不应为空");
        assertNotNull(result.getAvgDailyFat(), "avgDailyFat字段不应为空");
        assertNotNull(result.getAvgDailyVitaminC(), "avgDailyVitaminC字段不应为空");
        assertNotNull(result.getAvgDailyCalorie(), "avgDailyCalorie字段不应为空");
        assertNotNull(result.getSolverStatus(), "solverStatus字段不应为空");
    }
}
