package com.juyan.barracks.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionFeature {
    private BigDecimal dailyProteinG;
    private BigDecimal dailyFatG;
    private BigDecimal dailyVitaminCMg;
    private BigDecimal dailyCalorieKcal;
    private BigDecimal calorieBurned;
    private Integer age;
    private Integer activityDays;
    private BigDecimal avgProtein7d;
    private BigDecimal avgFat7d;
    private BigDecimal avgVitaminC7d;

    private int ageGroupYoung;
    private int ageGroupMiddle;
    private int ageGroupSenior;

    private int originNorth;
    private int originSouth;
    private int originWest;
}
