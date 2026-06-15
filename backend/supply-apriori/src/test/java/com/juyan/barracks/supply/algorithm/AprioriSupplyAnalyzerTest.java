package com.juyan.barracks.supply.algorithm;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AprioriSupplyAnalyzerTest {

    private List<Set<String>> createTestTransactions() {
        List<Set<String>> transactions = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            Set<String> t = new HashSet<>();
            t.add("天气_暴雨");
            t.add("短缺_蔬菜类");
            if (i < 3) {
                t.add("兵营_一号营");
            }
            transactions.add(t);
        }

        for (int i = 0; i < 5; i++) {
            Set<String> t = new HashSet<>();
            t.add("路线_西线");
            t.add("短缺_豆制品");
            if (i < 2) {
                t.add("兵营_二号营");
            }
            transactions.add(t);
        }

        assertEquals(11, transactions.size(), "测试事务总数应为11条");
        return transactions;
    }

    private Set<String> itemSetOf(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    private boolean containsItemSet(List<AssociationRuleResult.ItemSet> itemSets,
                                     Set<String> expectedItems) {
        for (AssociationRuleResult.ItemSet is : itemSets) {
            if (is.items.equals(expectedItems)) {
                return true;
            }
        }
        return false;
    }

    private AssociationRuleResult.ItemSet findItemSet(
            List<AssociationRuleResult.ItemSet> itemSets, Set<String> expectedItems) {
        for (AssociationRuleResult.ItemSet is : itemSets) {
            if (is.items.equals(expectedItems)) {
                return is;
            }
        }
        return null;
    }

    @Test
    void testEmptyTransactions() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();
        List<Set<String>> emptyTransactions = new ArrayList<>();

        List<AssociationRuleResult.ItemSet> frequentItemSets =
                analyzer.findFrequentItemSets(emptyTransactions, 0.3);

        assertNotNull(frequentItemSets, "空事务的频繁项集结果不应为空");
        assertTrue(frequentItemSets.isEmpty(),
                "空事务列表应返回空频繁项集");

        List<AssociationRuleResult> rules =
                analyzer.generateAssociationRules(frequentItemSets, emptyTransactions, 0.5);

        assertNotNull(rules, "空事务的关联规则结果不应为空");
        assertTrue(rules.isEmpty(),
                "空事务列表应返回空关联规则");
    }

    @Test
    void testFindFrequentItemSets() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();
        List<Set<String>> transactions = createTestTransactions();
        double minSupport = 0.30;

        List<AssociationRuleResult.ItemSet> frequentItemSets =
                analyzer.findFrequentItemSets(transactions, minSupport);

        assertNotNull(frequentItemSets, "频繁项集结果不应为空");
        assertFalse(frequentItemSets.isEmpty(),
                "应能挖掘出至少一些频繁项集");

        int totalTransactions = transactions.size();
        int minSupportCount = (int) Math.ceil(minSupport * totalTransactions);

        assertTrue(containsItemSet(frequentItemSets, itemSetOf("天气_暴雨")),
                "单项集 {天气_暴雨} 应为频繁项集");
        assertTrue(containsItemSet(frequentItemSets, itemSetOf("短缺_蔬菜类")),
                "单项集 {短缺_蔬菜类} 应为频繁项集");
        assertTrue(containsItemSet(frequentItemSets, itemSetOf("路线_西线")),
                "单项集 {路线_西线} 应为频繁项集");
        assertTrue(containsItemSet(frequentItemSets, itemSetOf("短缺_豆制品")),
                "单项集 {短缺_豆制品} 应为频繁项集");

        assertTrue(containsItemSet(frequentItemSets, itemSetOf("天气_暴雨", "短缺_蔬菜类")),
                "2项集 {天气_暴雨, 短缺_蔬菜类} 应为频繁项集 (出现6次)");
        assertTrue(containsItemSet(frequentItemSets, itemSetOf("路线_西线", "短缺_豆制品")),
                "2项集 {路线_西线, 短缺_豆制品} 应为频繁项集 (出现5次)");

        AssociationRuleResult.ItemSet stormVegItemSet =
                findItemSet(frequentItemSets, itemSetOf("天气_暴雨", "短缺_蔬菜类"));
        assertNotNull(stormVegItemSet);
        assertEquals(6, stormVegItemSet.count,
                "{天气_暴雨, 短缺_蔬菜类} 应出现6次");
        assertEquals(6.0 / totalTransactions, stormVegItemSet.support, 0.001,
                "{天气_暴雨, 短缺_蔬菜类} 支持度应为6/11");

        for (AssociationRuleResult.ItemSet itemSet : frequentItemSets) {
            assertTrue(itemSet.count >= minSupportCount,
                    "频繁项集 " + itemSet.items + " 的出现次数(" + itemSet.count +
                            ")应≥最小支持度计数(" + minSupportCount + ")");
            assertTrue(itemSet.support >= minSupport - 0.001,
                    "频繁项集 " + itemSet.items + " 的支持度(" +
                            String.format("%.4f", itemSet.support) +
                            ")应≥最小支持度(" + minSupport + ")");
        }
    }

    @Test
    void testAssociationRuleLift() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();
        List<Set<String>> transactions = createTestTransactions();

        List<AssociationRuleResult.ItemSet> frequentItemSets =
                analyzer.findFrequentItemSets(transactions, 0.30);

        List<AssociationRuleResult> rules =
                analyzer.generateAssociationRules(frequentItemSets, transactions, 0.5);

        assertNotNull(rules, "关联规则结果不应为空");
        assertFalse(rules.isEmpty(), "应生成至少一些关联规则");

        boolean foundStormVegRule = false;
        boolean foundWestSoyRule = false;

        for (AssociationRuleResult rule : rules) {
            assertNotNull(rule.getAntecedent(), "规则前件不应为空");
            assertNotNull(rule.getConsequent(), "规则后件不应为空");
            assertFalse(rule.getAntecedent().isEmpty(), "规则前件不应为空集");
            assertFalse(rule.getConsequent().isEmpty(), "规则后件不应为空集");
            assertNotNull(rule.getSupport(), "规则支持度不应为空");
            assertNotNull(rule.getConfidence(), "规则置信度不应为空");
            assertNotNull(rule.getLift(), "规则提升度不应为空");
            assertNotNull(rule.getCount(), "规则计数不应为空");

            assertTrue(rule.getConfidence().compareTo(BigDecimal.valueOf(0.5)) >= 0,
                    "规则置信度应≥minConfidence=0.5，实际: " + rule.getConfidence());

            Set<String> ant = new HashSet<>(rule.getAntecedent());
            Set<String> cons = new HashSet<>(rule.getConsequent());

            if (ant.contains("天气_暴雨") && cons.contains("短缺_蔬菜类")) {
                foundStormVegRule = true;
                assertTrue(rule.getLift().compareTo(BigDecimal.ONE) > 0,
                        "强关联规则 {天气_暴雨} → {短缺_蔬菜类} 的Lift应>1，实际: " +
                                rule.getLift());
            }
            if (ant.contains("短缺_蔬菜类") && cons.contains("天气_暴雨")) {
                foundStormVegRule = true;
                assertTrue(rule.getLift().compareTo(BigDecimal.ONE) > 0,
                        "强关联规则 {短缺_蔬菜类} → {天气_暴雨} 的Lift应>1，实际: " +
                                rule.getLift());
            }
            if (ant.contains("路线_西线") && cons.contains("短缺_豆制品")) {
                foundWestSoyRule = true;
                assertTrue(rule.getLift().compareTo(BigDecimal.ONE) > 0,
                        "强关联规则 {路线_西线} → {短缺_豆制品} 的Lift应>1，实际: " +
                                rule.getLift());
            }
            if (ant.contains("短缺_豆制品") && cons.contains("路线_西线")) {
                foundWestSoyRule = true;
                assertTrue(rule.getLift().compareTo(BigDecimal.ONE) > 0,
                        "强关联规则 {短缺_豆制品} → {路线_西线} 的Lift应>1，实际: " +
                                rule.getLift());
            }
        }

        assertTrue(foundStormVegRule,
                "应能找到 {天气_暴雨} ↔ {短缺_蔬菜类} 之间的强关联规则");
        assertTrue(foundWestSoyRule,
                "应能找到 {路线_西线} ↔ {短缺_豆制品} 之间的强关联规则");
    }

    @Test
    void testMinSupportFilter() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();
        List<Set<String>> transactions = createTestTransactions();
        int totalTransactions = transactions.size();

        double highMinSupport = 0.70;
        List<AssociationRuleResult.ItemSet> highSupportItemSets =
                analyzer.findFrequentItemSets(transactions, highMinSupport);

        assertNotNull(highSupportItemSets, "高支持度频繁项集结果不应为空");

        int highMinSupportCount = (int) Math.ceil(highMinSupport * totalTransactions);
        for (AssociationRuleResult.ItemSet itemSet : highSupportItemSets) {
            assertTrue(itemSet.count >= highMinSupportCount,
                    "高支持度阈值下，项集 " + itemSet.items +
                            " 的计数(" + itemSet.count +
                            ")应≥" + highMinSupportCount);
        }

        double lowMinSupport = 0.10;
        List<AssociationRuleResult.ItemSet> lowSupportItemSets =
                analyzer.findFrequentItemSets(transactions, lowMinSupport);

        assertNotNull(lowSupportItemSets, "低支持度频繁项集结果不应为空");
        assertTrue(lowSupportItemSets.size() >= highSupportItemSets.size(),
                "降低支持度阈值应产生不少于原来的频繁项集数。" +
                        "低支持度: " + lowSupportItemSets.size() +
                        "，高支持度: " + highSupportItemSets.size());

        double veryHighMinSupport = 0.95;
        List<AssociationRuleResult.ItemSet> veryHighSupportItemSets =
                analyzer.findFrequentItemSets(transactions, veryHighMinSupport);

        for (AssociationRuleResult.ItemSet itemSet : veryHighSupportItemSets) {
            double expectedSupport = (double) itemSet.count / totalTransactions;
            assertTrue(expectedSupport >= veryHighMinSupport - 0.001,
                    "极高支持度下项集 " + itemSet.items +
                            " 的支持度(" + String.format("%.4f", expectedSupport) +
                            ")应≥" + veryHighMinSupport);
        }

        List<Set<String>> singleRareItemTransactions = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Set<String> t = new HashSet<>();
            t.add("常见项_A");
            if (i == 0) {
                t.add("极其罕见项");
            }
            singleRareItemTransactions.add(t);
        }

        List<AssociationRuleResult.ItemSet> rareItemResult =
                analyzer.findFrequentItemSets(singleRareItemTransactions, 0.05);

        assertFalse(containsItemSet(rareItemResult, itemSetOf("极其罕见项")),
                "仅出现1次的项(支持度1%)不应被minSupport=5%的阈值挖掘出来");
        assertTrue(containsItemSet(rareItemResult, itemSetOf("常见项_A")),
                "出现100次的常见项应被挖掘出来");
    }

    @Test
    void testMeatShortageHighConfidence() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();
        List<Set<String>> transactions = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            Set<String> t = new HashSet<>();
            t.add("天气_大雪");
            t.add("短缺_肉类");
            transactions.add(t);
        }

        for (int i = 0; i < 5; i++) {
            Set<String> t = new HashSet<>();
            t.add("路线_北线");
            t.add("短缺_肉类");
            transactions.add(t);
        }

        for (int i = 0; i < 3; i++) {
            Set<String> t = new HashSet<>();
            t.add("天气_暴雨");
            t.add("短缺_蔬菜类");
            transactions.add(t);
        }

        for (int i = 0; i < 2; i++) {
            Set<String> t = new HashSet<>();
            t.add("天气_晴");
            t.add("短缺_豆制品");
            transactions.add(t);
        }

        assertEquals(20, transactions.size(), "测试事务总数应为20条");

        double minSupport = 0.20;
        double minConfidence = 0.6;

        List<AssociationRuleResult.ItemSet> frequentItemSets =
                analyzer.findFrequentItemSets(transactions, minSupport);

        assertNotNull(frequentItemSets, "频繁项集结果不应为空");
        assertFalse(frequentItemSets.isEmpty(), "应能挖掘出至少一些频繁项集");

        assertTrue(containsItemSet(frequentItemSets, itemSetOf("短缺_肉类")),
                "\"短缺_肉类\"应是频繁项集");

        List<AssociationRuleResult.ItemSet> topItems = analyzer.getTopFrequentItems(frequentItemSets, 3);
        assertNotNull(topItems, "Top频繁项集不应为空");
        assertTrue(topItems.size() <= 3, "Top N结果不应超过3个");
        assertEquals("短缺_肉类", topItems.get(0).items.iterator().next(),
                "最频繁的1-项集应为短缺_肉类");

        List<AssociationRuleResult> rules =
                analyzer.generateAssociationRules(frequentItemSets, transactions, minConfidence);

        assertNotNull(rules, "关联规则结果不应为空");
        assertFalse(rules.isEmpty(), "应生成至少一些关联规则");

        List<AssociationRuleResult> meatConsequentRules =
                analyzer.getRulesByConsequent(rules, "短缺_肉类");
        assertNotNull(meatConsequentRules, "按后件筛选的规则列表不应为空");
        assertFalse(meatConsequentRules.isEmpty(), "应找到以后件为短缺_肉类的规则");

        List<AssociationRuleResult> snowAntecedentRules =
                analyzer.getRulesByAntecedent(rules, "天气_大雪");
        assertNotNull(snowAntecedentRules, "按前件筛选的规则列表不应为空");

        boolean foundSnowMeatRule = false;
        AssociationRuleResult snowMeatRule = null;
        for (AssociationRuleResult rule : rules) {
            Set<String> ant = new HashSet<>(rule.getAntecedent());
            Set<String> cons = new HashSet<>(rule.getConsequent());
            if (ant.contains("天气_大雪") && cons.contains("短缺_肉类") && ant.size() == 1) {
                foundSnowMeatRule = true;
                snowMeatRule = rule;
                break;
            }
        }

        assertTrue(foundSnowMeatRule,
                "应能找到 {天气_大雪} → {短缺_肉类} 的规则");

        assertNotNull(snowMeatRule);
        assertTrue(snowMeatRule.getConfidence().compareTo(BigDecimal.valueOf(0.7)) >= 0,
                "规则 {天气_大雪} → {短缺_肉类} 的置信度应≥0.7，实际: " +
                        snowMeatRule.getConfidence());

        assertTrue(snowMeatRule.getLift().compareTo(BigDecimal.ONE) > 0,
                "规则 {天气_大雪} → {短缺_肉类} 的Lift应>1，实际: " +
                        snowMeatRule.getLift());
    }

    @Test
    void testLowSupportGeneratesManyRules() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();
        List<Set<String>> transactions = new ArrayList<>();

        String[] items = {
                "A", "B", "C", "D", "E", "F", "G", "H",
                "天气_晴", "天气_雨", "天气_雪",
                "短缺_肉类", "短缺_蔬菜", "短缺_豆制品",
                "路线_东", "路线_西", "路线_南", "路线_北"
        };

        Random random = new Random(42);
        for (int i = 0; i < 50; i++) {
            Set<String> t = new HashSet<>();
            int numItems = 2 + random.nextInt(5);
            List<String> shuffled = new ArrayList<>(Arrays.asList(items));
            Collections.shuffle(shuffled, random);
            for (int j = 0; j < numItems && j < shuffled.size(); j++) {
                t.add(shuffled.get(j));
            }
            transactions.add(t);
        }

        assertEquals(50, transactions.size(), "测试事务总数应为50条");

        double highSupport = 0.30;
        double lowSupport = 0.05;

        List<AssociationRuleResult.ItemSet> highSupportItemSets =
                analyzer.findFrequentItemSets(transactions, highSupport);
        List<AssociationRuleResult> highSupportRules =
                analyzer.generateAssociationRules(highSupportItemSets, transactions, 0.5);

        List<AssociationRuleResult.ItemSet> lowSupportItemSets =
                analyzer.findFrequentItemSets(transactions, lowSupport);
        List<AssociationRuleResult> lowSupportRules =
                analyzer.generateAssociationRules(lowSupportItemSets, transactions, 0.5);

        int highRuleCount = analyzer.getRuleCount(highSupportRules);
        int lowRuleCount = analyzer.getRuleCount(lowSupportRules);

        assertTrue(lowRuleCount > highRuleCount * 2,
                "低支持度的规则数应显著多于高支持度（至少2倍以上）。" +
                        "低支持度: " + lowRuleCount + "，高支持度: " + highRuleCount);

        int high2ItemSets = analyzer.countItemSetsBySize(highSupportItemSets, 2);
        int low2ItemSets = analyzer.countItemSetsBySize(lowSupportItemSets, 2);
        assertTrue(low2ItemSets >= high2ItemSets,
                "低支持度的2项集数量应不少于高支持度。" +
                        "低: " + low2ItemSets + "，高: " + high2ItemSets);

        int high3ItemSets = analyzer.countItemSetsBySize(highSupportItemSets, 3);
        int low3ItemSets = analyzer.countItemSetsBySize(lowSupportItemSets, 3);

        assertTrue(lowSupportItemSets.size() > highSupportItemSets.size(),
                "低支持度的频繁项集总数应更多。低: " + lowSupportItemSets.size() +
                        "，高: " + highSupportItemSets.size());

        if (high3ItemSets == 0) {
            assertTrue(low3ItemSets > 0 || lowSupportItemSets.size() > highSupportItemSets.size(),
                    "支持度降低时，应能挖掘出更高阶的项集或更多项集");
        } else {
            assertTrue(low3ItemSets >= high3ItemSets,
                    "低支持度的3项集数量应不少于高支持度");
        }
    }

    @Test
    void testEmptyTransactionsReturnsEmptySet() {
        AprioriSupplyAnalyzer analyzer = new AprioriSupplyAnalyzer();

        List<AssociationRuleResult.ItemSet> nullResult =
                analyzer.findFrequentItemSets(null, 0.3);
        assertNotNull(nullResult, "null事务列表的频繁项集结果不应为null");
        assertTrue(nullResult.isEmpty(), "null事务列表应返回空频繁项集");

        List<AssociationRuleResult> nullRules =
                analyzer.generateAssociationRules(null, null, 0.5);
        assertNotNull(nullRules, "null输入的关联规则结果不应为null");
        assertTrue(nullRules.isEmpty(), "null输入应返回空关联规则");

        List<Set<String>> emptyTransactions = new ArrayList<>();
        List<AssociationRuleResult.ItemSet> emptyResult =
                analyzer.findFrequentItemSets(emptyTransactions, 0.3);
        assertNotNull(emptyResult, "空列表的频繁项集结果不应为null");
        assertTrue(emptyResult.isEmpty(), "空列表应返回空频繁项集");

        List<Set<String>> transactionsWithEmptySets = new ArrayList<>();
        transactionsWithEmptySets.add(new HashSet<>());
        transactionsWithEmptySets.add(itemSetOf("天气_晴"));
        transactionsWithEmptySets.add(new HashSet<>());

        List<AssociationRuleResult.ItemSet> emptySetResult =
                analyzer.findFrequentItemSets(transactionsWithEmptySets, 0.3);
        assertNotNull(emptySetResult, "包含空集的事务结果不应为null");

        List<AssociationRuleResult.ItemSet> zeroSupportResult =
                analyzer.findFrequentItemSets(createTestTransactions(), 0.0);
        assertNotNull(zeroSupportResult, "minSupport=0时结果不应为null");
        assertFalse(zeroSupportResult.isEmpty(), "minSupport=0钳制后应能挖掘出项集");

        List<AssociationRuleResult.ItemSet> overOneSupportResult =
                analyzer.findFrequentItemSets(createTestTransactions(), 2.0);
        assertNotNull(overOneSupportResult, "minSupport>1时结果不应为null");

        List<AssociationRuleResult.ItemSet> negativeSupportResult =
                analyzer.findFrequentItemSets(createTestTransactions(), -0.5);
        assertNotNull(negativeSupportResult, "minSupport<0时结果不应为null");
        assertFalse(negativeSupportResult.isEmpty(), "minSupport<0钳制后应能挖掘出项集");

        List<AssociationRuleResult.ItemSet> freqItems =
                analyzer.findFrequentItemSets(createTestTransactions(), 0.3);
        List<AssociationRuleResult> zeroConfidenceRules =
                analyzer.generateAssociationRules(freqItems, createTestTransactions(), 0.0);
        assertNotNull(zeroConfidenceRules, "minConfidence=0时结果不应为null");

        List<AssociationRuleResult> overOneConfidenceRules =
                analyzer.generateAssociationRules(freqItems, createTestTransactions(), 2.0);
        assertNotNull(overOneConfidenceRules, "minConfidence>1时结果不应为null");

        assertEquals(0, analyzer.getTotalItemCount(null),
                "null事务的总项数应为0");
        assertEquals(0, analyzer.getTotalItemCount(new ArrayList<>()),
                "空事务列表的总项数应为0");

        assertTrue(analyzer.getAllUniqueItems(null).isEmpty(),
                "null事务的唯一项集合应为空");
        assertTrue(analyzer.getAllUniqueItems(new ArrayList<>()).isEmpty(),
                "空事务列表的唯一项集合应为空");

        assertTrue(analyzer.getItemFrequencyMap(null).isEmpty(),
                "null事务的频率Map应为空");
        assertTrue(analyzer.getItemFrequencyMap(new ArrayList<>()).isEmpty(),
                "空事务列表的频率Map应为空");

        assertEquals(0, analyzer.countItemSetsBySize(null, 1),
                "null项集列表的计数应为0");
        assertEquals(0, analyzer.getRuleCount(null),
                "null规则列表的计数应为0");
    }
}
