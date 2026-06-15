package com.juyan.barracks.common.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NutritionFeatureTest {

    @Test
    void testBuilderAndGetters() {
        NutritionFeature feature = NutritionFeature.builder()
                .dailyProteinG(70.5)
                .dailyFatG(50.0)
                .dailyVitaminCMg(80.0)
                .dailyCalorieKcal(3200.0)
                .physicalActivityLevel(0.7)
                .age(25)
                .ageGroupYoung(1.0)
                .ageGroupMiddle(0.0)
                .ageGroupSenior(0.0)
                .originNorth(1.0)
                .originSouth(0.0)
                .originWest(0.0)
                .daysSinceLastMeal(1)
                .proteinVariance(5.2)
                .fatVariance(3.0)
                .vitaminCVariance(10.5)
                .build();

        assertEquals(70.5, feature.getDailyProteinG());
        assertEquals(50.0, feature.getDailyFatG());
        assertEquals(80.0, feature.getDailyVitaminCMg());
        assertEquals(3200.0, feature.getDailyCalorieKcal());
        assertEquals(0.7, feature.getPhysicalActivityLevel());
        assertEquals(25, feature.getAge());
        assertEquals(1.0, feature.getAgeGroupYoung());
        assertEquals(0.0, feature.getAgeGroupMiddle());
        assertEquals(0.0, feature.getAgeGroupSenior());
        assertEquals(1.0, feature.getOriginNorth());
        assertEquals(0.0, feature.getOriginSouth());
        assertEquals(0.0, feature.getOriginWest());
        assertEquals(1, feature.getDaysSinceLastMeal());
        assertEquals(5.2, feature.getProteinVariance());
        assertEquals(3.0, feature.getFatVariance());
        assertEquals(10.5, feature.getVitaminCVariance());
    }

    @Test
    void testToFeatureArray() {
        NutritionFeature feature = NutritionFeature.builder()
                .dailyProteinG(70.0)
                .dailyFatG(50.0)
                .dailyVitaminCMg(80.0)
                .dailyCalorieKcal(3000.0)
                .physicalActivityLevel(0.5)
                .ageGroupYoung(1.0)
                .ageGroupMiddle(0.0)
                .ageGroupSenior(0.0)
                .originNorth(1.0)
                .originSouth(0.0)
                .originWest(0.0)
                .build();

        double[] arr = feature.toFeatureArray();
        assertEquals(16, arr.length);
        assertEquals(70.0, arr[0]);
        assertEquals(50.0, arr[1]);
        assertEquals(80.0, arr[2]);
        assertEquals(3000.0, arr[3]);
        assertEquals(0.5, arr[4]);
        assertEquals(1.0, arr[5]);
        assertEquals(0.0, arr[6]);
        assertEquals(0.0, arr[7]);
        assertEquals(1.0, arr[8]);
        assertEquals(0.0, arr[9]);
        assertEquals(0.0, arr[10]);
    }

    @Test
    void testDefaultValues() {
        NutritionFeature feature = new NutritionFeature();
        double[] arr = feature.toFeatureArray();
        assertEquals(16, arr.length);
        for (double v : arr) {
            assertEquals(0.0, v);
        }
    }
}
