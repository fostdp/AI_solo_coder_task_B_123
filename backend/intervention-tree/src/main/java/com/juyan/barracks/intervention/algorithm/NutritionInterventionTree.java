package com.juyan.barracks.intervention.algorithm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class NutritionInterventionTree implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int FEATURE_RISK_LEVEL = 0;
    public static final int FEATURE_AGE = 1;
    public static final int FEATURE_ORIGIN_REGION = 2;
    public static final int FEATURE_PROTEIN_RISK = 3;
    public static final int FEATURE_VITAMIN_C_RISK = 4;
    public static final int FEATURE_FAT_RISK = 5;
    public static final int FEATURE_ACTIVITY_LEVEL = 6;

    public static final String[] FEATURE_NAMES = {
            "riskLevel", "age", "originRegion",
            "proteinRisk", "vitaminCRisk", "fatRisk", "activityLevel"
    };

    public static final int NUM_FEATURES = FEATURE_NAMES.length;

    private Node root;
    private final int maxDepth;
    private final int minSamplesSplit;
    private final SplitCriterion splitCriterion;
    private boolean trained = false;

    public enum SplitCriterion {
        GINI, ENTROPY
    }

    public NutritionInterventionTree() {
        this(10, 5, SplitCriterion.GINI);
    }

    public NutritionInterventionTree(int maxDepth, int minSamplesSplit, SplitCriterion splitCriterion) {
        this.maxDepth = maxDepth;
        this.minSamplesSplit = minSamplesSplit;
        this.splitCriterion = splitCriterion;
    }

    @Data
    public static class SoldierFeatures implements Serializable {
        private static final long serialVersionUID = 1L;

        private double riskLevel;
        private int age;
        private String originRegion;
        private double originRegionEncoded;
        private double proteinRisk;
        private double vitaminCRisk;
        private double fatRisk;
        private double activityLevel;
        private Set<String> matchedRules;

        public SoldierFeatures() {
            this.matchedRules = new LinkedHashSet<>();
        }

        public double[] toFeatureArray() {
            return new double[]{
                    riskLevel,
                    age,
                    originRegionEncoded,
                    proteinRisk,
                    vitaminCRisk,
                    fatRisk,
                    activityLevel
            };
        }

        public void addMatchedRule(String rule) {
            matchedRules.add(rule);
        }
    }

    @Data
    public static class Intervention implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum RecommendationSource {
            DECISION_TREE,
            FALLBACK_RULE,
            COMBINED,
            DEFAULT_FALLBACK
        }

        private SupplementCatalog.Supplement supplement;
        private int durationDays;
        private String dosage;
        private List<String> reasons;
        private RecommendationSource recommendationSource;
        private String fallbackReason;
        private boolean combinedRecommendation;

        public Intervention() {
            this.reasons = new ArrayList<>();
            this.recommendationSource = RecommendationSource.FALLBACK_RULE;
        }

        public Intervention(SupplementCatalog.Supplement supplement, int durationDays) {
            this.supplement = supplement;
            this.durationDays = durationDays;
            this.dosage = supplement.getDosageDescription();
            this.reasons = new ArrayList<>(supplement.getMatchedRules());
            this.recommendationSource = RecommendationSource.FALLBACK_RULE;
        }

        public void addReason(String reason) {
            if (!reasons.contains(reason)) {
                reasons.add(reason);
            }
        }

        public void adjustDosageByRiskLevel(double riskLevel) {
            double multiplier;
            int extraDays;
            if (riskLevel >= 0.9) {
                multiplier = 1.5;
                extraDays = 7;
            } else if (riskLevel >= 0.7) {
                multiplier = 1.0;
                extraDays = 0;
            } else if (riskLevel >= 0.5) {
                multiplier = 0.8;
                extraDays = 0;
            } else {
                multiplier = 0.5;
                extraDays = -3;
            }

            int originalDosage = supplement.getDailyDosage();
            int adjustedDosage = (int) Math.round(originalDosage * multiplier);
            supplement.setDailyDosage(Math.max(5, adjustedDosage));
            this.dosage = supplement.getDosageDescription();
            this.durationDays = Math.max(3, durationDays + extraDays);

            String doseAdjust = String.format("风险等级(%.2f)剂量调整: ×%.1f", riskLevel, multiplier);
            if (!reasons.contains(doseAdjust)) {
                reasons.add(doseAdjust);
            }
        }
    }

    @Data
    public static class TrainingSample implements Serializable {
        private static final long serialVersionUID = 1L;

        private double[] features;
        private List<SupplementCatalog.SupplementType> label;

        public TrainingSample(double[] features, List<SupplementCatalog.SupplementType> label) {
            this.features = features;
            this.label = label;
        }
    }

    public void train(List<TrainingSample> samples) {
        log.info("开始训练营养干预决策树: samples={}, maxDepth={}, criterion={}",
                samples.size(), maxDepth, splitCriterion);

        if (samples.isEmpty()) {
            log.warn("训练数据为空，将使用内置规则");
            this.trained = false;
            return;
        }

        this.root = buildTree(samples, 0);
        this.trained = true;
        log.info("决策树训练完成");
    }

    public boolean isTrained() {
        return trained;
    }

    private Node buildTree(List<TrainingSample> samples, int depth) {
        Node node = new Node();
        node.depth = depth;
        node.sampleCount = samples.size();

        if (shouldStop(samples, depth)) {
            node.isLeaf = true;
            node.recommendations = createLeafRecommendations(samples);
            node.ruleDescription = generateLeafRule(samples);
            return node;
        }

        Split bestSplit = findBestSplit(samples);
        if (bestSplit == null) {
            node.isLeaf = true;
            node.recommendations = createLeafRecommendations(samples);
            node.ruleDescription = generateLeafRule(samples);
            return node;
        }

        node.featureIndex = bestSplit.featureIndex;
        node.threshold = bestSplit.threshold;
        node.featureName = FEATURE_NAMES[bestSplit.featureIndex];
        node.gain = bestSplit.gain;

        List<TrainingSample> leftSamples = new ArrayList<>();
        List<TrainingSample> rightSamples = new ArrayList<>();

        for (TrainingSample sample : samples) {
            if (sample.features[bestSplit.featureIndex] <= bestSplit.threshold) {
                leftSamples.add(sample);
            } else {
                rightSamples.add(sample);
            }
        }

        node.left = buildTree(leftSamples, depth + 1);
        node.right = buildTree(rightSamples, depth + 1);

        return node;
    }

    private boolean shouldStop(List<TrainingSample> samples, int depth) {
        if (depth >= maxDepth) return true;
        if (samples.size() < minSamplesSplit) return true;
        return isLabelPure(samples);
    }

    private boolean isLabelPure(List<TrainingSample> samples) {
        if (samples.isEmpty()) return true;
        Set<List<SupplementCatalog.SupplementType>> labelSet = new HashSet<>();
        for (TrainingSample sample : samples) {
            labelSet.add(new ArrayList<>(sample.label));
        }
        return labelSet.size() == 1;
    }

    private Split findBestSplit(List<TrainingSample> samples) {
        double bestGain = -Double.MAX_VALUE;
        Split bestSplit = null;

        for (int featureIndex = 0; featureIndex < NUM_FEATURES; featureIndex++) {
            double[] values = samples.stream()
                    .mapToDouble(s -> s.features[featureIndex])
                    .distinct()
                    .sorted()
                    .toArray();

            if (values.length < 2) continue;

            for (int i = 0; i < values.length - 1; i++) {
                double threshold = (values[i] + values[i + 1]) / 2.0;
                double gain = calculateSplitGain(samples, featureIndex, threshold);

                if (gain > bestGain) {
                    bestGain = gain;
                    bestSplit = new Split(featureIndex, threshold, gain);
                }
            }
        }

        return bestSplit;
    }

    private double calculateSplitGain(List<TrainingSample> samples, int featureIndex, double threshold) {
        List<TrainingSample> left = new ArrayList<>();
        List<TrainingSample> right = new ArrayList<>();

        for (TrainingSample sample : samples) {
            if (sample.features[featureIndex] <= threshold) {
                left.add(sample);
            } else {
                right.add(sample);
            }
        }

        if (left.isEmpty() || right.isEmpty()) return -Double.MAX_VALUE;

        double parentImpurity = calculateImpurity(samples);
        double leftImpurity = calculateImpurity(left);
        double rightImpurity = calculateImpurity(right);

        double weightedChildImpurity =
                ((double) left.size() / samples.size()) * leftImpurity +
                        ((double) right.size() / samples.size()) * rightImpurity;

        return parentImpurity - weightedChildImpurity;
    }

    private double calculateImpurity(List<TrainingSample> samples) {
        if (samples.isEmpty()) return 0.0;

        Map<String, Integer> labelCounts = new HashMap<>();
        for (TrainingSample sample : samples) {
            String key = sample.label.stream()
                    .map(Enum::name)
                    .sorted()
                    .collect(Collectors.joining(","));
            labelCounts.merge(key, 1, Integer::sum);
        }

        int total = samples.size();

        if (splitCriterion == SplitCriterion.GINI) {
            double gini = 1.0;
            for (int count : labelCounts.values()) {
                double p = (double) count / total;
                gini -= p * p;
            }
            return gini;
        } else {
            double entropy = 0.0;
            for (int count : labelCounts.values()) {
                double p = (double) count / total;
                if (p > 0) {
                    entropy -= p * Math.log(p) / Math.log(2);
                }
            }
            return entropy;
        }
    }

    private List<SupplementCatalog.SupplementType> createLeafRecommendations(List<TrainingSample> samples) {
        Map<SupplementCatalog.SupplementType, Integer> counts = new HashMap<>();
        for (TrainingSample sample : samples) {
            for (SupplementCatalog.SupplementType type : sample.label) {
                counts.merge(type, 1, Integer::sum);
            }
        }

        int majorityThreshold = samples.size() / 2;
        List<SupplementCatalog.SupplementType> result = new ArrayList<>();
        for (Map.Entry<SupplementCatalog.SupplementType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= majorityThreshold) {
                result.add(entry.getKey());
            }
        }

        if (result.isEmpty() && !counts.isEmpty()) {
            counts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .ifPresent(e -> result.add(e.getKey()));
        }

        return result;
    }

    private String generateLeafRule(List<TrainingSample> samples) {
        List<SupplementCatalog.SupplementType> recs = createLeafRecommendations(samples);
        if (recs.isEmpty()) {
            return "无推荐";
        }
        return recs.stream()
                .map(SupplementCatalog.SupplementType::getDisplayName)
                .collect(Collectors.joining("、"));
    }

    private boolean lastPredictUsedFallback = false;
    private String lastFallbackReason = null;

    public boolean isFallbackUsed() {
        return lastPredictUsedFallback;
    }

    public String getLastFallbackReason() {
        return lastFallbackReason;
    }

    public List<String> getFallbackRuleDescriptions() {
        List<String> rules = new ArrayList<>();
        rules.add("VitaminCRisk > 0.7 → 干枣 + 新鲜蔬菜");
        rules.add("ProteinRisk > 0.7 → 肉干 + 豆制品");
        rules.add("FatRisk > 0.7 → 坚果");
        rules.add("Age > 40 → 鱼肝油");
        rules.add("南方士兵 → 豆制品比例+30%");
        rules.add("北方士兵 → 蔬菜比例+30%");
        return rules;
    }

    public int getTreeDepth() {
        return calculateTreeDepth(root);
    }

    private int calculateTreeDepth(Node node) {
        if (node == null) return 0;
        if (node.isLeaf) return node.depth + 1;
        return Math.max(calculateTreeDepth(node.left), calculateTreeDepth(node.right));
    }

    public List<Intervention> predict(SoldierFeatures features, int defaultDurationDays) {
        lastPredictUsedFallback = false;
        lastFallbackReason = null;

        try {
            List<Intervention> ruleBased = applyBuiltinRules(features, defaultDurationDays);
            markSource(ruleBased, Intervention.RecommendationSource.FALLBACK_RULE);

            List<Intervention> treeBased = Collections.emptyList();
            boolean treeSuccess = false;

            if (trained) {
                try {
                    treeBased = predictWithTree(features, defaultDurationDays);
                    markSource(treeBased, Intervention.RecommendationSource.DECISION_TREE);
                    treeSuccess = true;
                } catch (Exception e) {
                    log.warn("决策树预测异常，回退到内置规则: {}", e.getMessage());
                    lastPredictUsedFallback = true;
                    lastFallbackReason = "决策树异常: " + e.getMessage();
                }
            } else {
                lastPredictUsedFallback = true;
                lastFallbackReason = "决策树未训练";
            }

            boolean hasMultipleDeficiencies =
                    features.getVitaminCRisk() > 0.7 && features.getProteinRisk() > 0.7
                            || features.getVitaminCRisk() > 0.7 && features.getFatRisk() > 0.7
                            || features.getProteinRisk() > 0.7 && features.getFatRisk() > 0.7;

            Set<SupplementCatalog.SupplementType> combinedTypes = new LinkedHashSet<>();
            Map<SupplementCatalog.SupplementType, Intervention> interventionMap = new LinkedHashMap<>();

            for (Intervention intervention : ruleBased) {
                SupplementCatalog.SupplementType type = intervention.getSupplement().getType();
                combinedTypes.add(type);
                interventionMap.put(type, intervention);
            }

            for (Intervention intervention : treeBased) {
                SupplementCatalog.SupplementType type = intervention.getSupplement().getType();
                combinedTypes.add(type);
                if (interventionMap.containsKey(type)) {
                    Intervention existing = interventionMap.get(type);
                    for (String reason : intervention.getReasons()) {
                        existing.addReason(reason);
                    }
                    if (treeSuccess && !lastPredictUsedFallback) {
                        existing.setRecommendationSource(Intervention.RecommendationSource.COMBINED);
                    }
                } else {
                    interventionMap.put(type, intervention);
                }
            }

            List<Intervention> result = new ArrayList<>(interventionMap.values());

            if (combinedTypes.size() >= 2 || hasMultipleDeficiencies) {
                for (Intervention iv : result) {
                    iv.setCombinedRecommendation(true);
                    if (iv.getRecommendationSource() == Intervention.RecommendationSource.FALLBACK_RULE) {
                        iv.setRecommendationSource(Intervention.RecommendationSource.COMBINED);
                    }
                }
            }

            if (lastPredictUsedFallback) {
                for (Intervention iv : result) {
                    iv.setFallbackReason(lastFallbackReason);
                }
            }

            for (Intervention iv : result) {
                iv.adjustDosageByRiskLevel(features.getRiskLevel());
            }

            if (result.isEmpty() && features.getRiskLevel() > 0.5) {
                lastPredictUsedFallback = true;
                lastFallbackReason = "无匹配规则，返回默认推荐";
                result.add(createDefaultIntervention(defaultDurationDays));
            }

            return result;

        } catch (Exception e) {
            log.error("预测过程发生严重异常，返回默认推荐: {}", e.getMessage());
            lastPredictUsedFallback = true;
            lastFallbackReason = "严重异常: " + e.getMessage();
            List<Intervention> fallback = new ArrayList<>();
            fallback.add(createDefaultIntervention(defaultDurationDays));
            return fallback;
        }
    }

    private void markSource(List<Intervention> interventions, Intervention.RecommendationSource source) {
        for (Intervention iv : interventions) {
            iv.setRecommendationSource(source);
        }
    }

    private Intervention createDefaultIntervention(int durationDays) {
        SupplementCatalog.Supplement multivitamin = SupplementCatalog.getSupplement(
                SupplementCatalog.SupplementType.NUTS);
        multivitamin.addMatchedRule("默认推荐: 坚果补充基础营养");
        Intervention intervention = new Intervention(multivitamin, durationDays);
        intervention.setRecommendationSource(Intervention.RecommendationSource.DEFAULT_FALLBACK);
        intervention.setFallbackReason(lastFallbackReason);
        return intervention;
    }

    private List<Intervention> predictWithTree(SoldierFeatures features, int defaultDurationDays) {
        if (root == null) {
            return Collections.emptyList();
        }

        Node leaf = traverseTree(root, features.toFeatureArray());
        if (leaf == null || leaf.recommendations == null) {
            return Collections.emptyList();
        }

        List<Intervention> result = new ArrayList<>();
        for (SupplementCatalog.SupplementType type : leaf.recommendations) {
            SupplementCatalog.Supplement supplement = SupplementCatalog.getSupplement(type);
            supplement.addMatchedRule("决策树规则: " + leaf.ruleDescription);
            Intervention intervention = new Intervention(supplement, defaultDurationDays);
            result.add(intervention);
        }
        return result;
    }

    private Node traverseTree(Node node, double[] features) {
        if (node.isLeaf) {
            return node;
        }

        if (features[node.featureIndex] <= node.threshold) {
            return traverseTree(node.left, features);
        } else {
            return traverseTree(node.right, features);
        }
    }

    private List<Intervention> applyBuiltinRules(SoldierFeatures features, int defaultDurationDays) {
        List<Intervention> interventions = new ArrayList<>();
        Map<SupplementCatalog.SupplementType, SupplementCatalog.Supplement> supplementMap = new LinkedHashMap<>();

        if (features.getVitaminCRisk() > 0.7) {
            String rule = String.format("VitaminCRisk(%.2f) > 0.7", features.getVitaminCRisk());
            features.addMatchedRule(rule);

            SupplementCatalog.Supplement driedDate = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.DRIED_DATE);
            driedDate.addMatchedRule(rule + " → 推荐干枣补维C");
            supplementMap.put(SupplementCatalog.SupplementType.DRIED_DATE, driedDate);

            SupplementCatalog.Supplement freshVeg = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.FRESH_VEG);
            freshVeg.addMatchedRule(rule + " → 推荐新鲜蔬菜补维C");
            supplementMap.put(SupplementCatalog.SupplementType.FRESH_VEG, freshVeg);
        }

        if (features.getProteinRisk() > 0.7) {
            String rule = String.format("ProteinRisk(%.2f) > 0.7", features.getProteinRisk());
            features.addMatchedRule(rule);

            SupplementCatalog.Supplement jerky = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.JERKY);
            jerky.addMatchedRule(rule + " → 推荐肉干补蛋白");
            supplementMap.put(SupplementCatalog.SupplementType.JERKY, jerky);

            SupplementCatalog.Supplement soy = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.SOY);
            soy.addMatchedRule(rule + " → 推荐豆制品补蛋白");
            supplementMap.put(SupplementCatalog.SupplementType.SOY, soy);
        }

        if (features.getFatRisk() > 0.7) {
            String rule = String.format("FatRisk(%.2f) > 0.7", features.getFatRisk());
            features.addMatchedRule(rule);

            SupplementCatalog.Supplement nuts = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.NUTS);
            nuts.addMatchedRule(rule + " → 推荐坚果补脂肪");
            supplementMap.put(SupplementCatalog.SupplementType.NUTS, nuts);
        }

        if (features.getAge() > 40) {
            String rule = String.format("Age(%d) > 40", features.getAge());
            features.addMatchedRule(rule);

            SupplementCatalog.Supplement fishLiverOil = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.FISH_LIVER_OIL);
            fishLiverOil.addMatchedRule(rule + " → 推荐鱼肝油补充维生素A/D");
            supplementMap.put(SupplementCatalog.SupplementType.FISH_LIVER_OIL, fishLiverOil);
        }

        if (features.getOriginRegion() != null) {
            if (isSouthernRegion(features.getOriginRegion())) {
                String rule = String.format("南方士兵(%s) → 增加豆制品比例", features.getOriginRegion());
                features.addMatchedRule(rule);

                if (!supplementMap.containsKey(SupplementCatalog.SupplementType.SOY)) {
                    SupplementCatalog.Supplement soy = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.SOY);
                    soy.addMatchedRule(rule);
                    supplementMap.put(SupplementCatalog.SupplementType.SOY, soy);
                } else {
                    SupplementCatalog.Supplement soy = supplementMap.get(SupplementCatalog.SupplementType.SOY);
                    soy.setDailyDosage((int) (soy.getDailyDosage() * 1.3));
                    soy.addMatchedRule(rule + " → 豆制品剂量增加30%");
                }
            }

            if (isNorthernRegion(features.getOriginRegion())) {
                String rule = String.format("北方士兵(%s) → 增加蔬菜比例", features.getOriginRegion());
                features.addMatchedRule(rule);

                if (!supplementMap.containsKey(SupplementCatalog.SupplementType.FRESH_VEG)) {
                    SupplementCatalog.Supplement freshVeg = SupplementCatalog.getSupplement(SupplementCatalog.SupplementType.FRESH_VEG);
                    freshVeg.addMatchedRule(rule);
                    supplementMap.put(SupplementCatalog.SupplementType.FRESH_VEG, freshVeg);
                } else {
                    SupplementCatalog.Supplement freshVeg = supplementMap.get(SupplementCatalog.SupplementType.FRESH_VEG);
                    freshVeg.setDailyDosage((int) (freshVeg.getDailyDosage() * 1.3));
                    freshVeg.addMatchedRule(rule + " → 蔬菜剂量增加30%");
                }
            }
        }

        for (SupplementCatalog.Supplement supplement : supplementMap.values()) {
            Intervention intervention = new Intervention(supplement, defaultDurationDays);
            interventions.add(intervention);
        }

        return interventions;
    }

    private boolean isSouthernRegion(String region) {
        if (region == null) return false;
        String r = region.toLowerCase();
        return r.contains("广东") || r.contains("广西") || r.contains("福建") || r.contains("浙江")
                || r.contains("江苏") || r.contains("上海") || r.contains("湖南") || r.contains("湖北")
                || r.contains("江西") || r.contains("安徽") || r.contains("四川") || r.contains("重庆")
                || r.contains("贵州") || r.contains("云南") || r.contains("海南") || r.contains("香港")
                || r.contains("澳门") || r.contains("台湾") || r.contains("南方") || r.contains("south");
    }

    private boolean isNorthernRegion(String region) {
        if (region == null) return false;
        String r = region.toLowerCase();
        return r.contains("北京") || r.contains("天津") || r.contains("河北") || r.contains("山西")
                || r.contains("内蒙古") || r.contains("辽宁") || r.contains("吉林") || r.contains("黑龙江")
                || r.contains("山东") || r.contains("河南") || r.contains("陕西") || r.contains("甘肃")
                || r.contains("青海") || r.contains("宁夏") || r.contains("新疆") || r.contains("西藏")
                || r.contains("北方") || r.contains("north");
    }

    public double crossValidate(List<TrainingSample> samples, int k) {
        if (samples.size() < k) {
            log.warn("样本数 {} 小于折数 {}，跳过交叉验证", samples.size(), k);
            return 0.0;
        }

        Collections.shuffle(samples, new Random(42));
        int foldSize = samples.size() / k;
        double totalAccuracy = 0.0;

        for (int i = 0; i < k; i++) {
            List<TrainingSample> test = new ArrayList<>();
            List<TrainingSample> train = new ArrayList<>();

            for (int j = 0; j < samples.size(); j++) {
                if (j >= i * foldSize && j < (i + 1) * foldSize) {
                    test.add(samples.get(j));
                } else {
                    train.add(samples.get(j));
                }
            }

            Node savedRoot = this.root;
            boolean savedTrained = this.trained;

            if (!train.isEmpty()) {
                this.root = buildTree(train, 0);
                this.trained = true;
            }

            int correct = 0;
            for (TrainingSample sample : test) {
                Set<SupplementCatalog.SupplementType> actual = new HashSet<>(sample.label);
                SoldierFeatures sf = new SoldierFeatures();
                double[] fa = sample.features;
                sf.setRiskLevel(fa[0]);
                sf.setAge((int) fa[1]);
                sf.setOriginRegionEncoded(fa[2]);
                sf.setProteinRisk(fa[3]);
                sf.setVitaminCRisk(fa[4]);
                sf.setFatRisk(fa[5]);
                sf.setActivityLevel(fa[6]);

                List<Intervention> predicted = predictWithTree(sf, 14);
                Set<SupplementCatalog.SupplementType> predictedSet = predicted.stream()
                        .map(iv -> iv.getSupplement().getType())
                        .collect(Collectors.toSet());

                if (predictedSet.equals(actual) || (!predictedSet.isEmpty() && !actual.isEmpty()
                        && predictedSet.containsAll(actual))) {
                    correct++;
                }
            }

            totalAccuracy += (double) correct / test.size();

            this.root = savedRoot;
            this.trained = savedTrained;
        }

        return totalAccuracy / k;
    }

    public List<String> getRuleDescriptions() {
        List<String> rules = new ArrayList<>();
        collectRules(root, "", rules);
        return rules;
    }

    private void collectRules(Node node, String prefix, List<String> rules) {
        if (node == null) return;

        if (node.isLeaf) {
            rules.add(prefix + " → " + node.ruleDescription + " (样本数: " + node.sampleCount + ")");
            return;
        }

        String condition = String.format("%s ≤ %.3f", node.featureName, node.threshold);
        collectRules(node.left, prefix.isEmpty() ? condition : prefix + " AND " + condition, rules);

        condition = String.format("%s > %.3f", node.featureName, node.threshold);
        collectRules(node.right, prefix.isEmpty() ? condition : prefix + " AND " + condition, rules);
    }

    @Data
    private static class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean isLeaf;
        private int featureIndex;
        private String featureName;
        private double threshold;
        private double gain;
        private int depth;
        private int sampleCount;
        private List<SupplementCatalog.SupplementType> recommendations;
        private String ruleDescription;
        private Node left;
        private Node right;
    }

    @Data
    private static class Split implements Serializable {
        private static final long serialVersionUID = 1L;

        private final int featureIndex;
        private final double threshold;
        private final double gain;
    }
}
