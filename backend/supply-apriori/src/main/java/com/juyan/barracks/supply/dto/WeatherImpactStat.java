package com.juyan.barracks.supply.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherImpactStat {
    private String weatherCondition;
    private String weatherName;
    private Long totalDays;
    private Long deficitDays;
    private BigDecimal deficitRate;
    private BigDecimal avgDeficitAmount;
    private String riskLevel;
}
