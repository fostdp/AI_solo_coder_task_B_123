package com.juyan.barracks.mealplanner.algorithm;

import com.juyan.barracks.common.entity.FoodItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
public class MipMealPlanner {

    private static final int DAYS = 7;
    private static final String[] MEAL_TYPES = {"BREAKFAST", "LUNCH", "DINNER"};
    private static final double MIN_QUANTITY_GRAMS = 50.0;
    private static final double BIG_M = 10000.0;

    private final List<FoodItem> foodItems;
    private final int soldierCount;
    private final double targetProtein;
    private final double targetFat;
    private final double targetVitaminC;
    private final double calorieMin;
    private final double calorieMax;

    public MipMealPlanner(List<FoodItem> foodItems, int soldierCount,
                          double targetProtein, double targetFat, double targetVitaminC,
                          double calorieMin, double calorieMax) {
        this.foodItems = foodItems;
        this.soldierCount = soldierCount;
        this.targetProtein = targetProtein;
        this.targetFat = targetFat;
        this.targetVitaminC = targetVitaminC;
        this.calorieMin = calorieMin;
        this.calorieMax = calorieMax;
    }

    public MealPlanResult solve() {
        if (foodItems == null || foodItems.isEmpty()) {
            log.warn("食材列表为空，生成默认食谱");
            return buildDefaultPlan();
        }

        try {
            ExpressionsBasedModel model = new ExpressionsBasedModel();

            Map<String, Variable> quantityVars = new HashMap<>();
            Map<String, Variable> selectionVars = new HashMap<>();

            createVariables(model, quantityVars, selectionVars);
            addSelectionConstraints(model, quantityVars, selectionVars);
            addMealVarietyConstraints(model, selectionVars);
            addDailyVarietyConstraints(model, selectionVars);
            addNutritionConstraints(model, quantityVars);
            setObjectiveFunction(model, quantityVars);

            Optimisation.Result result = model.minimise();

            if (result.getState().isFeasible()) {
                log.info("MIP求解成功: 状态={}, 目标值={}", result.getState(), result.getValue());
                return extractResult(result, quantityVars, selectionVars, result.getState().toString());
            } else {
                log.warn("MIP求解不可行: 状态={}, 生成默认食谱", result.getState());
                return buildDefaultPlan();
            }
        } catch (Exception e) {
            log.error("MIP求解异常: {}", e.getMessage(), e);
            return buildDefaultPlan();
        }
    }

    private void createVariables(ExpressionsBasedModel model,
                                 Map<String, Variable> quantityVars,
                                 Map<String, Variable> selectionVars) {
        for (int day = 1; day <= DAYS; day++) {
            for (String mealType : MEAL_TYPES) {
                for (FoodItem food : foodItems) {
                    String qKey = buildQuantityKey(day, mealType, food.getId());
                    Variable qVar = model.addVariable(qKey)
                            .lower(BigDecimal.ZERO)
                            .upper(BigDecimal.valueOf(2000.0));
                    quantityVars.put(qKey, qVar);

                    String sKey = buildSelectionKey(day, mealType, food.getId());
                    Variable sVar = model.addVariable(sKey).binary();
                    selectionVars.put(sKey, sVar);
                }
            }
        }
    }

    private void addSelectionConstraints(ExpressionsBasedModel model,
                                         Map<String, Variable> quantityVars,
                                         Map<String, Variable> selectionVars) {
        for (int day = 1; day <= DAYS; day++) {
            for (String mealType : MEAL_TYPES) {
                for (FoodItem food : foodItems) {
                    String qKey = buildQuantityKey(day, mealType, food.getId());
                    String sKey = buildSelectionKey(day, mealType, food.getId());
                    Variable qVar = quantityVars.get(qKey);
                    Variable sVar = selectionVars.get(sKey);

                    model.addExpression("q_ub_" + qKey)
                            .set(qVar, BigDecimal.ONE)
                            .set(sVar, BigDecimal.valueOf(-BIG_M))
                            .upper(BigDecimal.ZERO);

                    model.addExpression("q_lb_" + qKey)
                            .set(qVar, BigDecimal.ONE)
                            .set(sVar, BigDecimal.valueOf(-MIN_QUANTITY_GRAMS))
                            .lower(BigDecimal.ZERO);
                }
            }
        }
    }

    private void addMealVarietyConstraints(ExpressionsBasedModel model,
                                           Map<String, Variable> selectionVars) {
        for (int day = 1; day <= DAYS; day++) {
            for (String mealType : MEAL_TYPES) {
                ExpressionsBasedModel.Expression expr =
                        model.addExpression("meal_variety_d" + day + "_" + mealType)
                                .lower(BigDecimal.valueOf(2));

                for (FoodItem food : foodItems) {
                    String sKey = buildSelectionKey(day, mealType, food.getId());
                    expr.set(selectionVars.get(sKey), BigDecimal.ONE);
                }
            }
        }
    }

    private void addDailyVarietyConstraints(ExpressionsBasedModel model,
                                            Map<String, Variable> selectionVars) {
        for (int day = 1; day <= DAYS; day++) {
            ExpressionsBasedModel.Expression expr =
                    model.addExpression("daily_variety_d" + day)
                            .lower(BigDecimal.valueOf(5));

            Set<Long> processedFoods = new HashSet<>();
            for (FoodItem food : foodItems) {
                if (processedFoods.contains(food.getId())) continue;
                processedFoods.add(food.getId());

                String dayKey = "daily_used_d" + day + "_f" + food.getId();
                Variable dayVar = model.addVariable(dayKey).binary();

                for (String mealType : MEAL_TYPES) {
                    String sKey = buildSelectionKey(day, mealType, food.getId());
                    Variable selVar = selectionVars.get(sKey);

                    model.addExpression("dayvar_ub_" + dayKey + "_" + mealType)
                            .set(dayVar, BigDecimal.ONE)
                            .set(selVar, BigDecimal.valueOf(-1))
                            .lower(BigDecimal.ZERO);
                }

                ExpressionsBasedModel.Expression lbExpr =
                        model.addExpression("dayvar_lb_" + dayKey)
                                .set(dayVar, BigDecimal.valueOf(-3));

                for (String mealType : MEAL_TYPES) {
                    String sKey = buildSelectionKey(day, mealType, food.getId());
                    lbExpr.set(selectionVars.get(sKey), BigDecimal.ONE);
                }
                lbExpr.lower(BigDecimal.ZERO);

                expr.set(dayVar, BigDecimal.ONE);
            }
        }
    }

    private void addNutritionConstraints(ExpressionsBasedModel model,
                                         Map<String, Variable> quantityVars) {
        for (int day = 1; day <= DAYS; day++) {
            ExpressionsBasedModel.Expression proteinExpr =
                    model.addExpression("protein_d" + day).lower(BigDecimal.valueOf(targetProtein));
            ExpressionsBasedModel.Expression fatExpr =
                    model.addExpression("fat_d" + day).lower(BigDecimal.valueOf(targetFat));
            ExpressionsBasedModel.Expression vitaminCExpr =
                    model.addExpression("vitamin_c_d" + day).lower(BigDecimal.valueOf(targetVitaminC));
            ExpressionsBasedModel.Expression calorieMinExpr =
                    model.addExpression("calorie_min_d" + day).lower(BigDecimal.valueOf(calorieMin));
            ExpressionsBasedModel.Expression calorieMaxExpr =
                    model.addExpression("calorie_max_d" + day).upper(BigDecimal.valueOf(calorieMax));

            for (String mealType : MEAL_TYPES) {
                for (FoodItem food : foodItems) {
                    String qKey = buildQuantityKey(day, mealType, food.getId());
                    Variable qVar = quantityVars.get(qKey);

                    BigDecimal proteinPerGram = safeDivide(food.getProteinPer100g(), 100);
                    BigDecimal fatPerGram = safeDivide(food.getFatPer100g(), 100);
                    BigDecimal vitaminCPerGram = safeDivide(food.getVitaminCPer100gMg(), 100);
                    BigDecimal caloriePerGram = safeDivide(food.getCaloriePer100gKcal(), 100);

                    proteinExpr.set(qVar, proteinPerGram);
                    fatExpr.set(qVar, fatPerGram);
                    vitaminCExpr.set(qVar, vitaminCPerGram);
                    calorieMinExpr.set(qVar, caloriePerGram);
                    calorieMaxExpr.set(qVar, caloriePerGram);
                }
            }
        }
    }

    private void setObjectiveFunction(ExpressionsBasedModel model,
                                      Map<String, Variable> quantityVars) {
        ExpressionsBasedModel.Expression costExpr =
                model.addExpression("total_cost");

        for (int day = 1; day <= DAYS; day++) {
            for (String mealType : MEAL_TYPES) {
                for (FoodItem food : foodItems) {
                    String qKey = buildQuantityKey(day, mealType, food.getId());
                    Variable qVar = quantityVars.get(qKey);
                    BigDecimal costPerGram = safeDivide(food.getCostPerKg(), 1000);
                    costExpr.set(qVar, costPerGram);
                }
            }
        }

        costExpr.weight(BigDecimal.ONE);
    }

    private MealPlanResult extractResult(Optimisation.Result result,
                                         Map<String, Variable> quantityVars,
                                         Map<String, Variable> selectionVars,
                                         String solverStatus) {
        MealPlanResult planResult = new MealPlanResult();
        planResult.setSolverStatus(solverStatus);
        planResult.setFeasible(true);

        BigDecimal totalCost = BigDecimal.ZERO;
        Map<Integer, DailyNutrition> dailyNutritionMap = new HashMap<>();
        List<MealItemDetail> allItems = new ArrayList<>();

        for (int day = 1; day <= DAYS; day++) {
            dailyNutritionMap.put(day, new DailyNutrition());
        }

        for (int day = 1; day <= DAYS; day++) {
            DailyNutrition dailyNutrition = dailyNutritionMap.get(day);

            for (String mealType : MEAL_TYPES) {
                for (FoodItem food : foodItems) {
                    String qKey = buildQuantityKey(day, mealType, food.getId());
                    Variable qVar = quantityVars.get(qKey);
                    double quantity = result.doubleValue(qVar.getIndex());

                    if (quantity > 1.0) {
                        BigDecimal quantityBd = BigDecimal.valueOf(quantity)
                                .setScale(2, RoundingMode.HALF_UP);

                        BigDecimal protein = quantityBd.multiply(safeDivide(food.getProteinPer100g(), 100))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal fat = quantityBd.multiply(safeDivide(food.getFatPer100g(), 100))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal vitaminC = quantityBd.multiply(safeDivide(food.getVitaminCPer100gMg(), 100))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal calorie = quantityBd.multiply(safeDivide(food.getCaloriePer100gKcal(), 100))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal cost = quantityBd.multiply(safeDivide(food.getCostPerKg(), 1000))
                                .setScale(2, RoundingMode.HALF_UP);

                        dailyNutrition.setProtein(dailyNutrition.getProtein().add(protein));
                        dailyNutrition.setFat(dailyNutrition.getFat().add(fat));
                        dailyNutrition.setVitaminC(dailyNutrition.getVitaminC().add(vitaminC));
                        dailyNutrition.setCalorie(dailyNutrition.getCalorie().add(calorie));

                        totalCost = totalCost.add(cost);

                        MealItemDetail detail = new MealItemDetail();
                        detail.setDayOfWeek(day);
                        detail.setMealType(mealType);
                        detail.setFoodItemId(food.getId());
                        detail.setFoodName(food.getName());
                        detail.setQuantityGrams(quantityBd);
                        detail.setProteinG(protein);
                        detail.setFatG(fat);
                        detail.setVitaminCMg(vitaminC);
                        detail.setCalorieKcal(calorie);
                        detail.setCost(cost);
                        allItems.add(detail);
                    }
                }
            }
        }

        planResult.setTotalCost(totalCost.multiply(BigDecimal.valueOf(soldierCount))
                .setScale(2, RoundingMode.HALF_UP));
        planResult.setDailyNutritionMap(dailyNutritionMap);
        planResult.setMealItems(allItems);

        BigDecimal avgProtein = BigDecimal.ZERO;
        BigDecimal avgFat = BigDecimal.ZERO;
        BigDecimal avgVitaminC = BigDecimal.ZERO;
        BigDecimal avgCalorie = BigDecimal.ZERO;
        for (DailyNutrition dn : dailyNutritionMap.values()) {
            avgProtein = avgProtein.add(dn.getProtein());
            avgFat = avgFat.add(dn.getFat());
            avgVitaminC = avgVitaminC.add(dn.getVitaminC());
            avgCalorie = avgCalorie.add(dn.getCalorie());
        }
        planResult.setAvgDailyProtein(avgProtein.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));
        planResult.setAvgDailyFat(avgFat.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));
        planResult.setAvgDailyVitaminC(avgVitaminC.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));
        planResult.setAvgDailyCalorie(avgCalorie.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));

        return planResult;
    }

    private MealPlanResult buildDefaultPlan() {
        MealPlanResult planResult = new MealPlanResult();
        planResult.setSolverStatus("FALLBACK_DEFAULT");
        planResult.setFeasible(false);

        BigDecimal totalCost = BigDecimal.ZERO;
        Map<Integer, DailyNutrition> dailyNutritionMap = new HashMap<>();
        List<MealItemDetail> allItems = new ArrayList<>();

        List<FoodItem> availableFoods = foodItems != null ?
                foodItems.stream().filter(f -> Boolean.TRUE.equals(f.getIsAvailable())).toList() :
                new ArrayList<>();

        if (availableFoods.isEmpty()) {
            availableFoods = foodItems != null ? foodItems : new ArrayList<>();
        }

        String[] defaultFoodNames = {"粟米", "小麦", "牛肉", "羊肉", "葵菜", "韭菜", "大豆", "鸡蛋"};
        List<FoodItem> selectedFoods = new ArrayList<>();

        for (String name : defaultFoodNames) {
            Optional<FoodItem> match = availableFoods.stream()
                    .filter(f -> f.getName() != null && f.getName().contains(name))
                    .findFirst();
            if (match.isPresent()) {
                selectedFoods.add(match.get());
            }
        }

        if (selectedFoods.isEmpty()) {
            for (int i = 0; i < Math.min(8, availableFoods.size()); i++) {
                selectedFoods.add(availableFoods.get(i));
            }
        }
        if (selectedFoods.isEmpty()) {
            selectedFoods = buildDummyFoodItems();
        }

        for (int day = 1; day <= DAYS; day++) {
            dailyNutritionMap.put(day, new DailyNutrition());
            DailyNutrition dn = dailyNutritionMap.get(day);

            for (int mealIdx = 0; mealIdx < MEAL_TYPES.length; mealIdx++) {
                String mealType = MEAL_TYPES[mealIdx];
                int startIdx = (mealIdx * 3 + day) % selectedFoods.size();
                int itemsInMeal = 0;

                for (int offset = 0; offset < selectedFoods.size() && itemsInMeal < 3; offset++) {
                    int foodIdx = (startIdx + offset) % selectedFoods.size();
                    FoodItem food = selectedFoods.get(foodIdx);

                    double baseQuantity = 150.0 + (day + mealIdx + foodIdx) % 3 * 50.0;
                    BigDecimal quantityBd = BigDecimal.valueOf(baseQuantity)
                            .setScale(2, RoundingMode.HALF_UP);

                    BigDecimal protein = quantityBd.multiply(safeDivide(food.getProteinPer100g(), 100))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal fat = quantityBd.multiply(safeDivide(food.getFatPer100g(), 100))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal vitaminC = quantityBd.multiply(safeDivide(food.getVitaminCPer100gMg(), 100))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal calorie = quantityBd.multiply(safeDivide(food.getCaloriePer100gKcal(), 100))
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal cost = quantityBd.multiply(safeDivide(food.getCostPerKg(), 1000))
                            .setScale(2, RoundingMode.HALF_UP);

                    dn.setProtein(dn.getProtein().add(protein));
                    dn.setFat(dn.getFat().add(fat));
                    dn.setVitaminC(dn.getVitaminC().add(vitaminC));
                    dn.setCalorie(dn.getCalorie().add(calorie));
                    totalCost = totalCost.add(cost);

                    MealItemDetail detail = new MealItemDetail();
                    detail.setDayOfWeek(day);
                    detail.setMealType(mealType);
                    detail.setFoodItemId(food.getId());
                    detail.setFoodName(food.getName());
                    detail.setQuantityGrams(quantityBd);
                    detail.setProteinG(protein);
                    detail.setFatG(fat);
                    detail.setVitaminCMg(vitaminC);
                    detail.setCalorieKcal(calorie);
                    detail.setCost(cost);
                    allItems.add(detail);
                    itemsInMeal++;
                }
            }
        }

        planResult.setTotalCost(totalCost.multiply(BigDecimal.valueOf(soldierCount))
                .setScale(2, RoundingMode.HALF_UP));
        planResult.setDailyNutritionMap(dailyNutritionMap);
        planResult.setMealItems(allItems);

        BigDecimal avgProtein = BigDecimal.ZERO;
        BigDecimal avgFat = BigDecimal.ZERO;
        BigDecimal avgVitaminC = BigDecimal.ZERO;
        BigDecimal avgCalorie = BigDecimal.ZERO;
        for (DailyNutrition dn : dailyNutritionMap.values()) {
            avgProtein = avgProtein.add(dn.getProtein());
            avgFat = avgFat.add(dn.getFat());
            avgVitaminC = avgVitaminC.add(dn.getVitaminC());
            avgCalorie = avgCalorie.add(dn.getCalorie());
        }
        planResult.setAvgDailyProtein(avgProtein.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));
        planResult.setAvgDailyFat(avgFat.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));
        planResult.setAvgDailyVitaminC(avgVitaminC.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));
        planResult.setAvgDailyCalorie(avgCalorie.divide(BigDecimal.valueOf(DAYS), 2, RoundingMode.HALF_UP));

        return planResult;
    }

    private List<FoodItem> buildDummyFoodItems() {
        List<FoodItem> dummies = new ArrayList<>();
        double[][] nutritionData = {
                {11.2, 1.5, 0, 346, 4.5},
                {10.3, 1.1, 0, 317, 3.8},
                {20.2, 2.3, 0, 125, 60.0},
                {28.8, 4.2, 0, 158, 70.0},
                {1.8, 0.4, 27, 30, 8.0},
                {2.4, 0.4, 24, 26, 12.0},
                {35.0, 16.0, 0, 359, 6.5},
                {13.3, 8.8, 0, 144, 15.0}
        };
        String[] names = {"粟米", "小麦", "牛肉", "羊肉", "葵菜", "韭菜", "大豆", "鸡蛋"};

        for (int i = 0; i < names.length; i++) {
            FoodItem f = new FoodItem();
            f.setId((long) (i + 1000));
            f.setName(names[i]);
            f.setProteinPer100g(BigDecimal.valueOf(nutritionData[i][0]));
            f.setFatPer100g(BigDecimal.valueOf(nutritionData[i][1]));
            f.setVitaminCPer100gMg(BigDecimal.valueOf(nutritionData[i][2]));
            f.setCaloriePer100gKcal(BigDecimal.valueOf(nutritionData[i][3]));
            f.setCostPerKg(BigDecimal.valueOf(nutritionData[i][4]));
            f.setIsAvailable(true);
            dummies.add(f);
        }
        return dummies;
    }

    private static String buildQuantityKey(int day, String mealType, Long foodId) {
        return "q_d" + day + "_" + mealType + "_f" + foodId;
    }

    private static String buildSelectionKey(int day, String mealType, Long foodId) {
        return "s_d" + day + "_" + mealType + "_f" + foodId;
    }

    private static BigDecimal safeDivide(BigDecimal dividend, int divisor) {
        if (dividend == null) return BigDecimal.ZERO;
        return dividend.divide(BigDecimal.valueOf(divisor), 6, RoundingMode.HALF_UP);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealPlanResult {
        private boolean feasible;
        private String solverStatus;
        private BigDecimal totalCost;
        private BigDecimal avgDailyProtein;
        private BigDecimal avgDailyFat;
        private BigDecimal avgDailyVitaminC;
        private BigDecimal avgDailyCalorie;
        private Map<Integer, DailyNutrition> dailyNutritionMap;
        private List<MealItemDetail> mealItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyNutrition {
        @Builder.Default
        private BigDecimal protein = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal fat = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal vitaminC = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal calorie = BigDecimal.ZERO;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealItemDetail {
        private Integer dayOfWeek;
        private String mealType;
        private Long foodItemId;
        private String foodName;
        private BigDecimal quantityGrams;
        private BigDecimal proteinG;
        private BigDecimal fatG;
        private BigDecimal vitaminCMg;
        private BigDecimal calorieKcal;
        private BigDecimal cost;
    }
}
