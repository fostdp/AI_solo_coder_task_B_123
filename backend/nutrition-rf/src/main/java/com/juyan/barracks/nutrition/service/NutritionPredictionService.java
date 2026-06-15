package com.juyan.barracks.nutrition.service;

import com.juyan.barracks.common.dto.NutritionFeature;
import com.juyan.barracks.common.entity.MealRecord;
import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.PhysicalActivity;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.event.NutritionRiskComputedEvent;
import com.juyan.barracks.common.repository.MealRecordRepository;
import com.juyan.barracks.common.repository.NutritionRiskRepository;
import com.juyan.barracks.common.repository.PhysicalActivityRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import com.juyan.barracks.nutrition.algorithm.RandomForestNutritionPredictor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionPredictionService {

    private final SoldierRepository soldierRepository;
    private final MealRecordRepository mealRecordRepository;
    private final PhysicalActivityRepository physicalActivityRepository;
    private final NutritionRiskRepository nutritionRiskRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${algorithm.nutrition.random-forest.num-trees:100}")
    private int numTrees;

    @Value("${algorithm.nutrition.random-forest.max-depth:10}")
    private int maxDepth;

    @Value("${algorithm.nutrition.vitamin-c-threshold:60.0}")
    private double vitaminCThreshold;

    @Value("${algorithm.nutrition.protein-threshold:50.0}")
    private double proteinThreshold;

    @Value("${algorithm.nutrition.fat-threshold:40.0}")
    private double fatThreshold;

    private RandomForestNutritionPredictor proteinPredictor;
    private RandomForestNutritionPredictor fatPredictor;
    private RandomForestNutritionPredictor vitaminCPredictor;

    @PostConstruct
    public void init() {
        log.info("初始化随机森林营养预测模型...");
        proteinPredictor = new RandomForestNutritionPredictor(numTrees, maxDepth);
        fatPredictor = new RandomForestNutritionPredictor(numTrees, maxDepth);
        vitaminCPredictor = new RandomForestNutritionPredictor(numTrees, maxDepth);

        trainModels();
        log.info("随机森林模型初始化完成");
    }

    private void trainModels() {
        List<double[]> trainingFeatures = generateTrainingFeatures();
        List<Integer> proteinLabels = new ArrayList<>();
        List<Integer> fatLabels = new ArrayList<>();
        List<Integer> vitaminCLabels = new ArrayList<>();

        Random random = new Random(42);
        for (double[] feature : trainingFeatures) {
            double protein = feature[0];
            double fat = feature[1];
            double vitaminC = feature[2];
            double calorieBurned = feature[4];
            double avgProtein = feature[7];
            double avgFat = feature[8];
            double avgVitaminC = feature[9];

            double adjustedProtein = protein * (1 + calorieBurned / 2000);
            double adjustedFat = fat * (1 + calorieBurned / 3000);
            double adjustedVitaminC = (vitaminC + avgVitaminC / 2) * (1 + calorieBurned / 2500);

            proteinLabels.add(adjustedProtein < proteinThreshold ? 1 : 0);
            fatLabels.add(adjustedFat < fatThreshold ? 1 : 0);
            vitaminCLabels.add(adjustedVitaminC < vitaminCThreshold ? 1 : 0);
        }

        proteinPredictor.train(trainingFeatures, proteinLabels);
        fatPredictor.train(trainingFeatures, fatLabels);
        vitaminCPredictor.train(trainingFeatures, vitaminCLabels);

        log.info("模型训练完成: 训练样本数={}", trainingFeatures.size());
    }

    private List<double[]> generateTrainingFeatures() {
        List<double[]> features = new ArrayList<>();
        Random random = new Random(42);
        String[] origins = {"NORTH", "SOUTH", "WEST"};

        for (int i = 0; i < 500; i++) {
            double protein = 20 + random.nextDouble() * 80;
            double fat = 10 + random.nextDouble() * 60;
            double vitaminC = 10 + random.nextDouble() * 150;
            double calorie = 800 + random.nextDouble() * 2000;
            double calorieBurned = 200 + random.nextDouble() * 800;
            int age = 18 + random.nextInt(30);
            int activityDays = 3 + random.nextInt(5);
            double avgProtein7d = 20 + random.nextDouble() * 80;
            double avgFat7d = 10 + random.nextDouble() * 60;
            double avgVitaminC7d = 10 + random.nextDouble() * 150;

            int ageGroupYoung = (age >= 18 && age <= 25) ? 1 : 0;
            int ageGroupMiddle = (age >= 26 && age <= 35) ? 1 : 0;
            int ageGroupSenior = (age >= 36) ? 1 : 0;

            String origin = origins[random.nextInt(origins.length)];
            int originNorth = "NORTH".equals(origin) ? 1 : 0;
            int originSouth = "SOUTH".equals(origin) ? 1 : 0;
            int originWest = "WEST".equals(origin) ? 1 : 0;

            if (originNorth == 1) {
                protein *= 1.15;
                fat *= 1.2;
                vitaminC *= 0.75;
            } else if (originSouth == 1) {
                protein *= 0.9;
                fat *= 0.85;
                vitaminC *= 1.25;
            } else {
                protein *= 0.95;
                fat *= 1.0;
                vitaminC *= 0.9;
            }

            if (ageGroupYoung == 1) {
                protein *= 1.1;
                calorieBurned *= 1.15;
            } else if (ageGroupSenior == 1) {
                protein *= 0.85;
                calorieBurned *= 0.8;
            }

            features.add(new double[]{
                    protein, fat, vitaminC, calorie,
                    calorieBurned, age, activityDays,
                    avgProtein7d, avgFat7d, avgVitaminC7d,
                    ageGroupYoung, ageGroupMiddle, ageGroupSenior,
                    originNorth, originSouth, originWest
            });
        }

        return features;
    }

    @Scheduled(cron = "${scheduling.nutrition-prediction.cron:0 0 */2 * * ?}")
    @Transactional
    public void runNutritionPrediction() {
        log.info("开始执行营养缺乏预测任务...");
        List<Soldier> soldiers = soldierRepository.findAll();
        int highRiskCount = 0;

        for (Soldier soldier : soldiers) {
            try {
                NutritionRisk risk = predictSoldierNutritionRisk(soldier);
                if (risk != null) {
                    nutritionRiskRepository.markOldAsNotCurrent(soldier.getId());
                    nutritionRiskRepository.save(risk);

                    if ("HIGH".equals(risk.getRiskLevel()) || "CRITICAL".equals(risk.getRiskLevel())) {
                        highRiskCount++;
                        eventPublisher.publishEvent(new NutritionRiskComputedEvent(this, soldier, risk));
                    }
                }
            } catch (Exception e) {
                log.error("预测士兵 {} 营养风险失败: {}", soldier.getId(), e.getMessage(), e);
            }
        }

        log.info("营养缺乏预测任务完成: 处理士兵数={}, 高风险告警数={}", soldiers.size(), highRiskCount);
    }

    public NutritionRisk predictSoldierNutritionRisk(Soldier soldier) {
        NutritionFeature feature = extractFeatures(soldier);
        if (feature == null) {
            return null;
        }

        double[] featureVector = toFeatureVector(feature);

        double proteinRisk = proteinPredictor.predictProbability(featureVector);
        double fatRisk = fatPredictor.predictProbability(featureVector);
        double vitaminCRisk = vitaminCPredictor.predictProbability(featureVector);

        BigDecimal vitaminCScore = BigDecimal.valueOf(vitaminCRisk)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal proteinScore = BigDecimal.valueOf(proteinRisk)
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal fatScore = BigDecimal.valueOf(fatRisk)
                .setScale(4, RoundingMode.HALF_UP);

        double overall = (proteinRisk * 0.3 + fatRisk * 0.2 + vitaminCRisk * 0.5);
        BigDecimal overallScore = BigDecimal.valueOf(overall).setScale(4, RoundingMode.HALF_UP);

        String riskLevel;
        if (overall > 0.7 || vitaminCRisk > 0.8) {
            riskLevel = "CRITICAL";
        } else if (overall > 0.5 || vitaminCRisk > 0.6) {
            riskLevel = "HIGH";
        } else if (overall > 0.3) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        String suggestion = generateDietarySuggestion(feature, proteinRisk, fatRisk, vitaminCRisk);

        NutritionRisk risk = new NutritionRisk();
        risk.setSoldierId(soldier.getId());
        risk.setRiskLevel(riskLevel);
        risk.setProteinRiskScore(proteinScore);
        risk.setFatRiskScore(fatScore);
        risk.setVitaminCRiskScore(vitaminCScore);
        risk.setOverallRiskScore(overallScore);
        risk.setDietarySuggestion(suggestion);
        risk.setIsCurrent(true);

        return risk;
    }

    public NutritionFeature extractFeatures(Soldier soldier) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.atTime(23, 59, 59);
        LocalDate sevenDaysAgo = today.minusDays(7);

        BigDecimal proteinG = mealRecordRepository.sumProteinBySoldierAndTime(soldier.getId(), startOfToday, endOfToday);
        BigDecimal fatG = mealRecordRepository.sumFatBySoldierAndTime(soldier.getId(), startOfToday, endOfToday);
        BigDecimal vitaminCMg = mealRecordRepository.sumVitaminCBySoldierAndTime(soldier.getId(), startOfToday, endOfToday);
        BigDecimal calorieKcal = mealRecordRepository.sumCalorieBySoldierAndTime(soldier.getId(), startOfToday, endOfToday);

        BigDecimal avgProtein7d = BigDecimal.ZERO;
        BigDecimal avgFat7d = BigDecimal.ZERO;
        BigDecimal avgVitaminC7d = BigDecimal.ZERO;

        LocalDateTime sevenDaysAgoStart = sevenDaysAgo.atStartOfDay();
        List<MealRecord> last7DaysMeals = mealRecordRepository.findBySoldierIdAndMealTimeBetween(
                soldier.getId(), sevenDaysAgoStart, endOfToday);

        if (!last7DaysMeals.isEmpty()) {
            for (MealRecord meal : last7DaysMeals) {
                avgProtein7d = avgProtein7d.add(meal.getProteinG());
                avgFat7d = avgFat7d.add(meal.getFatG());
                avgVitaminC7d = avgVitaminC7d.add(meal.getVitaminCMg());
            }
            avgProtein7d = avgProtein7d.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            avgFat7d = avgFat7d.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
            avgVitaminC7d = avgVitaminC7d.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
        }

        BigDecimal calorieBurned = physicalActivityRepository.sumCalorieBurnedBySoldierAndDate(
                soldier.getId(), sevenDaysAgo, today);

        List<PhysicalActivity> activities = physicalActivityRepository.findBySoldierIdAndActivityDateBetween(
                soldier.getId(), sevenDaysAgo, today);

        int age = soldier.getAge() != null ? soldier.getAge() : 25;
        int ageGroupYoung = (age >= 18 && age <= 25) ? 1 : 0;
        int ageGroupMiddle = (age >= 26 && age <= 35) ? 1 : 0;
        int ageGroupSenior = (age >= 36) ? 1 : 0;

        String origin = soldier.getOriginRegion() != null ? soldier.getOriginRegion() : "NORTH";
        int originNorth = "NORTH".equals(origin) ? 1 : 0;
        int originSouth = "SOUTH".equals(origin) ? 1 : 0;
        int originWest = "WEST".equals(origin) ? 1 : 0;

        return NutritionFeature.builder()
                .dailyProteinG(proteinG != null ? proteinG : BigDecimal.ZERO)
                .dailyFatG(fatG != null ? fatG : BigDecimal.ZERO)
                .dailyVitaminCMg(vitaminCMg != null ? vitaminCMg : BigDecimal.ZERO)
                .dailyCalorieKcal(calorieKcal != null ? calorieKcal : BigDecimal.ZERO)
                .calorieBurned(calorieBurned != null ? calorieBurned : BigDecimal.ZERO)
                .age(soldier.getAge())
                .activityDays(activities.size())
                .avgProtein7d(avgProtein7d)
                .avgFat7d(avgFat7d)
                .avgVitaminC7d(avgVitaminC7d)
                .ageGroupYoung(ageGroupYoung)
                .ageGroupMiddle(ageGroupMiddle)
                .ageGroupSenior(ageGroupSenior)
                .originNorth(originNorth)
                .originSouth(originSouth)
                .originWest(originWest)
                .build();
    }

    private double[] toFeatureVector(NutritionFeature feature) {
        return new double[]{
                feature.getDailyProteinG().doubleValue(),
                feature.getDailyFatG().doubleValue(),
                feature.getDailyVitaminCMg().doubleValue(),
                feature.getDailyCalorieKcal().doubleValue(),
                feature.getCalorieBurned().doubleValue(),
                feature.getAge(),
                feature.getActivityDays(),
                feature.getAvgProtein7d().doubleValue(),
                feature.getAvgFat7d().doubleValue(),
                feature.getAvgVitaminC7d().doubleValue(),
                feature.getAgeGroupYoung(),
                feature.getAgeGroupMiddle(),
                feature.getAgeGroupSenior(),
                feature.getOriginNorth(),
                feature.getOriginSouth(),
                feature.getOriginWest()
        };
    }

    public String generateDietarySuggestion(NutritionFeature feature, double proteinRisk,
                                             double fatRisk, double vitaminCRisk) {
        StringBuilder suggestion = new StringBuilder();

        if (feature.getAgeGroupYoung() == 1) {
            suggestion.append("【青年士兵（18-25岁）】代谢旺盛，营养需求偏高。\n");
        } else if (feature.getAgeGroupMiddle() == 1) {
            suggestion.append("【壮年士兵（26-35岁）】需维持体能储备。\n");
        } else if (feature.getAgeGroupSenior() == 1) {
            suggestion.append("【年长士兵（36岁以上）】消化吸收能力下降，需注意营养密度。\n");
        }

        if (feature.getOriginNorth() == 1) {
            suggestion.append("【北方籍贯】膳食偏重面食肉食，蔬菜摄入偏少。\n");
        } else if (feature.getOriginSouth() == 1) {
            suggestion.append("【南方籍贯】膳食偏清淡，蛋白质摄入可能不足。\n");
        } else if (feature.getOriginWest() == 1) {
            suggestion.append("【西部籍贯】膳食偏杂粮，维生素多样性需关注。\n");
        }
        suggestion.append("\n");

        if (vitaminCRisk > 0.6) {
            suggestion.append("维生素C摄入严重不足（当前").append(feature.getDailyVitaminCMg().intValue())
                    .append("mg/天，建议≥60mg），建议增加以下食物：\n");
            if (feature.getOriginNorth() == 1) {
                suggestion.append("  - 北方士兵尤其需补充：酸枣、梨、萝卜叶等\n");
            }
            suggestion.append("  - 汉代已有：新鲜葵菜、韭菜、葱、蒜等蔬菜\n")
                    .append("  - 建议补充：酸枣、梨、桃等时令水果\n")
                    .append("  - 注意：维生素C不耐热，蔬菜不宜过度烹煮。\n\n");
        } else if (vitaminCRisk > 0.3) {
            suggestion.append("维生素C摄入偏低（当前").append(feature.getDailyVitaminCMg().intValue())
                    .append("mg/天），建议多食用新鲜蔬菜。\n\n");
        }

        if (proteinRisk > 0.5) {
            suggestion.append("蛋白质摄入不足（当前").append(feature.getDailyProteinG().intValue())
                    .append("g/天），建议增加：\n");
            if (feature.getOriginSouth() == 1) {
                suggestion.append("  - 南方籍士兵推荐：鱼类、豆制品\n");
            }
            if (feature.getAgeGroupSenior() == 1) {
                suggestion.append("  - 年长士兵推荐：炖煮肉类、豆腐等易消化蛋白\n");
            }
            suggestion.append("  - 肉类：牛肉、羊肉、猪肉等\n")
                    .append("  - 豆类：大豆、小豆等豆制品\n")
                    .append("  - 蛋类：鸡蛋、鸭蛋\n\n");
        }

        if (fatRisk > 0.4) {
            suggestion.append("脂肪摄入偏低（当前").append(feature.getDailyFatG().intValue())
                    .append("g/天），可适当增加动物脂肪和植物油摄入。\n\n");
        }

        if (suggestion.length() == 0) {
            suggestion.append("当前膳食结构基本合理，请继续保持均衡饮食。建议注意荤素搭配，保证充足水分摄入。");
        }

        suggestion.append("每日建议摄入量参考：蛋白质50-70g，脂肪40-60g，维生素C≥60mg，热量2500-3500kcal（根据体能消耗调整）。");

        return suggestion.toString();
    }

    public Optional<NutritionRisk> getCurrentRiskForSoldier(Long soldierId) {
        return nutritionRiskRepository.findBySoldierIdAndIsCurrentTrue(soldierId);
    }

    public List<NutritionRisk> getAllCurrentRisks() {
        return nutritionRiskRepository.findAllCurrent();
    }
}
