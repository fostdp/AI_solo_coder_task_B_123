package com.juyan.barracks.seir.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeirResult {

    private List<SeirDayPoint> dayPoints;
    private int peakDay;
    private int peakInfected;
    private int totalInfected;
    private int durationDays;
    private double r0;
    @Builder.Default
    private List<Double> dynamicBetaDaily = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeirDayPoint {
        private int day;
        private int susceptible;
        private int exposed;
        private int infected;
        private int recovered;
        private int quarantined;
    }

    public static SeirResult empty() {
        return SeirResult.builder()
                .dayPoints(new ArrayList<>())
                .peakDay(0)
                .peakInfected(0)
                .totalInfected(0)
                .durationDays(0)
                .r0(0.0)
                .build();
    }
}
