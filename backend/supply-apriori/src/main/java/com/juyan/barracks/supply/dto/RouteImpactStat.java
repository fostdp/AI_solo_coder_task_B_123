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
public class RouteImpactStat {
    private String supplyRoute;
    private String routeName;
    private Long totalDeliveries;
    private Long deficitDeliveries;
    private BigDecimal deficitRate;
    private BigDecimal avgDeficitRate;
    private String riskLevel;
}
