package com.juyan.barracks.supply.dto;

import com.juyan.barracks.supply.algorithm.AssociationRuleResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDTO {
    private LocalDateTime analyzedAt;
    private Integer totalTransactions;
    private Integer frequentItemSetCount;
    private Integer ruleCount;
    private Integer significantRuleCount;
    @Builder.Default
    private List<AssociationRuleResult> significantRules = new ArrayList<>();
    @Builder.Default
    private List<TopDeficitItem> topDeficitItems = new ArrayList<>();
    @Builder.Default
    private List<String> improvementSuggestions = new ArrayList<>();
}
