package com.juyan.barracks.intervention.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class NutritionInterventionTreeTest {

    private NutritionInterventionTree.SoldierFeatures createVitaminCHighRiskSoldier() {
        NutritionInterventionTree.SoldierFeatures features =
                new NutritionInterventionTree.SoldierFeatures();
        features.setRiskLevel(0.1);
        features.setAge(25);
        features.setOriginRegionEncoded(0.5);
        features.setProteinRisk(0.1);
        features.setVitaminCRisk(0.85);
        features.setFatRisk(0.1);
        features.setActivityLevel(0.3);
        features.setOriginRegion("北京");
        return features;
    }

    private NutritionInterventionTree.SoldierFeatures createProteinHighRiskSoldier() {
        NutritionInterventionTree.SoldierFeatures features =
                new NutritionInterventionTree.SoldierFeatures();
        features.setRiskLevel(0.1);
        features.setAge(28);
        features.setOriginRegionEncoded(0.3);
        features.setProteinRisk(0.85);
        features.setVitaminCRisk(0.1);
        features.setFatRisk(0.1);
        features.setActivityLevel(0.5);
        features.setOriginRegion("上海");
        return features;
    }

    private NutritionInterventionTree.SoldierFeatures createLowRiskSoldier() {
        NutritionInterventionTree.SoldierFeatures features =
                new NutritionInterventionTree.SoldierFeatures();
        features.setRiskLevel(0.05);
        features.setAge(22);
        features.setOriginRegionEncoded(0.0);
        features.setProteinRisk(0.2);
        features.setVitaminCRisk(0.15);
        features.setFatRisk(0.1);
        features.setActivityLevel(0.4);
        features.setOriginRegion("河南");
        return features;
    }

    @Test
    void testFallbackRules() {
        NutritionInterventionTree tree = new NutritionInterventionTree();

        assertFalse(tree.isTrained(), "新创建的决策树应未训练");

        NutritionInterventionTree.SoldierFeatures vitaminCRiskSoldier = createVitaminCHighRiskSoldier();
        List<NutritionInterventionTree.Intervention> result =
                tree.predict(vitaminCRiskSoldier, 14);

        assertNotNull(result, "未训练时predict返回值不应为空");
        assertFalse(result.isEmpty(),
                "维C高风险士兵未训练时应通过fallback规则返回推荐");

        Set<String> supplementNames = result.stream()
                .map(iv -> iv.getSupplement().getName())
                .collect(Collectors.toSet());
        assertTrue(supplementNames.contains("干枣"),
                "维C高风险应推荐干枣，实际推荐: " + supplementNames);
        assertTrue(supplementNames.contains("新鲜蔬菜"),
                "维C高风险应推荐新鲜蔬菜，实际推荐: " + supplementNames);

        for (NutritionInterventionTree.Intervention iv : result) {
            assertNotNull(iv.getSupplement(), "补充剂不应为空");
            assertNotNull(iv.getSupplement().getType(), "补充剂类型不应为空");
            assertNotNull(iv.getDosage(), "剂量说明不应为空");
            assertFalse(iv.getReasons().isEmpty(),
                    "推荐应包含原因说明");
            assertEquals(14, iv.getDurationDays(),
                    "默认持续天数应为14天");
        }
    }

    @Test
    void testVitaminCHighRisk() {
        NutritionInterventionTree tree = new NutritionInterventionTree();

        NutritionInterventionTree.SoldierFeatures soldier = createVitaminCHighRiskSoldier();
        List<NutritionInterventionTree.Intervention> interventions =
                tree.predict(soldier, 21);

        assertNotNull(interventions, "推荐结果不应为空");
        assertFalse(interventions.isEmpty(), "维C高风险应返回至少一个推荐");

        Set<SupplementCatalog.SupplementType> types = interventions.stream()
                .map(iv -> iv.getSupplement().getType())
                .collect(Collectors.toSet());

        assertTrue(types.contains(SupplementCatalog.SupplementType.DRIED_DATE),
                "维C高风险士兵应包含干枣推荐，实际类型: " + types);
        assertTrue(types.contains(SupplementCatalog.SupplementType.FRESH_VEG),
                "维C高风险士兵应包含新鲜蔬菜推荐，实际类型: " + types);

        boolean hasDriedDateReason = false;
        boolean hasVegReason = false;
        for (NutritionInterventionTree.Intervention iv : interventions) {
            for (String reason : iv.getReasons()) {
                if (reason.contains("维C") || reason.contains("VitaminC")) {
                    if (iv.getSupplement().getType() == SupplementCatalog.SupplementType.DRIED_DATE) {
                        hasDriedDateReason = true;
                    }
                    if (iv.getSupplement().getType() == SupplementCatalog.SupplementType.FRESH_VEG) {
                        hasVegReason = true;
                    }
                }
            }
        }
        assertTrue(hasDriedDateReason || hasVegReason,
                "推荐理由应提及维C相关原因");

        assertTrue(soldier.getMatchedRules().size() > 0,
                "士兵特征应记录匹配的规则");
    }

    @Test
    void testProteinHighRisk() {
        NutritionInterventionTree tree = new NutritionInterventionTree();

        NutritionInterventionTree.SoldierFeatures soldier = createProteinHighRiskSoldier();
        List<NutritionInterventionTree.Intervention> interventions =
                tree.predict(soldier, 14);

        assertNotNull(interventions, "推荐结果不应为空");
        assertFalse(interventions.isEmpty(), "蛋白质高风险应返回至少一个推荐");

        Set<SupplementCatalog.SupplementType> types = interventions.stream()
                .map(iv -> iv.getSupplement().getType())
                .collect(Collectors.toSet());

        assertTrue(types.contains(SupplementCatalog.SupplementType.JERKY),
                "蛋白质高风险士兵应包含肉干推荐，实际类型: " + types);
        assertTrue(types.contains(SupplementCatalog.SupplementType.SOY),
                "蛋白质高风险士兵应包含豆制品推荐，实际类型: " + types);

        boolean hasProteinReason = false;
        for (NutritionInterventionTree.Intervention iv : interventions) {
            for (String reason : iv.getReasons()) {
                if (reason.contains("蛋白") || reason.contains("Protein")) {
                    hasProteinReason = true;
                    break;
                }
            }
            if (hasProteinReason) break;
        }
        assertTrue(hasProteinReason, "推荐理由应提及蛋白质相关原因");

        NutritionInterventionTree.SoldierFeatures lowRiskSoldier = createLowRiskSoldier();
        List<NutritionInterventionTree.Intervention> lowRiskInterventions =
                tree.predict(lowRiskSoldier, 14);
        assertTrue(lowRiskInterventions.isEmpty() ||
                        lowRiskInterventions.size() < interventions.size(),
                "低风险士兵的推荐数量应少于高风险士兵");
    }

    @Test
    void testFeatureArrayEncoding() {
        NutritionInterventionTree.SoldierFeatures features =
                new NutritionInterventionTree.SoldierFeatures();

        features.setRiskLevel(0.5);
        features.setAge(30);
        features.setOriginRegionEncoded(0.7);
        features.setProteinRisk(0.25);
        features.setVitaminCRisk(0.8);
        features.setFatRisk(0.15);
        features.setActivityLevel(0.6);

        double[] featureArray = features.toFeatureArray();

        assertNotNull(featureArray, "特征数组不应为空");
        assertEquals(7, featureArray.length,
                "特征数组长度应为7，实际: " + featureArray.length);

        assertEquals(0.5, featureArray[NutritionInterventionTree.FEATURE_RISK_LEVEL], 0.0001,
                "FEATURE_RISK_LEVEL位置不正确");
        assertEquals(30.0, featureArray[NutritionInterventionTree.FEATURE_AGE], 0.0001,
                "FEATURE_AGE位置不正确");
        assertEquals(0.7, featureArray[NutritionInterventionTree.FEATURE_ORIGIN_REGION], 0.0001,
                "FEATURE_ORIGIN_REGION位置不正确");
        assertEquals(0.25, featureArray[NutritionInterventionTree.FEATURE_PROTEIN_RISK], 0.0001,
                "FEATURE_PROTEIN_RISK位置不正确");
        assertEquals(0.8, featureArray[NutritionInterventionTree.FEATURE_VITAMIN_C_RISK], 0.0001,
                "FEATURE_VITAMIN_C_RISK位置不正确");
        assertEquals(0.15, featureArray[NutritionInterventionTree.FEATURE_FAT_RISK], 0.0001,
                "FEATURE_FAT_RISK位置不正确");
        assertEquals(0.6, featureArray[NutritionInterventionTree.FEATURE_ACTIVITY_LEVEL], 0.0001,
                "FEATURE_ACTIVITY_LEVEL位置不正确");

        assertEquals(7, NutritionInterventionTree.NUM_FEATURES,
                "NUM_FEATURES常量应为7");
        assertEquals(7, NutritionInterventionTree.FEATURE_NAMES.length,
                "FEATURE_NAMES数组长度应为7");
    }
}
