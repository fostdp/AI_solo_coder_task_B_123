package com.juyan.barracks.seir.algorithm;

import com.juyan.barracks.common.entity.ContactEdge;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SeirModel {

    private static final double MIN_BETA = 0.001;
    private static final double MAX_BETA = 10.0;
    private static final double MIN_SIGMA = 0.01;
    private static final double MAX_SIGMA = 10.0;
    private static final double MIN_GAMMA = 0.01;
    private static final double MAX_GAMMA = 10.0;

    private final double betaBase;
    private final double sigma;
    private final double gamma;

    public SeirModel(double betaBase, double sigma, double gamma) {
        this.betaBase = clampBeta(betaBase);
        this.sigma = clampSigma(sigma);
        this.gamma = clampGamma(gamma);
    }

    public static double clampBeta(double beta) {
        double clamped = Math.max(MIN_BETA, Math.min(MAX_BETA, beta));
        if (Math.abs(clamped - beta) > 0.0001) {
            log.info("beta被钳制: {} → {}", beta, clamped);
        }
        return clamped;
    }

    public static double clampSigma(double sigma) {
        double clamped = Math.max(MIN_SIGMA, Math.min(MAX_SIGMA, sigma));
        if (Math.abs(clamped - sigma) > 0.0001) {
            log.info("sigma被钳制: {} → {}", sigma, clamped);
        }
        return clamped;
    }

    public static double clampGamma(double gamma) {
        double clamped = Math.max(MIN_GAMMA, Math.min(MAX_GAMMA, gamma));
        if (Math.abs(clamped - gamma) > 0.0001) {
            log.info("gamma被钳制: {} → {}", gamma, clamped);
        }
        return clamped;
    }

    public static int clampInitialInfected(int initialInfected, int population) {
        if (initialInfected < 0) {
            log.info("initialInfected被钳制: {} → 0", initialInfected);
            return 0;
        }
        if (initialInfected > population) {
            log.info("initialInfected被钳制: {} → {}", initialInfected, population);
            return population;
        }
        return initialInfected;
    }

    public static String clampAndDescribe(double value, double min, double max, String name) {
        double clamped = Math.max(min, Math.min(max, value));
        if (Math.abs(clamped - value) > 0.0001) {
            return String.format("%s被钳制: %.4f → %.4f", name, value, clamped);
        }
        return String.format("%s保持原值: %.4f", name, value);
    }

    public double getR0() {
        return betaBase / gamma;
    }

    public static final String LOCATION_BARRACKS = "BARRACKS";
    public static final String LOCATION_CANTEEN_A = "CANTEEN_A";
    public static final String LOCATION_CANTEEN_B = "CANTEEN_B";
    public static final String LOCATION_TRAINING = "TRAINING";
    public static final String LOCATION_HOSPITAL = "HOSPITAL";

    public enum TimeOfDay {
        MORNING(0.3, LOCATION_BARRACKS),
        BREAKFAST(1.5, LOCATION_CANTEEN_A),
        MORNING_TRAINING(1.8, LOCATION_TRAINING),
        LUNCH(2.0, LOCATION_CANTEEN_B),
        AFTERNOON_TRAINING(1.5, LOCATION_TRAINING),
        DINNER(1.2, LOCATION_CANTEEN_A),
        EVENING(0.8, LOCATION_BARRACKS),
        NIGHT(0.1, LOCATION_BARRACKS);

        private final double contactIntensity;
        private final String defaultLocation;

        TimeOfDay(double contactIntensity, String defaultLocation) {
            this.contactIntensity = contactIntensity;
            this.defaultLocation = defaultLocation;
        }

        public double getContactIntensity() {
            return contactIntensity;
        }

        public String getDefaultLocation() {
            return defaultLocation;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DynamicContactEdge extends ContactEdge {
        private String morningLocation;
        private String afternoonLocation;
        private String eveningLocation;

        public double getFrequencyByTime(TimeOfDay timeOfDay) {
            double baseFreq = getContactFrequencyPerDay() != null ? getContactFrequencyPerDay() : 0.0;
            return baseFreq * timeOfDay.getContactIntensity();
        }
    }

    private static TimeOfDay getTimeOfDayBySimulationDay(int day) {
        int cycleIndex = day % TimeOfDay.values().length;
        return TimeOfDay.values()[cycleIndex];
    }

    private double calculateDynamicDailyBeta(int day, double baseBeta,
                                             List<ContactEdge> contactEdges) {
        TimeOfDay timeOfDay = getTimeOfDayBySimulationDay(day);
        double timeIntensity = timeOfDay.getContactIntensity();

        double avgDailyFrequency = 0.0;
        int count = 0;

        if (contactEdges != null) {
            for (ContactEdge edge : contactEdges) {
                if (edge == null) continue;
                Double freq = edge.getContactFrequencyPerDay();
                if (freq != null) {
                    if (edge instanceof DynamicContactEdge dynamicEdge) {
                        avgDailyFrequency += dynamicEdge.getFrequencyByTime(timeOfDay);
                    } else {
                        avgDailyFrequency += freq * timeIntensity;
                    }
                    count++;
                }
            }
        }

        if (count == 0) {
            return 0.0;
        }

        avgDailyFrequency /= count;

        double locationFactor = 1.0;
        String location = timeOfDay.getDefaultLocation();
        switch (location) {
            case LOCATION_CANTEEN_A:
            case LOCATION_CANTEEN_B:
                locationFactor = 1.8;
                break;
            case LOCATION_TRAINING:
                locationFactor = 1.5;
                break;
            case LOCATION_BARRACKS:
                locationFactor = 0.8;
                break;
            case LOCATION_HOSPITAL:
                locationFactor = 0.3;
                break;
        }

        double calibrationBase = 5.0;
        double calibrationFactor = (avgDailyFrequency / calibrationBase) * locationFactor;

        double dynamicBeta = baseBeta * calibrationFactor;
        dynamicBeta = clampBeta(dynamicBeta);

        log.debug("Day {}: Time={}, Loc={}, avgFreq={:.2f}, locFactor={:.1f}, calFactor={:.3f}, dynamicBeta={:.4f}",
                day, timeOfDay.name(), location, avgDailyFrequency, locationFactor, calibrationFactor, dynamicBeta);

        return dynamicBeta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuarantineConfig {
        private int quarantineStartDay;
        private double isolationEffectiveness;
        private double quarantineRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimulationParams {
        private double beta;
        private double sigma;
        private double gamma;
    }

    public SeirResult simulate(int population, int initialInfected, int days,
                               SimulationParams params, List<ContactEdge> contactEdges,
                               QuarantineConfig quarantineConfig) {
        if (population <= 0 || days <= 0) {
            log.warn("SEIR模拟参数无效: population={}, days={}", population, days);
            return SeirResult.empty();
        }

        initialInfected = clampInitialInfected(initialInfected, population);
        if (initialInfected <= 0) {
            log.warn("初始感染人数钳制后为0，返回空结果");
            return SeirResult.empty();
        }

        double beta = params != null && params.getBeta() > 0 ? params.getBeta() : this.betaBase;
        double sigma = params != null && params.getSigma() > 0 ? params.getSigma() : this.sigma;
        double gamma = params != null && params.getGamma() > 0 ? params.getGamma() : this.gamma;

        beta = clampBeta(beta);
        sigma = clampSigma(sigma);
        gamma = clampGamma(gamma);

        double staticNetworkBeta = adjustBetaByContactNetwork(beta, contactEdges);

        double r0 = beta / gamma;

        int S = population - initialInfected;
        int E = 0;
        int I = initialInfected;
        int R = 0;
        int Q = 0;
        int N = population;

        List<SeirResult.SeirDayPoint> dayPoints = new ArrayList<>();

        dayPoints.add(SeirResult.SeirDayPoint.builder()
                .day(0)
                .susceptible(S)
                .exposed(E)
                .infected(I)
                .recovered(R)
                .quarantined(Q)
                .build());

        int peakDay = 0;
        int peakInfected = I;
        int totalInfected = I;
        int lastInfectedDay = 0;

        double qRate = quarantineConfig != null ? quarantineConfig.getQuarantineRate() : 0.0;
        if (qRate <= 0) {
            qRate = 0.5;
        }

        int lastQuarantineDay = -1;

        for (int day = 1; day <= days; day++) {
            double dynamicBeta = calculateDynamicDailyBeta(day, beta, contactEdges);

            if (dynamicBeta <= 0.0001 && staticNetworkBeta > 0) {
                dynamicBeta = staticNetworkBeta * 0.5;
            }

            double currentBeta = dynamicBeta;

            if (quarantineConfig != null && quarantineConfig.getQuarantineStartDay() > 0
                    && day >= quarantineConfig.getQuarantineStartDay()) {
                double effect = quarantineConfig.getIsolationEffectiveness();
                if (effect < 0) effect = 0;
                if (effect > 1) effect = 1;
                currentBeta = dynamicBeta * (1 - effect);
                lastQuarantineDay = day;
            }

            double Sd = S;
            double Ed = E;
            double Id = I;
            double Rd = R;
            double Qd = Q;
            double Nd = N;

            if (Nd <= 0) Nd = 1;

            double dS = -currentBeta * Sd * Id / Nd;

            double newInfections = currentBeta * Sd * Id / Nd;
            double newExposedToInfected = sigma * Ed;
            double newRecovered = gamma * Id;

            double quarantineFlow = 0;
            if (quarantineConfig != null && quarantineConfig.getQuarantineStartDay() > 0
                    && day >= quarantineConfig.getQuarantineStartDay()) {
                quarantineFlow = qRate * newExposedToInfected;
            }

            double releaseFromQ = gamma * Qd;

            double newS = Sd + dS;
            double newE = Ed + newInfections - newExposedToInfected;
            double newI = Id + (newExposedToInfected - quarantineFlow) - newRecovered;
            double newR = Rd + newRecovered + releaseFromQ;
            double newQ = Qd + quarantineFlow - releaseFromQ;

            if (newS < 0) newS = 0;
            if (newE < 0) newE = 0;
            if (newI < 0) newI = 0;
            if (newR < 0) newR = 0;
            if (newQ < 0) newQ = 0;

            double total = newS + newE + newI + newR + newQ;
            if (total > 0 && Math.abs(total - Nd) > 0.001) {
                double scale = Nd / total;
                newS *= scale;
                newE *= scale;
                newI *= scale;
                newR *= scale;
                newQ *= scale;
            }

            S = (int) Math.round(newS);
            E = (int) Math.round(newE);
            I = (int) Math.round(newI);
            R = (int) Math.round(newR);
            Q = (int) Math.round(newQ);

            int diff = N - (S + E + I + R + Q);
            if (diff != 0) {
                if (diff > 0) {
                    S += diff;
                } else {
                    if (S >= -diff) {
                        S += diff;
                    } else if (R >= -diff) {
                        R += diff;
                    } else {
                        S = 0;
                        R = 0;
                    }
                }
            }

            dayPoints.add(SeirResult.SeirDayPoint.builder()
                    .day(day)
                    .susceptible(S)
                    .exposed(E)
                    .infected(I)
                    .recovered(R)
                    .quarantined(Q)
                    .build());

            int currentInfected = I + Q;
            if (currentInfected > peakInfected) {
                peakInfected = currentInfected;
                peakDay = day;
            }

            if (currentInfected > 0) {
                lastInfectedDay = day;
            }
        }

        totalInfected = R + Q;
        if (dayPoints.size() > 0) {
            SeirResult.SeirDayPoint last = dayPoints.get(dayPoints.size() - 1);
            totalInfected = last.getRecovered() + last.getQuarantined();
        }

        int durationDays = lastInfectedDay;

        log.info("SEIR动态网络模拟完成: 人口={}, 初始感染={}, 模拟天数={}, R0={}, 峰值日={}, 峰值感染={}, 总感染={}, 隔离起始日={}",
                population, initialInfected, days, String.format("%.4f", r0),
                peakDay, peakInfected, totalInfected,
                quarantineConfig != null ? quarantineConfig.getQuarantineStartDay() : "无");

        SeirResult result = SeirResult.builder()
                .dayPoints(dayPoints)
                .peakDay(peakDay)
                .peakInfected(peakInfected)
                .totalInfected(totalInfected)
                .durationDays(durationDays)
                .r0(r0)
                .build();

        if (result.getDynamicBetaDaily() == null) {
            result.setDynamicBetaDaily(new ArrayList<>());
        }
        for (int day = 1; day <= days; day++) {
            double dailyBeta = calculateDynamicDailyBeta(day, beta, contactEdges);
            if (quarantineConfig != null && quarantineConfig.getQuarantineStartDay() > 0
                    && day >= quarantineConfig.getQuarantineStartDay()) {
                double effect = quarantineConfig.getIsolationEffectiveness();
                effect = Math.max(0, Math.min(1, effect));
                dailyBeta = dailyBeta * (1 - effect);
            }
            result.getDynamicBetaDaily().add(dailyBeta);
        }

        return result;
    }

    private double adjustBetaByContactNetwork(double betaBase, List<ContactEdge> contactEdges) {
        if (contactEdges == null || contactEdges.isEmpty()) {
            log.info("接触网络为空，传播速率β设为0");
            return 0.0;
        }

        double totalFrequency = 0;
        int count = 0;
        for (ContactEdge edge : contactEdges) {
            if (edge != null && edge.getContactFrequencyPerDay() != null) {
                totalFrequency += edge.getContactFrequencyPerDay();
                count++;
            }
        }

        if (count == 0 || totalFrequency <= 0.0001) {
            log.info("接触网络平均频率为0，传播速率β设为0");
            return 0.0;
        }

        double avgFrequency = totalFrequency / count;
        double calibrationFactor = avgFrequency / 5.0;

        double adjustedBeta = betaBase * calibrationFactor;
        log.debug("接触网络调整β: base={}, 平均频率={}, 校准因子={}, 调整后={}",
                String.format("%.4f", betaBase),
                String.format("%.2f", avgFrequency),
                String.format("%.4f", calibrationFactor),
                String.format("%.4f", adjustedBeta));

        return adjustedBeta;
    }
}
