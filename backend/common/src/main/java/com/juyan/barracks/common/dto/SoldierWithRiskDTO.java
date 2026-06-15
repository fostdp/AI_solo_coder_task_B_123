package com.juyan.barracks.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoldierWithRiskDTO {
    private Long soldierId;
    private String soldierCode;
    private String name;
    private Integer age;
    private String rank;
    private String originRegion;
    private Integer positionX;
    private Integer positionY;
    private String status;
    private Long barracksId;
    private String riskLevel;
    private BigDecimal overallRiskScore;
    private BigDecimal vitaminCRiskScore;
    private BigDecimal proteinRiskScore;
    private BigDecimal fatRiskScore;
    private String dietarySuggestion;
    private BigDecimal dailyProteinG;
    private BigDecimal dailyFatG;
    private BigDecimal dailyVitaminCMg;
    private BigDecimal dailyCalorieKcal;
}
