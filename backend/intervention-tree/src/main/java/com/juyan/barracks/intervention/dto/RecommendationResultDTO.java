package com.juyan.barracks.intervention.dto;

import com.juyan.barracks.intervention.algorithm.SupplementCatalog;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class RecommendationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long soldierId;
    private String soldierName;
    private String riskLevel;
    private BigDecimal overallRiskScore;
    private int durationDays;
    private List<SupplementDetail> supplements;
    private List<String> matchedRules;
    private BigDecimal totalEstimatedCost;
    private String recommendationSource;

    public RecommendationResultDTO() {
        this.supplements = new ArrayList<>();
        this.matchedRules = new ArrayList<>();
        this.totalEstimatedCost = BigDecimal.ZERO;
    }

    @Data
    public static class SupplementDetail implements Serializable {
        private static final long serialVersionUID = 1L;

        private String supplementCode;
        private String supplementName;
        private String category;
        private int dailyDosage;
        private String dailyDosageUnit;
        private String dosageDescription;
        private BigDecimal dailyCost;
        private BigDecimal totalCost;
        private SupplementCatalog.NutrientContent nutrientContent;
        private List<String> reasons;

        public SupplementDetail() {
            this.reasons = new ArrayList<>();
        }
    }
}
