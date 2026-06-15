package com.juyan.barracks.supply.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juyan.barracks.common.entity.AssociationRule;
import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.SupplyDeficitRecord;
import com.juyan.barracks.common.repository.AssociationRuleRepository;
import com.juyan.barracks.common.repository.BarracksRepository;
import com.juyan.barracks.common.repository.SupplyDeficitRecordRepository;
import com.juyan.barracks.supply.algorithm.AprioriSupplyAnalyzer;
import com.juyan.barracks.supply.algorithm.AssociationRuleResult;
import com.juyan.barracks.supply.dto.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyAnalysisService {

    private final SupplyDeficitRecordRepository deficitRecordRepository;
    private final AssociationRuleRepository associationRuleRepository;
    private final BarracksRepository barracksRepository;
    private final ObjectMapper objectMapper;

    @Value("${apriori.min-support:0.10}")
    private double minSupport;

    @Value("${apriori.min-confidence:0.60}")
    private double minConfidence;

    @Value("${apriori.min-lift:1.5}")
    private double minLift;

    @Value("${apriori.analysis-window-days:30}")
    private int analysisWindowDays;

    private final AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();

    private static final String[] FOOD_CATEGORIES = {
            "蔬菜类", "肉类", "豆制品", "乳制品", "谷物类", "油脂类", "蛋类", "水产类"
    };

    private static final String[] WEATHER_CONDITIONS = {
            "晴天", "多云", "暴雨", "大雪", "沙尘暴", "大雾", "高温"
    };

    private static final String[] SUPPLY_ROUTES = {
            "东线", "西线", "南线", "北线"
    };

    private static final String[] BARRACKS_CATEGORIES = {
            "边境", "山区", "平原"
    };

    @PostConstruct
    public void init() {
        log.info("补给短缺Apriori分析服务初始化完成");
        log.info("配置参数: minSupport={}, minConfidence={}, minLift={}, windowDays={}",
                minSupport, minConfidence, minLift, analysisWindowDays);
    }

    @Scheduled(cron = "${scheduling.supply-analysis.cron:0 0 4 * * ?}")
    public void scheduledAnalysis() {
        log.info("开始执行定时补给短缺分析任务...");
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(analysisWindowDays);
            analyzeSupplyDeficits(startDate, endDate);
            log.info("定时补给短缺分析任务执行完成");
        } catch (Exception e) {
            log.error("定时补给短缺分析任务执行失败", e);
        }
    }

    @Transactional
    public AnalysisResultDTO analyzeSupplyDeficits(LocalDate startDate, LocalDate endDate) {
        log.info("开始分析补给短缺: {} ~ {}", startDate, endDate);

        List<SupplyDeficitRecord> records = deficitRecordRepository.findByDateRange(startDate, endDate);
        log.info("从数据库读取到 {} 条短缺记录", records.size());

        if (records.isEmpty()) {
            log.info("数据库中无短缺记录，开始生成模拟数据...");
            records = generateMockData(startDate, endDate);
            log.info("生成模拟数据完成，共 {} 条记录", records.size());
        }

        List<Barracks> barracksList = barracksRepository.findAll();
        Map<Long, String> barracksCategoryMap = buildBarracksCategoryMap(barracksList);

        List<Set<String>> transactions = buildTransactions(records, barracksCategoryMap);
        log.info("构建事务完成，共 {} 个事务", transactions.size());

        if (transactions.isEmpty()) {
            return AnalysisResultDTO.builder()
                    .analyzedAt(LocalDateTime.now())
                    .totalTransactions(0)
                    .frequentItemSetCount(0)
                    .ruleCount(0)
                    .significantRuleCount(0)
                    .build();
        }

        List<AssociationRuleResult.ItemSet> frequentItemSets = analyzer.findFrequentItemSets(transactions, minSupport);
        log.info("挖掘频繁项集完成，共 {} 个", frequentItemSets.size());

        List<AssociationRuleResult> rules = analyzer.generateAssociationRules(frequentItemSets, transactions, minConfidence);
        log.info("生成关联规则完成，共 {} 条", rules.size());

        List<AssociationRuleResult> significantRules = rules.stream()
                .filter(r -> r.getLift().compareTo(BigDecimal.valueOf(minLift)) > 0)
                .collect(Collectors.toList());
        log.info("显著规则(lift>{}): {} 条", minLift, significantRules.size());

        saveAssociationRules(rules, transactions.size());

        List<TopDeficitItem> topDeficitItems = computeTopDeficitItems(records, 5);
        List<String> suggestions = generateImprovementSuggestions(significantRules, topDeficitItems);

        return AnalysisResultDTO.builder()
                .analyzedAt(LocalDateTime.now())
                .totalTransactions(transactions.size())
                .frequentItemSetCount(frequentItemSets.size())
                .ruleCount(rules.size())
                .significantRuleCount(significantRules.size())
                .significantRules(significantRules)
                .topDeficitItems(topDeficitItems)
                .improvementSuggestions(suggestions)
                .build();
    }

    private Map<Long, String> buildBarracksCategoryMap(List<Barracks> barracksList) {
        Map<Long, String> map = new HashMap<>();
        if (barracksList == null || barracksList.isEmpty()) {
            for (long i = 1; i <= 12; i++) {
                map.put(i, BARRACKS_CATEGORIES[(int) ((i - 1) % BARRACKS_CATEGORIES.length)]);
            }
        } else {
            int idx = 0;
            for (Barracks b : barracksList) {
                String category;
                if (b.getName() != null) {
                    String name = b.getName();
                    if (name.contains("边境") || name.contains("边防")) category = "边境";
                    else if (name.contains("山")) category = "山区";
                    else category = "平原";
                } else {
                    category = BARRACKS_CATEGORIES[idx % BARRACKS_CATEGORIES.length];
                }
                map.put(b.getId(), category);
                idx++;
            }
        }
        return map;
    }

    private List<Set<String>> buildTransactions(List<SupplyDeficitRecord> records,
                                                 Map<Long, String> barracksCategoryMap) {
        Map<String, Set<String>> transactionMap = new LinkedHashMap<>();

        for (SupplyDeficitRecord record : records) {
            String key = record.getDeficitDate() + "_" + record.getBarracksId();
            Set<String> transaction = transactionMap.computeIfAbsent(key, k -> new HashSet<>());

            if (record.getWeatherCondition() != null && !record.getWeatherCondition().isEmpty()) {
                transaction.add("天气_" + record.getWeatherCondition());
            }

            if (record.getSupplyRoute() != null && !record.getSupplyRoute().isEmpty()) {
                transaction.add("路线_" + record.getSupplyRoute());
            }

            String category = barracksCategoryMap.getOrDefault(record.getBarracksId(), "平原");
            transaction.add("兵营_" + category);

            if (record.getDeficitRate() != null && record.getDeficitRate().compareTo(BigDecimal.valueOf(0.15)) >= 0) {
                String foodCat = record.getFoodCategory() != null ? record.getFoodCategory() : "其他";
                transaction.add("短缺_" + foodCat);
            }
        }

        return new ArrayList<>(transactionMap.values());
    }

    private void saveAssociationRules(List<AssociationRuleResult> rules, int totalTransactions) {
        associationRuleRepository.deleteAll();
        log.info("清空历史关联规则数据");

        List<AssociationRule> entities = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (AssociationRuleResult rule : rules) {
            AssociationRule entity = new AssociationRule();
            try {
                entity.setAntecedentItems(objectMapper.writeValueAsString(rule.getAntecedent()));
                entity.setConsequentItems(objectMapper.writeValueAsString(rule.getConsequent()));
            } catch (JsonProcessingException e) {
                entity.setAntecedentItems(String.join(",", rule.getAntecedent()));
                entity.setConsequentItems(String.join(",", rule.getConsequent()));
            }
            entity.setSupport(rule.getSupport());
            entity.setConfidence(rule.getConfidence());
            entity.setLift(rule.getLift());
            entity.setAntecedentCount(countAntecedent(rule, totalTransactions));
            entity.setConsequentCount(countConsequent(rule, totalTransactions));
            entity.setBothCount(rule.getCount());
            entity.setTotalTransactions(totalTransactions);
            entity.setRuleDescription(rule.getDescription());
            entity.setIsSignificant(rule.getLift().compareTo(BigDecimal.valueOf(minLift)) > 0);
            entity.setAnalyzedAt(now);
            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            associationRuleRepository.saveAll(entities);
            log.info("保存关联规则 {} 条到数据库", entities.size());
        }
    }

    private int countAntecedent(AssociationRuleResult rule, int total) {
        double supportA = rule.getSupport().doubleValue() / Math.max(rule.getConfidence().doubleValue(), 0.0001);
        return (int) Math.round(supportA * total);
    }

    private int countConsequent(AssociationRuleResult rule, int total) {
        double supportB = rule.getConfidence().doubleValue() / Math.max(rule.getLift().doubleValue(), 0.0001);
        return (int) Math.round(supportB * total);
    }

    public List<SupplyDeficitRecord> getDeficitRecords(LocalDate startDate, LocalDate endDate, Long barracksId) {
        List<SupplyDeficitRecord> records;
        if (startDate != null && endDate != null) {
            records = deficitRecordRepository.findByDateRange(startDate, endDate);
        } else if (barracksId != null) {
            records = deficitRecordRepository.findByBarracksIdOrderByDeficitDateDesc(barracksId);
        } else {
            records = deficitRecordRepository.findAll();
        }
        return records.stream()
                .filter(r -> barracksId == null || r.getBarracksId().equals(barracksId))
                .sorted(Comparator.comparing(SupplyDeficitRecord::getDeficitDate).reversed())
                .limit(500)
                .collect(Collectors.toList());
    }

    public List<AssociationRuleResult> getAllAssociationRules() {
        return associationRuleRepository.findTop50ByOrderByAnalyzedAtDesc().stream()
                .map(this::convertToRuleResult)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<AssociationRuleResult> getSignificantRules() {
        return associationRuleRepository.findHighLiftRules().stream()
                .map(this::convertToRuleResult)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private AssociationRuleResult convertToRuleResult(AssociationRule entity) {
        AssociationRuleResult result = new AssociationRuleResult();
        try {
            if (entity.getAntecedentItems() != null && entity.getAntecedentItems().startsWith("[")) {
                result.setAntecedent(objectMapper.readValue(entity.getAntecedentItems(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            } else if (entity.getAntecedentItems() != null) {
                result.setAntecedent(Arrays.asList(entity.getAntecedentItems().split(",")));
            }

            if (entity.getConsequentItems() != null && entity.getConsequentItems().startsWith("[")) {
                result.setConsequent(objectMapper.readValue(entity.getConsequentItems(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
            } else if (entity.getConsequentItems() != null) {
                result.setConsequent(Arrays.asList(entity.getConsequentItems().split(",")));
            }
        } catch (Exception e) {
            log.warn("解析关联规则项集失败: {}", e.getMessage());
            return null;
        }
        result.setSupport(entity.getSupport());
        result.setConfidence(entity.getConfidence());
        result.setLift(entity.getLift());
        result.setLeverage(BigDecimal.ZERO);
        result.setCount(entity.getBothCount());
        result.setDescription(entity.getRuleDescription());
        return result;
    }

    public List<TopDeficitItem> getTopDeficitItems(int limit) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(analysisWindowDays);
        List<SupplyDeficitRecord> records = deficitRecordRepository.findByDateRange(startDate, endDate);
        if (records.isEmpty()) {
            records = deficitRecordRepository.findAll();
        }
        return computeTopDeficitItems(records, limit);
    }

    private List<TopDeficitItem> computeTopDeficitItems(List<SupplyDeficitRecord> records, int limit) {
        Map<String, List<SupplyDeficitRecord>> grouped = records.stream()
                .filter(r -> r.getFoodCategory() != null)
                .collect(Collectors.groupingBy(SupplyDeficitRecord::getFoodCategory));

        List<TopDeficitItem> items = new ArrayList<>();

        for (Map.Entry<String, List<SupplyDeficitRecord>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<SupplyDeficitRecord> catRecords = entry.getValue();

            long count = catRecords.size();
            BigDecimal avgRate = catRecords.stream()
                    .map(SupplyDeficitRecord::getDeficitRate)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(Math.max(count, 1)), 4, RoundingMode.HALF_UP);

            BigDecimal maxRate = catRecords.stream()
                    .map(SupplyDeficitRecord::getDeficitRate)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            int affectedBarracks = (int) catRecords.stream()
                    .map(SupplyDeficitRecord::getBarracksId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();

            items.add(TopDeficitItem.builder()
                    .foodCategory(category)
                    .categoryName(translateCategory(category))
                    .totalDeficitCount(count)
                    .avgDeficitRate(avgRate)
                    .maxDeficitRate(maxRate)
                    .affectedBarracksCount(affectedBarracks)
                    .build());
        }

        items.sort((a, b) -> Long.compare(b.getTotalDeficitCount(), a.getTotalDeficitCount()));
        return items.stream().limit(limit).collect(Collectors.toList());
    }

    public List<WeatherImpactStat> getWeatherImpactStats() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(analysisWindowDays);
        List<SupplyDeficitRecord> records = deficitRecordRepository.findByDateRange(startDate, endDate);
        if (records.isEmpty()) {
            records = deficitRecordRepository.findAll();
        }

        Map<String, List<SupplyDeficitRecord>> byWeather = records.stream()
                .filter(r -> r.getWeatherCondition() != null)
                .collect(Collectors.groupingBy(SupplyDeficitRecord::getWeatherCondition));

        long totalAll = records.size();
        long deficitAll = records.stream()
                .filter(r -> r.getDeficitRate() != null && r.getDeficitRate().compareTo(BigDecimal.valueOf(0.30)) > 0)
                .count();
        double baseRate = totalAll > 0 ? (double) deficitAll / totalAll : 0.0;

        List<WeatherImpactStat> stats = new ArrayList<>();

        for (String weather : WEATHER_CONDITIONS) {
            List<SupplyDeficitRecord> wRecords = byWeather.getOrDefault(weather, Collections.emptyList());
            if (wRecords.isEmpty()) continue;

            long total = wRecords.size();
            long deficit = wRecords.stream()
                    .filter(r -> r.getDeficitRate() != null && r.getDeficitRate().compareTo(BigDecimal.valueOf(0.30)) > 0)
                    .count();

            BigDecimal rate = total > 0
                    ? BigDecimal.valueOf(deficit).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal avgAmount = wRecords.stream()
                    .map(SupplyDeficitRecord::getDeficitAmountG)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(Math.max(total, 1)), 2, RoundingMode.HALF_UP);

            double rateRatio = baseRate > 0 ? rate.doubleValue() / baseRate : 1.0;
            String riskLevel;
            if (rateRatio >= 1.5 || rate.doubleValue() >= 0.4) riskLevel = "高风险";
            else if (rateRatio >= 1.1 || rate.doubleValue() >= 0.25) riskLevel = "中风险";
            else riskLevel = "低风险";

            stats.add(WeatherImpactStat.builder()
                    .weatherCondition(weather)
                    .weatherName(weather + "天气")
                    .totalDays(total)
                    .deficitDays(deficit)
                    .deficitRate(rate)
                    .avgDeficitAmount(avgAmount)
                    .riskLevel(riskLevel)
                    .build());
        }

        stats.sort((a, b) -> b.getDeficitRate().compareTo(a.getDeficitRate()));
        return stats;
    }

    public List<RouteImpactStat> getRouteImpactStats() {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(analysisWindowDays);
        List<SupplyDeficitRecord> records = deficitRecordRepository.findByDateRange(startDate, endDate);
        if (records.isEmpty()) {
            records = deficitRecordRepository.findAll();
        }

        Map<String, List<SupplyDeficitRecord>> byRoute = records.stream()
                .filter(r -> r.getSupplyRoute() != null)
                .collect(Collectors.groupingBy(SupplyDeficitRecord::getSupplyRoute));

        long totalAll = records.size();
        long deficitAll = records.stream()
                .filter(r -> r.getDeficitRate() != null && r.getDeficitRate().compareTo(BigDecimal.valueOf(0.30)) > 0)
                .count();
        double baseRate = totalAll > 0 ? (double) deficitAll / totalAll : 0.0;

        List<RouteImpactStat> stats = new ArrayList<>();

        for (String route : SUPPLY_ROUTES) {
            List<SupplyDeficitRecord> rRecords = byRoute.getOrDefault(route, Collections.emptyList());
            if (rRecords.isEmpty()) continue;

            long total = rRecords.size();
            long deficit = rRecords.stream()
                    .filter(r -> r.getDeficitRate() != null && r.getDeficitRate().compareTo(BigDecimal.valueOf(0.30)) > 0)
                    .count();

            BigDecimal rate = total > 0
                    ? BigDecimal.valueOf(deficit).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal avgDeficitRate = rRecords.stream()
                    .map(SupplyDeficitRecord::getDeficitRate)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(Math.max(total, 1)), 4, RoundingMode.HALF_UP);

            double rateRatio = baseRate > 0 ? rate.doubleValue() / baseRate : 1.0;
            String riskLevel;
            if (rateRatio >= 1.5 || rate.doubleValue() >= 0.4) riskLevel = "高风险";
            else if (rateRatio >= 1.1 || rate.doubleValue() >= 0.25) riskLevel = "中风险";
            else riskLevel = "低风险";

            stats.add(RouteImpactStat.builder()
                    .supplyRoute(route)
                    .routeName(route + "补给路线")
                    .totalDeliveries(total)
                    .deficitDeliveries(deficit)
                    .deficitRate(rate)
                    .avgDeficitRate(avgDeficitRate)
                    .riskLevel(riskLevel)
                    .build());
        }

        stats.sort((a, b) -> b.getDeficitRate().compareTo(a.getDeficitRate()));
        return stats;
    }

    private List<String> generateImprovementSuggestions(List<AssociationRuleResult> significantRules,
                                                        List<TopDeficitItem> topDeficitItems) {
        List<String> suggestions = new ArrayList<>();

        if (significantRules.isEmpty() && topDeficitItems.isEmpty()) {
            suggestions.add("当前数据正常，无显著补给短缺风险");
            return suggestions;
        }

        for (AssociationRuleResult rule : significantRules.stream().limit(3).collect(Collectors.toList())) {
            StringBuilder sb = new StringBuilder();
            sb.append("【预警】");
            sb.append(rule.getDescription());
            sb.append("，建议提前做好");
            List<String> deficitItems = rule.getConsequent().stream()
                    .filter(s -> s.startsWith("短缺_"))
                    .map(s -> s.replace("短缺_", ""))
                    .collect(Collectors.toList());
            if (!deficitItems.isEmpty()) {
                sb.append(String.join("、", deficitItems)).append("的");
            }
            sb.append("应急储备预案");
            suggestions.add(sb.toString());
        }

        if (!topDeficitItems.isEmpty()) {
            TopDeficitItem topItem = topDeficitItems.get(0);
            if (topItem.getAvgDeficitRate().compareTo(BigDecimal.valueOf(0.30)) > 0) {
                suggestions.add(String.format("【重点关注】%s短缺最为严重（平均短缺率%.1f%%），建议增加供应商备选方案",
                        topItem.getCategoryName(), topItem.getAvgDeficitRate().multiply(BigDecimal.valueOf(100))));
            }
        }

        Set<String> highRiskWeather = new HashSet<>();
        Set<String> highRiskRoute = new HashSet<>();
        for (AssociationRuleResult rule : significantRules) {
            for (String item : rule.getAntecedent()) {
                if (item.startsWith("天气_")) highRiskWeather.add(item.replace("天气_", ""));
                if (item.startsWith("路线_")) highRiskRoute.add(item.replace("路线_", ""));
            }
        }

        if (!highRiskWeather.isEmpty()) {
            suggestions.add("【天气预警】" + String.join("、", highRiskWeather) + "天气易导致补给短缺，建议提前调整运输计划");
        }
        if (!highRiskRoute.isEmpty()) {
            suggestions.add("【路线预警】" + String.join("、", highRiskRoute) + "路线补给风险较高，建议启用备用路线");
        }

        return suggestions;
    }

    private String translateCategory(String category) {
        return category;
    }

    @Transactional
    public List<SupplyDeficitRecord> generateMockData(LocalDate startDate, LocalDate endDate) {
        List<SupplyDeficitRecord> allRecords = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        List<Barracks> barracksList = barracksRepository.findAll();
        if (barracksList.isEmpty()) {
            barracksList = new ArrayList<>();
        }
        int barracksCount = Math.max(barracksList.size(), 10);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (int bIdx = 0; bIdx < barracksCount; bIdx++) {
                Long barracksId = bIdx < barracksList.size() ? barracksList.get(bIdx).getId() : (long) (bIdx + 1);
                String barracksCategory;
                if (bIdx < barracksList.size() && barracksList.get(bIdx).getName() != null) {
                    String name = barracksList.get(bIdx).getName();
                    if (name.contains("边境") || name.contains("边防")) barracksCategory = "边境";
                    else if (name.contains("山")) barracksCategory = "山区";
                    else barracksCategory = "平原";
                } else {
                    barracksCategory = BARRACKS_CATEGORIES[bIdx % BARRACKS_CATEGORIES.length];
                }

                String weather = pickWeather(date, barracksCategory, random);
                String route = pickRoute(barracksCategory, random);

                boolean badWeather = weather.equals("暴雨") || weather.equals("大雪") || weather.equals("沙尘暴");
                boolean mountainRoute = barracksCategory.equals("山区") && (route.equals("西线") || route.equals("北线"));
                boolean borderEast = barracksCategory.equals("边境") && route.equals("东线");

                double deficitBaseProb = 0.20;
                if (badWeather) deficitBaseProb += 0.35;
                if (mountainRoute) deficitBaseProb += 0.25;
                if (borderEast) deficitBaseProb += 0.20;

                int deficitCategories = 0;
                if (random.nextDouble() < deficitBaseProb) {
                    deficitCategories = random.nextInt(3) + 1;
                }

                if (deficitCategories > 0) {
                    List<String> shuffledCats = new ArrayList<>(Arrays.asList(FOOD_CATEGORIES));
                    Collections.shuffle(shuffledCats, random);

                    for (int c = 0; c < deficitCategories && c < shuffledCats.size(); c++) {
                        String category = shuffledCats.get(c);

                        double rateMultiplier = 1.0;
                        if (badWeather) rateMultiplier *= 1.5;
                        if (mountainRoute) rateMultiplier *= 1.4;
                        if ((category.equals("蔬菜类") || category.equals("豆制品")) && (weather.equals("暴雨") || weather.equals("大雪"))) {
                            rateMultiplier *= 1.3;
                        }
                        if (category.equals("肉类") && barracksCategory.equals("山区")) {
                            rateMultiplier *= 1.2;
                        }
                        if (category.equals("乳制品") && route.equals("西线")) {
                            rateMultiplier *= 1.3;
                        }

                        double deficitRateValue = (0.15 + random.nextDouble() * 0.55) * rateMultiplier;
                        deficitRateValue = Math.min(deficitRateValue, 0.90);
                        double standard = 50000 + random.nextDouble() * 150000;
                        double actual = standard * (1 - deficitRateValue);
                        double deficitAmount = standard - actual;

                        SupplyDeficitRecord record = new SupplyDeficitRecord();
                        record.setBarracksId(barracksId);
                        record.setDeficitDate(date);
                        record.setFoodCategory(category);
                        record.setFoodName(getFoodName(category, random));
                        record.setStandardRationG(BigDecimal.valueOf(standard).setScale(2, RoundingMode.HALF_UP));
                        record.setActualDeliveredG(BigDecimal.valueOf(actual).setScale(2, RoundingMode.HALF_UP));
                        record.setDeficitAmountG(BigDecimal.valueOf(deficitAmount).setScale(2, RoundingMode.HALF_UP));
                        record.setDeficitRate(BigDecimal.valueOf(deficitRateValue).setScale(4, RoundingMode.HALF_UP));
                        record.setWeatherCondition(weather);
                        record.setSupplyRoute(route);
                        record.setCreatedAt(LocalDateTime.now());
                        allRecords.add(record);
                    }
                }

                if (random.nextDouble() < 0.3) {
                    String category = FOOD_CATEGORIES[random.nextInt(FOOD_CATEGORIES.length)];
                    double deficitRateValue = 0.01 + random.nextDouble() * 0.10;
                    double standard = 50000 + random.nextDouble() * 150000;
                    double actual = standard * (1 - deficitRateValue);
                    double deficitAmount = standard - actual;

                    SupplyDeficitRecord record = new SupplyDeficitRecord();
                    record.setBarracksId(barracksId);
                    record.setDeficitDate(date);
                    record.setFoodCategory(category);
                    record.setFoodName(getFoodName(category, random));
                    record.setStandardRationG(BigDecimal.valueOf(standard).setScale(2, RoundingMode.HALF_UP));
                    record.setActualDeliveredG(BigDecimal.valueOf(actual).setScale(2, RoundingMode.HALF_UP));
                    record.setDeficitAmountG(BigDecimal.valueOf(deficitAmount).setScale(2, RoundingMode.HALF_UP));
                    record.setDeficitRate(BigDecimal.valueOf(deficitRateValue).setScale(4, RoundingMode.HALF_UP));
                    record.setWeatherCondition(weather);
                    record.setSupplyRoute(route);
                    record.setCreatedAt(LocalDateTime.now());
                    allRecords.add(record);
                }
            }
        }

        if (!allRecords.isEmpty()) {
            log.info("保存模拟短缺数据 {} 条", allRecords.size());
            allRecords = deficitRecordRepository.saveAll(allRecords);
        }
        return allRecords;
    }

    private String pickWeather(LocalDate date, String barracksCategory, ThreadLocalRandom random) {
        int month = date.getMonthValue();
        double[] weights;

        switch (barracksCategory) {
            case "山区":
                if (month >= 11 || month <= 2) {
                    weights = new double[]{0.15, 0.15, 0.10, 0.40, 0.05, 0.10, 0.05};
                } else if (month >= 6 && month <= 8) {
                    weights = new double[]{0.20, 0.20, 0.30, 0.02, 0.08, 0.10, 0.10};
                } else {
                    weights = new double[]{0.25, 0.25, 0.15, 0.10, 0.05, 0.15, 0.05};
                }
                break;
            case "边境":
                weights = new double[]{0.25, 0.20, 0.15, 0.15, 0.10, 0.10, 0.05};
                break;
            default:
                if (month >= 6 && month <= 9) {
                    weights = new double[]{0.30, 0.25, 0.25, 0.02, 0.03, 0.05, 0.10};
                } else {
                    weights = new double[]{0.40, 0.30, 0.10, 0.08, 0.02, 0.08, 0.02};
                }
                break;
        }
        return weightedPick(WEATHER_CONDITIONS, weights, random);
    }

    private String pickRoute(String barracksCategory, ThreadLocalRandom random) {
        double[] weights;
        switch (barracksCategory) {
            case "山区":
                weights = new double[]{0.15, 0.40, 0.15, 0.30};
                break;
            case "边境":
                weights = new double[]{0.40, 0.10, 0.20, 0.30};
                break;
            default:
                weights = new double[]{0.30, 0.20, 0.35, 0.15};
                break;
        }
        return weightedPick(SUPPLY_ROUTES, weights, random);
    }

    private String weightedPick(String[] options, double[] weights, ThreadLocalRandom random) {
        double total = 0;
        for (double w : weights) total += w;
        double r = random.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < options.length; i++) {
            cumulative += weights[i];
            if (r <= cumulative) return options[i];
        }
        return options[options.length - 1];
    }

    private String getFoodName(String category, ThreadLocalRandom random) {
        Map<String, String[]> foodMap = new HashMap<>();
        foodMap.put("蔬菜类", new String[]{"大白菜", "萝卜", "土豆", "西红柿", "黄瓜", "青菜"});
        foodMap.put("肉类", new String[]{"猪肉", "牛肉", "羊肉", "鸡肉", "鸭肉"});
        foodMap.put("豆制品", new String[]{"豆腐", "豆干", "豆浆", "腐竹", "豆芽"});
        foodMap.put("乳制品", new String[]{"牛奶", "酸奶", "奶粉", "奶酪"});
        foodMap.put("谷物类", new String[]{"大米", "面粉", "小米", "玉米", "挂面"});
        foodMap.put("油脂类", new String[]{"花生油", "菜籽油", "大豆油", "猪油"});
        foodMap.put("蛋类", new String[]{"鸡蛋", "鸭蛋", "松花蛋"});
        foodMap.put("水产类", new String[]{"草鱼", "鲤鱼", "带鱼", "虾", "海带"});

        String[] names = foodMap.getOrDefault(category, new String[]{"食品"});
        return names[random.nextInt(names.length)];
    }
}
