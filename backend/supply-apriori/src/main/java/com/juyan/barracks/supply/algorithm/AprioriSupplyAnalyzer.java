package com.juyan.barracks.supply.algorithm;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class AprioriSupplyAnalyzer {

    public static final double DEFAULT_MIN_LIFT = 1.5;

    public List<AssociationRuleResult.ItemSet> findFrequentItemSets(List<Set<String>> transactions, double minSupport) {
        if (transactions == null) {
            return new ArrayList<>();
        }
        int totalTransactions = transactions.size();
        if (totalTransactions == 0) {
            return new ArrayList<>();
        }

        minSupport = clampSupport(minSupport);
        int minSupportCount = (int) Math.ceil(minSupport * totalTransactions);
        log.debug("总事务数: {}, 最小支持度计数: {}", totalTransactions, minSupportCount);

        List<AssociationRuleResult.ItemSet> allFrequentItemSets = new ArrayList<>();

        Map<Set<String>, Integer> l1Itemsets = new HashMap<>();
        for (Set<String> transaction : transactions) {
            for (String item : transaction) {
                Set<String> singleItem = new HashSet<>(Collections.singletonList(item));
                l1Itemsets.merge(singleItem, 1, Integer::sum);
            }
        }

        List<AssociationRuleResult.ItemSet> l1 = new ArrayList<>();
        for (Map.Entry<Set<String>, Integer> entry : l1Itemsets.entrySet()) {
            if (entry.getValue() >= minSupportCount) {
                double support = (double) entry.getValue() / totalTransactions;
                l1.add(new AssociationRuleResult.ItemSet(entry.getKey(), entry.getValue(), support));
            }
        }

        if (l1.isEmpty()) {
            log.debug("L1 频繁项集为空，无法继续挖掘");
            return allFrequentItemSets;
        }

        allFrequentItemSets.addAll(l1);
        log.debug("L1 频繁项集数量: {}", l1.size());

        List<AssociationRuleResult.ItemSet> prevLk = l1;
        int k = 2;

        while (!prevLk.isEmpty()) {
            List<Set<String>> ck = aprioriGen(prevLk, k);
            if (ck.isEmpty()) {
                break;
            }

            Map<Set<String>, Integer> ckCounts = new HashMap<>();
            for (Set<String> candidate : ck) {
                for (Set<String> transaction : transactions) {
                    if (transaction.containsAll(candidate)) {
                        ckCounts.merge(candidate, 1, Integer::sum);
                    }
                }
            }

            List<AssociationRuleResult.ItemSet> lk = new ArrayList<>();
            for (Map.Entry<Set<String>, Integer> entry : ckCounts.entrySet()) {
                if (entry.getValue() >= minSupportCount) {
                    double support = (double) entry.getValue() / totalTransactions;
                    lk.add(new AssociationRuleResult.ItemSet(entry.getKey(), entry.getValue(), support));
                }
            }

            if (lk.isEmpty()) {
                break;
            }

            allFrequentItemSets.addAll(lk);
            log.debug("L{} 频繁项集数量: {}", k, lk.size());

            prevLk = lk;
            k++;
        }

        log.debug("总共挖掘出 {} 个频繁项集", allFrequentItemSets.size());
        return allFrequentItemSets;
    }

    private List<Set<String>> aprioriGen(List<AssociationRuleResult.ItemSet> prevLk, int k) {
        List<Set<String>> candidates = new ArrayList<>();
        int prevSize = prevLk.size();

        for (int i = 0; i < prevSize; i++) {
            for (int j = i + 1; j < prevSize; j++) {
                Set<String> itemset1 = new TreeSet<>(prevLk.get(i).items);
                Set<String> itemset2 = new TreeSet<>(prevLk.get(j).items);

                List<String> list1 = new ArrayList<>(itemset1);
                List<String> list2 = new ArrayList<>(itemset2);

                boolean canJoin = true;
                for (int m = 0; m < k - 2; m++) {
                    if (!list1.get(m).equals(list2.get(m))) {
                        canJoin = false;
                        break;
                    }
                }

                if (canJoin) {
                    Set<String> candidate = new HashSet<>(itemset1);
                    candidate.addAll(itemset2);

                    if (candidate.size() == k && hasInfrequentSubset(candidate, prevLk, k - 1)) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        return candidates;
    }

    private boolean allSubsetsFrequent(Set<String> candidate, List<AssociationRuleResult.ItemSet> prevLk, int subsetSize) {
        Set<Set<String>> prevItemSets = prevLk.stream()
                .map(itemSet -> new HashSet<>(itemSet.items))
                .collect(Collectors.toSet());

        List<String> items = new ArrayList<>(candidate);
        List<List<String>> subsets = generateSubsets(items, subsetSize);
        for (List<String> subset : subsets) {
            if (!prevItemSets.contains(new HashSet<>(subset))) {
                return false;
            }
        }
        return true;
    }

    private List<List<String>> generateSubsets(List<String> items, int size) {
        List<List<String>> subsets = new ArrayList<>();
        generateSubsetsHelper(items, size, 0, new ArrayList<>(), subsets);
        return subsets;
    }

    private void generateSubsetsHelper(List<String> items, int size, int start,
                                       List<String> current, List<List<String>> subsets) {
        if (current.size() == size) {
            subsets.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            generateSubsetsHelper(items, size, i + 1, current, subsets);
            current.remove(current.size() - 1);
        }
    }

    public List<AssociationRuleResult> generateAssociationRules(
            List<AssociationRuleResult.ItemSet> frequentItemSets,
            List<Set<String>> transactions,
            double minConfidence) {
        return generateAssociationRules(frequentItemSets, transactions, minConfidence, DEFAULT_MIN_LIFT);
    }

    public List<AssociationRuleResult> generateAssociationRules(
            List<AssociationRuleResult.ItemSet> frequentItemSets,
            List<Set<String>> transactions,
            double minConfidence,
            double minLift) {

        List<AssociationRuleResult> allRules = new ArrayList<>();
        if (frequentItemSets == null || transactions == null) {
            return allRules;
        }
        int totalTransactions = transactions.size();

        if (totalTransactions == 0) {
            return allRules;
        }

        minConfidence = clampConfidence(minConfidence);
        minLift = Math.max(0.001, minLift);

        Map<Set<String>, AssociationRuleResult.ItemSet> itemSetMap = new HashMap<>();
        for (AssociationRuleResult.ItemSet itemSet : frequentItemSets) {
            itemSetMap.put(new HashSet<>(itemSet.items), itemSet);
        }

        for (AssociationRuleResult.ItemSet frequentItemSet : frequentItemSets) {
            if (frequentItemSet.items.size() < 2) {
                continue;
            }

            List<String> items = new ArrayList<>(frequentItemSet.items);
            generateRulesFromItemSet(frequentItemSet, items, transactions,
                    minConfidence, totalTransactions, itemSetMap, allRules);
        }

        List<AssociationRuleResult> filteredRules = new ArrayList<>();
        int spuriousCount = 0;
        for (AssociationRuleResult rule : allRules) {
            if (rule.getLift() != null && rule.getLift().doubleValue() >= minLift) {
                filteredRules.add(rule);
            } else {
                spuriousCount++;
            }
        }

        filteredRules.sort((r1, r2) -> {
            int cmp = r2.getLift().compareTo(r1.getLift());
            if (cmp != 0) return cmp;
            cmp = r2.getConfidence().compareTo(r1.getConfidence());
            if (cmp != 0) return cmp;
            return r2.getSupport().compareTo(r1.getSupport());
        });

        double spuriousReduction = allRules.isEmpty() ? 0.0 :
                (double) spuriousCount / allRules.size();

        log.debug("规则过滤完成: 原始规则={}, Lift<{}剔除={}, 保留={}, 虚假规则减少率={:.1f}%",
                allRules.size(), minLift, spuriousCount, filteredRules.size(),
                spuriousReduction * 100);

        return filteredRules;
    }

    private void generateRulesFromItemSet(
            AssociationRuleResult.ItemSet itemSet,
            List<String> items,
            List<Set<String>> transactions,
            double minConfidence,
            int totalTransactions,
            Map<Set<String>, AssociationRuleResult.ItemSet> itemSetMap,
            List<AssociationRuleResult> rules) {

        int n = items.size();
        int bothCount = itemSet.count;
        double supportAB = (double) bothCount / totalTransactions;

        for (int mask = 1; mask < (1 << n) - 1; mask++) {
            List<String> antecedent = new ArrayList<>();
            List<String> consequent = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    antecedent.add(items.get(i));
                } else {
                    consequent.add(items.get(i));
                }
            }

            Set<String> antecedentSet = new HashSet<>(antecedent);
            AssociationRuleResult.ItemSet antecedentItemSet = itemSetMap.get(antecedentSet);

            if (antecedentItemSet == null) {
                continue;
            }

            int antecedentCount = antecedentItemSet.count;
            double confidence = (double) bothCount / antecedentCount;

            if (confidence < minConfidence) {
                continue;
            }

            Set<String> consequentSet = new HashSet<>(consequent);
            AssociationRuleResult.ItemSet consequentItemSet = itemSetMap.get(consequentSet);

            if (consequentItemSet == null) {
                continue;
            }

            int consequentCount = consequentItemSet.count;
            double supportB = (double) consequentCount / totalTransactions;

            double lift = supportB > 0 ? confidence / supportB : 0.0;
            double leverage = supportAB - (antecedentItemSet.support * supportB);

            String description = generateRuleDescription(antecedent, consequent, lift, confidence);

            AssociationRuleResult rule = AssociationRuleResult.builder()
                    .antecedent(new ArrayList<>(antecedent))
                    .consequent(new ArrayList<>(consequent))
                    .support(BigDecimal.valueOf(supportAB).setScale(4, RoundingMode.HALF_UP))
                    .confidence(BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP))
                    .lift(BigDecimal.valueOf(lift).setScale(4, RoundingMode.HALF_UP))
                    .leverage(BigDecimal.valueOf(leverage).setScale(4, RoundingMode.HALF_UP))
                    .count(bothCount)
                    .description(description)
                    .build();

            rules.add(rule);
        }
    }

    private String generateRuleDescription(List<String> antecedent, List<String> consequent,
                                            double lift, double confidence) {
        StringBuilder sb = new StringBuilder();

        List<String> antParts = antecedent.stream()
                .map(this::translateItem)
                .collect(Collectors.toList());
        List<String> consParts = consequent.stream()
                .map(this::translateItem)
                .collect(Collectors.toList());

        sb.append("当");
        sb.append(String.join(" + ", antParts));
        sb.append("时，");

        sb.append(String.join(" 且 ", consParts));

        if (lift > 1.0) {
            sb.append(String.format("的概率提升%.1f倍", lift));
        } else if (lift < 1.0) {
            sb.append(String.format("的概率降低%.1f倍", 1.0 / lift));
        } else {
            sb.append("的概率无明显变化");
        }

        sb.append(String.format("（置信度%.1f%%）", confidence * 100));

        return sb.toString();
    }

    private String translateItem(String item) {
        if (item.startsWith("短缺_")) {
            return item.replace("短缺_", "") + "短缺";
        }
        if (item.startsWith("天气_")) {
            return item.replace("天气_", "") + "天气";
        }
        if (item.startsWith("路线_")) {
            return item.replace("路线_", "") + "路线运输";
        }
        if (item.startsWith("兵营_")) {
            return item.replace("兵营_", "") + "兵营";
        }
        return item;
    }

    private double clampSupport(double minSupport) {
        if (minSupport < 0.001) {
            return 0.001;
        }
        if (minSupport > 1.0) {
            return 1.0;
        }
        return minSupport;
    }

    private double clampConfidence(double minConfidence) {
        if (minConfidence < 0.001) {
            return 0.001;
        }
        if (minConfidence > 1.0) {
            return 1.0;
        }
        return minConfidence;
    }

    public List<AssociationRuleResult.ItemSet> getTopFrequentItems(
            List<AssociationRuleResult.ItemSet> frequentItemSets, int topN) {
        if (frequentItemSets == null || frequentItemSets.isEmpty()) {
            return new ArrayList<>();
        }
        List<AssociationRuleResult.ItemSet> oneItemSets = frequentItemSets.stream()
                .filter(itemSet -> itemSet.items.size() == 1)
                .sorted((a, b) -> Integer.compare(b.count, a.count))
                .limit(topN)
                .collect(Collectors.toList());
        return oneItemSets;
    }

    public List<AssociationRuleResult> getRulesByConsequent(
            List<AssociationRuleResult> rules, String consequentItem) {
        if (rules == null || rules.isEmpty() || consequentItem == null) {
            return new ArrayList<>();
        }
        return rules.stream()
                .filter(rule -> rule.getConsequent().contains(consequentItem))
                .collect(Collectors.toList());
    }

    public List<AssociationRuleResult> getRulesByAntecedent(
            List<AssociationRuleResult> rules, String antecedentItem) {
        if (rules == null || rules.isEmpty() || antecedentItem == null) {
            return new ArrayList<>();
        }
        return rules.stream()
                .filter(rule -> rule.getAntecedent().contains(antecedentItem))
                .collect(Collectors.toList());
    }

    public int countItemSetsBySize(List<AssociationRuleResult.ItemSet> frequentItemSets, int k) {
        if (frequentItemSets == null || frequentItemSets.isEmpty()) {
            return 0;
        }
        return (int) frequentItemSets.stream()
                .filter(itemSet -> itemSet.items.size() == k)
                .count();
    }

    public int getRuleCount(List<AssociationRuleResult> rules) {
        if (rules == null) {
            return 0;
        }
        return rules.size();
    }

    public int getTotalItemCount(List<Set<String>> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Set<String> transaction : transactions) {
            if (transaction != null) {
                total += transaction.size();
            }
        }
        return total;
    }

    public Set<String> getAllUniqueItems(List<Set<String>> transactions) {
        Set<String> uniqueItems = new HashSet<>();
        if (transactions == null || transactions.isEmpty()) {
            return uniqueItems;
        }
        for (Set<String> transaction : transactions) {
            if (transaction != null) {
                uniqueItems.addAll(transaction);
            }
        }
        return uniqueItems;
    }

    public Map<String, Integer> getItemFrequencyMap(List<Set<String>> transactions) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        if (transactions == null || transactions.isEmpty()) {
            return frequencyMap;
        }
        for (Set<String> transaction : transactions) {
            if (transaction != null) {
                for (String item : transaction) {
                    frequencyMap.merge(item, 1, Integer::sum);
                }
            }
        }
        return frequencyMap;
    }
}
