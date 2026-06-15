package com.juyan.barracks.epidemic.algorithm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
public class SatscanEpidemicDetector {

    private final GeometryFactory geometryFactory = new GeometryFactory();
    private final double spatialRadiusMeters;
    private final int maxWindowDays;
    private final double significanceLevel;

    public SatscanEpidemicDetector(double spatialRadiusMeters, int maxWindowDays, double significanceLevel) {
        this.spatialRadiusMeters = spatialRadiusMeters;
        this.maxWindowDays = maxWindowDays;
        this.significanceLevel = significanceLevel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClusterPoint {
        private double x;
        private double y;
        private LocalDateTime time;
        private boolean isCase;
        private Long soldierId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanResult {
        private boolean hasSignificantCluster;
        private double pValue;
        private double logLikelihoodRatio;
        private Point clusterCenter;
        private double clusterRadius;
        private int casesInCluster;
        private int totalInCluster;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<ClusterPoint> affectedPoints;
        private double positiveRate;
    }

    public ScanResult scanSpatiotemporal(List<ClusterPoint> points, LocalDateTime scanEndTime) {
        if (points == null || points.isEmpty()) {
            return ScanResult.builder()
                    .hasSignificantCluster(false)
                    .pValue(1.0)
                    .logLikelihoodRatio(0.0)
                    .affectedPoints(new ArrayList<>())
                    .build();
        }

        int totalCases = (int) points.stream().filter(ClusterPoint::isCase).count();
        int totalPopulation = points.size();

        if (totalCases == 0) {
            return ScanResult.builder()
                    .hasSignificantCluster(false)
                    .pValue(1.0)
                    .logLikelihoodRatio(0.0)
                    .affectedPoints(new ArrayList<>())
                    .build();
        }

        double baselineRate = (double) totalCases / totalPopulation;

        ScanResult bestCluster = null;
        double bestLLR = Double.NEGATIVE_INFINITY;

        LocalDateTime scanStartTime = scanEndTime.minusDays(maxWindowDays);

        for (ClusterPoint centerPoint : points) {
            for (int timeWindowDays = 1; timeWindowDays <= maxWindowDays; timeWindowDays++) {
                for (double radiusStep = 20.0; radiusStep <= spatialRadiusMeters; radiusStep += 20.0) {
                    LocalDateTime windowStart = scanEndTime.minusDays(timeWindowDays);
                    if (windowStart.isBefore(scanStartTime)) {
                        windowStart = scanStartTime;
                    }

                    List<ClusterPoint> pointsInWindow = filterBySpaceTime(
                            points, centerPoint, radiusStep, windowStart, scanEndTime);

                    if (pointsInWindow.size() < 2) continue;

                    int casesInWindow = (int) pointsInWindow.stream().filter(ClusterPoint::isCase).count();
                    int populationInWindow = pointsInWindow.size();

                    if (casesInWindow == 0 || casesInWindow == populationInWindow) continue;

                    double llr = calculateLogLikelihoodRatio(
                            casesInWindow, populationInWindow,
                            totalCases - casesInWindow, totalPopulation - populationInWindow,
                            baselineRate);

                    if (llr > bestLLR) {
                        bestLLR = llr;

                        double positiveRate = populationInWindow > 0 ?
                                (double) casesInWindow / populationInWindow : 0.0;

                        bestCluster = ScanResult.builder()
                                .logLikelihoodRatio(llr)
                                .clusterCenter(geometryFactory.createPoint(
                                        new Coordinate(centerPoint.getX(), centerPoint.getY())))
                                .clusterRadius(radiusStep)
                                .casesInCluster(casesInWindow)
                                .totalInCluster(populationInWindow)
                                .startTime(windowStart)
                                .endTime(scanEndTime)
                                .affectedPoints(new ArrayList<>(pointsInWindow))
                                .positiveRate(positiveRate)
                                .build();
                    }
                }
            }
        }

        if (bestCluster != null) {
            double pValue = calculatePValue(points, bestCluster.getLogLikelihoodRatio(),
                    baselineRate, totalCases, totalPopulation);

            bestCluster.setPValue(pValue);
            bestCluster.setHasSignificantCluster(pValue < significanceLevel);

            log.info("时空扫描结果: LLR={}, pValue={}, 阳性率={}, 聚类内样本数={}",
                    String.format("%.4f", bestCluster.getLogLikelihoodRatio()),
                    String.format("%.4f", pValue),
                    String.format("%.2f%%", bestCluster.getPositiveRate() * 100),
                    bestCluster.getTotalInCluster());
        }

        return bestCluster != null ? bestCluster : ScanResult.builder()
                .hasSignificantCluster(false)
                .pValue(1.0)
                .logLikelihoodRatio(0.0)
                .affectedPoints(new ArrayList<>())
                .build();
    }

    private List<ClusterPoint> filterBySpaceTime(List<ClusterPoint> points, ClusterPoint center,
                                                  double radiusMeters, LocalDateTime startTime,
                                                  LocalDateTime endTime) {
        List<ClusterPoint> result = new ArrayList<>();
        double radiusDegrees = radiusMeters / 111000.0;

        for (ClusterPoint p : points) {
            if (p.getTime().isBefore(startTime) || p.getTime().isAfter(endTime)) {
                continue;
            }

            double dx = (p.getX() - center.getX()) * 111000.0;
            double dy = (p.getY() - center.getY()) * 111000.0 * Math.cos(Math.toRadians(center.getY()));
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance <= radiusMeters) {
                result.add(p);
            }
        }

        return result;
    }

    private double calculateLogLikelihoodRatio(int casesIn, int popIn,
                                                int casesOut, int popOut,
                                                double baselineRate) {
        if (popIn == 0 || popOut == 0) return 0.0;

        double rateIn = (double) casesIn / popIn;
        double rateOut = (double) casesOut / popOut;

        if (rateIn <= baselineRate) return 0.0;

        double llIn = 0;
        if (casesIn > 0) {
            llIn = casesIn * Math.log(rateIn) + (popIn - casesIn) * Math.log(1 - rateIn);
        }
        double llOut = 0;
        if (casesOut > 0) {
            llOut = casesOut * Math.log(rateOut) + (popOut - casesOut) * Math.log(1 - rateOut);
        }

        double ll0 = 0;
        int totalCases = casesIn + casesOut;
        int totalPop = popIn + popOut;
        if (totalCases > 0 && totalCases < totalPop) {
            double overallRate = (double) totalCases / totalPop;
            ll0 = totalCases * Math.log(overallRate) + (totalPop - totalCases) * Math.log(1 - overallRate);
        }

        return 2.0 * (llIn + llOut - ll0);
    }

    private double calculatePValue(List<ClusterPoint> originalPoints, double observedLLR,
                                    double baselineRate, int totalCases, int totalPopulation) {
        if (observedLLR <= 0) return 1.0;

        int numPermutations = 99;
        int exceedCount = 0;
        Random random = new Random(42);

        for (int perm = 0; perm < numPermutations; perm++) {
            List<Boolean> permutedCases = generatePermutedLabels(totalCases, totalPopulation, random);

            List<ClusterPoint> permutedPoints = new ArrayList<>();
            for (int i = 0; i < originalPoints.size(); i++) {
                ClusterPoint p = originalPoints.get(i);
                ClusterPoint permuted = ClusterPoint.builder()
                        .x(p.getX())
                        .y(p.getY())
                        .time(p.getTime())
                        .isCase(i < permutedCases.size() ? permutedCases.get(i) : false)
                        .soldierId(p.getSoldierId())
                        .build();
                permutedPoints.add(permuted);
            }

            double permutedMaxLLR = findMaxLLR(permutedPoints, baselineRate);
            if (permutedMaxLLR >= observedLLR) {
                exceedCount++;
            }
        }

        return (double) (exceedCount + 1) / (numPermutations + 1);
    }

    private List<Boolean> generatePermutedLabels(int totalCases, int totalPopulation, Random random) {
        List<Boolean> labels = new ArrayList<>();
        for (int i = 0; i < totalPopulation; i++) {
            labels.add(i < totalCases);
        }
        Collections.shuffle(labels, random);
        return labels;
    }

    private double findMaxLLR(List<ClusterPoint> points, double baselineRate) {
        double maxLLR = 0.0;
        int totalCases = (int) points.stream().filter(ClusterPoint::isCase).count();
        int totalPopulation = points.size();
        LocalDateTime scanEndTime = points.stream()
                .map(ClusterPoint::getTime)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime scanStartTime = scanEndTime.minusDays(maxWindowDays);

        for (ClusterPoint center : points) {
            for (int timeWindowDays = 1; timeWindowDays <= maxWindowDays; timeWindowDays++) {
                for (double radiusStep = 20.0; radiusStep <= spatialRadiusMeters; radiusStep += 20.0) {
                    LocalDateTime windowStart = scanEndTime.minusDays(timeWindowDays);
                    if (windowStart.isBefore(scanStartTime)) {
                        windowStart = scanStartTime;
                    }

                    List<ClusterPoint> pointsInWindow = filterBySpaceTime(
                            points, center, radiusStep, windowStart, scanEndTime);

                    if (pointsInWindow.size() < 2) continue;

                    int casesInWindow = (int) pointsInWindow.stream().filter(ClusterPoint::isCase).count();
                    int popInWindow = pointsInWindow.size();

                    if (casesInWindow == 0 || casesInWindow == popInWindow) continue;

                    double llr = calculateLogLikelihoodRatio(
                            casesInWindow, popInWindow,
                            totalCases - casesInWindow, totalPopulation - popInWindow,
                            baselineRate);

                    if (llr > maxLLR) {
                        maxLLR = llr;
                    }
                }
            }
        }

        return maxLLR;
    }
}
