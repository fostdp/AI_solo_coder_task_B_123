package com.juyan.barracks.intervention.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juyan.barracks.common.entity.InterventionRecommendation;
import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.InterventionRecommendationRepository;
import com.juyan.barracks.common.repository.NutritionRiskRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import com.juyan.barracks.intervention.algorithm.NutritionInterventionTree;
import com.juyan.barracks.intervention.algorithm.SupplementCatalog;
import com.juyan.barracks.intervention.config.InterventionConfig;
import com.juyan.barracks.intervention.dto.RecommendationResultDTO;
import com.juyan.barracks.intervention.dto.TreeTrainResultDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionInterventionService {

    private final SoldierRepository soldierRepository;
    private final NutritionRiskRepository nutritionRiskRepository;
    private final InterventionRecommendationRepository recommendationRepository;
    private final InterventionConfig config;
    private final ObjectMapper objectMapper;

    private NutritionInterventionTree decisionTree;

    @PostConstruct
    public void init() {
        log.info("初始化营养干预决策树服务");
        this.decisionTree = new NutritionInterventionTree(
                config.getTreeMaxDepth(),
                config.getTreeMinSamplesSplit(),
                NutritionInterventionTree.SplitCriterion.GINI
        );
        log.info("尝试从历史数据训练决策树...");
        try {
            TreeTrainResultDTO result = trainTreeFromHistory();
            log.info("决策树初始化训练完成: success={}, samples={}, accuracy={}, fallback={}",
                    result.isSuccess(), result.getTrainingSamples(),
                    result.getCrossValidationAccuracy(), result.isUsedBuiltinFallback());
        } catch (Exception e) {
            log.warn("决策树初始训练失败，将使用内置规则: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${intervention.retrain-cron:0 0 3 * * ?}")
    public void scheduledRetrain() {
        log.info("执行定时任务：重新训练决策树");
        try {
            TreeTrainResultDTO result = trainTreeFromHistory();
            log.info("定时重训练完成: success={}, samples={}, accuracy={}",
                    result.isSuccess(), result.getTrainingSamples(), result.getCrossValidationAccuracy());
        } catch (Exception e) {
            log.error("定时重训练决策树失败", e);
        }
    }

    public TreeTrainResultDTO trainTreeFromHistory() {
        long startTime = System.currentTimeMillis();
        TreeTrainResultDTO result = new TreeTrainResultDTO();
        result.setTrainedAt(LocalDateTime.now());
        result.setSplitCriterion("GINI");
        result.setMaxDepth(config.getTreeMaxDepth());

        try {
            List<NutritionRisk> allRisks = nutritionRiskRepository.findAll();
            List<InterventionRecommendation> allRecommendations = recommendationRepository.findAll();

            log.info("收集训练数据: NutritionRisk={}, InterventionRecommendation={}",
                    allRisks.size(), allRecommendations.size());

            Map<Long, Soldier> soldierMap = soldierRepository.findAll().stream()
                    .collect(Collectors.toMap(Soldier::getId, s -> s));

            Map<Long, NutritionRisk> riskMap = new HashMap<>();
            for (NutritionRisk risk : allRisks) {
                riskMap.put(risk.getSoldierId(), risk);
            }

            List<NutritionInterventionTree.TrainingSample> trainingSamples = new ArrayList<>();

            for (InterventionRecommendation rec : allRecommendations) {
                if (rec.getSoldierId() == null) continue;

                Soldier soldier = soldierMap.get(rec.getSoldierId());
                NutritionRisk risk = riskMap.get(rec.getSoldierId());

                if (soldier == null || risk == null) continue;

                double[] features = extractFeatures(soldier, risk);
                List<SupplementCatalog.SupplementType> labels = parseSupplementLabels(rec.getRecommendedSupplements());

                if (labels.isEmpty()) continue;

                trainingSamples.add(new NutritionInterventionTree.TrainingSample(features, labels));
            }

            result.setTrainingSamples(trainingSamples.size());

            if (trainingSamples.size() >= config.getMinHistorySamplesForTraining()) {
                log.info("使用 {} 条样本训练决策树（阈值: {}）",
                        trainingSamples.size(), config.getMinHistorySamplesForTraining());

                this.decisionTree = new NutritionInterventionTree(
                        config.getTreeMaxDepth(),
                        config.getTreeMinSamplesSplit(),
                        NutritionInterventionTree.SplitCriterion.GINI
                );

                decisionTree.train(trainingSamples);

                double cvAccuracy = decisionTree.crossValidate(trainingSamples, config.getCrossValidationFolds());
                result.setCrossValidationAccuracy(cvAccuracy);
                result.setRuleCount(decisionTree.getRuleDescriptions().size());
                result.setSuccess(true);
                result.setUsedBuiltinFallback(false);
                result.setMessage(String.format("训练成功：%d条样本，交叉验证准确率%.2f%%，规则数%d",
                        trainingSamples.size(), cvAccuracy * 100, result.getRuleCount()));

                log.info("决策树训练完成: 规则数={}, CV准确率={}", result.getRuleCount(), cvAccuracy);
            } else {
                log.warn("历史数据不足（{} < {}），使用内置规则",
                        trainingSamples.size(), config.getMinHistorySamplesForTraining());
                result.setSuccess(false);
                result.setUsedBuiltinFallback(true);
                result.setMessage(String.format("历史数据不足（%d条，需%d条），已使用内置规则作为兜底",
                        trainingSamples.size(), config.getMinHistorySamplesForTraining()));
                this.decisionTree = new NutritionInterventionTree(
                        config.getTreeMaxDepth(),
                        config.getTreeMinSamplesSplit(),
                        NutritionInterventionTree.SplitCriterion.GINI
                );
            }
        } catch (Exception e) {
            log.error("训练决策树失败", e);
            result.setSuccess(false);
            result.setUsedBuiltinFallback(true);
            result.setMessage("训练失败: " + e.getMessage());
        }

        result.setTrainingTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    @Transactional
    public RecommendationResultDTO generateRecommendation(Long soldierId) {
        log.info("为士兵生成营养干预推荐: soldierId={}", soldierId);

        Optional<Soldier> soldierOpt = soldierRepository.findById(soldierId);
        if (soldierOpt.isEmpty()) {
            throw new IllegalArgumentException("士兵不存在: " + soldierId);
        }

        Soldier soldier = soldierOpt.get();
        Optional<NutritionRisk> riskOpt = nutritionRiskRepository.findBySoldierIdAndIsCurrentTrue(soldierId);
        if (riskOpt.isEmpty()) {
            throw new IllegalStateException("士兵无当前营养风险数据: " + soldierId);
        }

        NutritionRisk risk = riskOpt.get();
        NutritionInterventionTree.SoldierFeatures features = buildSoldierFeatures(soldier, risk);

        List<NutritionInterventionTree.Intervention> interventions =
                decisionTree.predict(features, config.getDefaultDurationDays());

        RecommendationResultDTO result = buildResultDTO(soldier, risk, features, interventions);
        saveRecommendation(soldier, risk, result);

        log.info("推荐生成完成: soldierId={}, supplements={}, totalCost={}",
                soldierId, result.getSupplements().size(), result.getTotalEstimatedCost());

        return result;
    }

    @Transactional
    public List<RecommendationResultDTO> generateForHighRisk(Long barracksId, Double riskThreshold) {
        double threshold = riskThreshold != null ? riskThreshold : config.getHighRiskThreshold();
        log.info("批量为高风险士兵生成推荐: barracksId={}, threshold={}", barracksId, threshold);

        List<Soldier> soldiers;
        if (barracksId != null) {
            soldiers = soldierRepository.findByBarracksId(barracksId);
        } else {
            soldiers = soldierRepository.findAll();
        }

        List<NutritionRisk> currentRisks = nutritionRiskRepository.findAllCurrent();
        Map<Long, NutritionRisk> riskMap = currentRisks.stream()
                .collect(Collectors.toMap(NutritionRisk::getSoldierId, r -> r));

        List<RecommendationResultDTO> results = new ArrayList<>();
        int skipped = 0;

        for (Soldier soldier : soldiers) {
            NutritionRisk risk = riskMap.get(soldier.getId());
            if (risk == null) {
                skipped++;
                continue;
            }

            if (risk.getOverallRiskScore().doubleValue() < threshold) {
                continue;
            }

            try {
                NutritionInterventionTree.SoldierFeatures features = buildSoldierFeatures(soldier, risk);
                List<NutritionInterventionTree.Intervention> interventions =
                        decisionTree.predict(features, config.getDefaultDurationDays());

                RecommendationResultDTO result = buildResultDTO(soldier, risk, features, interventions);
                saveRecommendation(soldier, risk, result);
                results.add(result);
            } catch (Exception e) {
                log.warn("为士兵 {} 生成推荐失败: {}", soldier.getId(), e.getMessage());
            }
        }

        log.info("批量推荐完成: 处理士兵={}, 生成推荐={}, 跳过无数据={}", soldiers.size(), results.size(), skipped);
        return results;
    }

    public RecommendationResultDTO generateForSoldierAndRisk(Soldier soldier, NutritionRisk risk) {
        log.info("事件触发: 为士兵生成推荐 soldierId={}, risk={}", soldier.getId(), risk.getRiskLevel());

        NutritionInterventionTree.SoldierFeatures features = buildSoldierFeatures(soldier, risk);
        List<NutritionInterventionTree.Intervention> interventions =
                decisionTree.predict(features, config.getDefaultDurationDays());

        RecommendationResultDTO result = buildResultDTO(soldier, risk, features, interventions);
        saveRecommendation(soldier, risk, result);

        return result;
    }

    public List<String> getDecisionTreeRules() {
        return decisionTree.getRuleDescriptions();
    }

    public boolean isTreeTrained() {
        return decisionTree.isTrained();
    }

    private double[] extractFeatures(Soldier soldier, NutritionRisk risk) {
        NutritionInterventionTree.SoldierFeatures features = buildSoldierFeatures(soldier, risk);
        return features.toFeatureArray();
    }

    private NutritionInterventionTree.SoldierFeatures buildSoldierFeatures(Soldier soldier, NutritionRisk risk) {
        NutritionInterventionTree.SoldierFeatures features = new NutritionInterventionTree.SoldierFeatures();

        features.setRiskLevel(riskLevelToDouble(risk.getRiskLevel()));
        features.setAge(soldier.getAge() != null ? soldier.getAge() : 25);
        features.setOriginRegion(soldier.getOriginRegion());
        features.setOriginRegionEncoded(encodeOriginRegion(soldier.getOriginRegion()));
        features.setProteinRisk(risk.getProteinRiskScore() != null ? risk.getProteinRiskScore().doubleValue() : 0.0);
        features.setVitaminCRisk(risk.getVitaminCRiskScore() != null ? risk.getVitaminCRiskScore().doubleValue() : 0.0);
        features.setFatRisk(risk.getFatRiskScore() != null ? risk.getFatRiskScore().doubleValue() : 0.0);
        features.setActivityLevel(estimateActivityLevel(soldier));

        return features;
    }

    private double riskLevelToDouble(String riskLevel) {
        if (riskLevel == null) return 0.0;
        switch (riskLevel.toUpperCase()) {
            case "CRITICAL":
            case "极高":
                return 1.0;
            case "HIGH":
            case "高":
                return 0.75;
            case "MEDIUM":
            case "中":
                return 0.5;
            case "LOW":
            case "低":
                return 0.25;
            default:
                return 0.0;
        }
    }

    private double encodeOriginRegion(String region) {
        if (region == null) return 0.5;
        String r = region.toLowerCase();
        if (r.contains("南方") || r.contains("south")
                || r.contains("广东") || r.contains("广西") || r.contains("福建")
                || r.contains("浙江") || r.contains("江苏") || r.contains("上海")
                || r.contains("湖南") || r.contains("湖北") || r.contains("江西")
                || r.contains("安徽") || r.contains("四川") || r.contains("重庆")
                || r.contains("贵州") || r.contains("云南") || r.contains("海南")) {
            return 0.0;
        }
        if (r.contains("北方") || r.contains("north")
                || r.contains("北京") || r.contains("天津") || r.contains("河北")
                || r.contains("山西") || r.contains("内蒙古") || r.contains("辽宁")
                || r.contains("吉林") || r.contains("黑龙江") || r.contains("山东")
                || r.contains("河南") || r.contains("陕西") || r.contains("甘肃")
                || r.contains("青海") || r.contains("宁夏") || r.contains("新疆")) {
            return 1.0;
        }
        return 0.5;
    }

    private double estimateActivityLevel(Soldier soldier) {
        String rank = soldier.getRank();
        if (rank == null) return 0.5;
        if (rank.contains("列兵") || rank.contains("上等兵") || rank.contains("Private")) {
            return 0.8;
        }
        if (rank.contains("士官") || rank.contains("Sergeant")) {
            return 0.6;
        }
        if (rank.contains("尉") || rank.contains("Lieutenant") || rank.contains("Captain")) {
            return 0.5;
        }
        if (rank.contains("校") || rank.contains("Major") || rank.contains("Colonel")) {
            return 0.3;
        }
        if (rank.contains("将") || rank.contains("General")) {
            return 0.1;
        }
        return 0.5;
    }

    private List<SupplementCatalog.SupplementType> parseSupplementLabels(String recommendedSupplementsJson) {
        if (recommendedSupplementsJson == null || recommendedSupplementsJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<String> names = objectMapper.readValue(recommendedSupplementsJson, new TypeReference<List<String>>() {});
            List<SupplementCatalog.SupplementType> types = new ArrayList<>();
            for (String name : names) {
                for (SupplementCatalog.SupplementType type : SupplementCatalog.SupplementType.values()) {
                    if (type.getDisplayName().equals(name) || type.name().equalsIgnoreCase(name)) {
                        types.add(type);
                        break;
                    }
                }
            }
            return types;
        } catch (JsonProcessingException e) {
            log.debug("解析补给品JSON失败: {}, 尝试按分隔符解析", recommendedSupplementsJson);
            List<SupplementCatalog.SupplementType> types = new ArrayList<>();
            String[] parts = recommendedSupplementsJson.split("[,、;；]");
            for (String part : parts) {
                String trimmed = part.trim();
                for (SupplementCatalog.SupplementType type : SupplementCatalog.SupplementType.values()) {
                    if (type.getDisplayName().equals(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                        types.add(type);
                        break;
                    }
                }
            }
            return types;
        }
    }

    private RecommendationResultDTO buildResultDTO(Soldier soldier, NutritionRisk risk,
                                                   NutritionInterventionTree.SoldierFeatures features,
                                                   List<NutritionInterventionTree.Intervention> interventions) {
        RecommendationResultDTO result = new RecommendationResultDTO();
        result.setSoldierId(soldier.getId());
        result.setSoldierName(soldier.getName());
        result.setRiskLevel(risk.getRiskLevel());
        result.setOverallRiskScore(risk.getOverallRiskScore());
        result.setDurationDays(config.getDefaultDurationDays());
        result.setRecommendationSource(decisionTree.isTrained() ? "决策树+内置规则" : "内置规则");

        Map<SupplementCatalog.SupplementType, RecommendationResultDTO.SupplementDetail> detailMap = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (NutritionInterventionTree.Intervention intervention : interventions) {
            SupplementCatalog.Supplement supplement = intervention.getSupplement();
            SupplementCatalog.SupplementType type = supplement.getType();

            RecommendationResultDTO.SupplementDetail detail;
            if (detailMap.containsKey(type)) {
                detail = detailMap.get(type);
            } else {
                detail = new RecommendationResultDTO.SupplementDetail();
                detail.setSupplementCode(type.name());
                detail.setSupplementName(supplement.getName());
                detail.setCategory(supplement.getCategory());
                detail.setDailyDosage(supplement.getDailyDosage());
                detail.setDailyDosageUnit(supplement.getUnit().getSymbol());
                detail.setDosageDescription(supplement.getDosageDescription());
                detail.setDailyCost(supplement.getDailyCost().setScale(4, RoundingMode.HALF_UP));
                detail.setNutrientContent(supplement.getNutrientContent());
                detailMap.put(type, detail);
            }

            for (String reason : intervention.getReasons()) {
                if (!detail.getReasons().contains(reason)) {
                    detail.getReasons().add(reason);
                }
            }
        }

        for (RecommendationResultDTO.SupplementDetail detail : detailMap.values()) {
            BigDecimal itemTotal = detail.getDailyCost()
                    .multiply(new BigDecimal(result.getDurationDays()))
                    .setScale(2, RoundingMode.HALF_UP);
            detail.setTotalCost(itemTotal);
            totalCost = totalCost.add(itemTotal);
            result.getSupplements().add(detail);
        }

        result.setTotalEstimatedCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        result.setMatchedRules(new ArrayList<>(features.getMatchedRules()));

        return result;
    }

    private void saveRecommendation(Soldier soldier, NutritionRisk risk, RecommendationResultDTO result) {
        InterventionRecommendation rec = new InterventionRecommendation();
        rec.setSoldierId(soldier.getId());
        rec.setSoldierName(soldier.getName());
        rec.setRiskLevel(risk.getRiskLevel());
        rec.setOverallRiskScore(risk.getOverallRiskScore());
        rec.setDurationDays(result.getDurationDays());
        rec.setStatus("PENDING");
        rec.setGeneratedAt(LocalDateTime.now());
        rec.setEstimatedCostTotal(result.getTotalEstimatedCost());

        List<String> supplementNames = result.getSupplements().stream()
                .map(RecommendationResultDTO.SupplementDetail::getSupplementName)
                .collect(Collectors.toList());

        List<String> dosageDescriptions = result.getSupplements().stream()
                .map(d -> String.format("%s: %s, 共%d天, 预估%.2f元",
                        d.getSupplementName(), d.getDosageDescription(),
                        result.getDurationDays(), d.getTotalCost()))
                .collect(Collectors.toList());

        try {
            rec.setRecommendedSupplements(objectMapper.writeValueAsString(supplementNames));
            rec.setRecommendedDosage(objectMapper.writeValueAsString(dosageDescriptions));
            rec.setMatchedRuleIds(objectMapper.writeValueAsString(result.getMatchedRules()));
        } catch (JsonProcessingException e) {
            rec.setRecommendedSupplements(String.join("、", supplementNames));
            rec.setRecommendedDosage(String.join("; ", dosageDescriptions));
            rec.setMatchedRuleIds(String.join(" | ", result.getMatchedRules()));
        }

        recommendationRepository.save(rec);
        log.debug("已保存推荐记录: id={}, soldierId={}", rec.getId(), soldier.getId());
    }
}
