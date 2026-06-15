package com.juyan.barracks.supply.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssociationRuleResult {

    private List<String> antecedent = new ArrayList<>();

    private List<String> consequent = new ArrayList<>();

    private BigDecimal support;

    private BigDecimal confidence;

    private BigDecimal lift;

    private BigDecimal leverage;

    private Integer count;

    private String description;

    public static class ItemSet {
        public final java.util.Set<String> items;
        public final int count;
        public final double support;

        public ItemSet(java.util.Set<String> items, int count, double support) {
            this.items = items;
            this.count = count;
            this.support = support;
        }
    }
}
