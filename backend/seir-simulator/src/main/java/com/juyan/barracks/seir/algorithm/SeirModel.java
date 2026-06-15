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

    private final double betaBase;
    private final double sigma;
    private final double gamma;

    public SeirModel(double betaBase, double sigma, double gamma) {
        this.betaBase = betaBase;
        this.sigma = sigma;
        this.gamma = gamma;
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
        if (population <= 0 || initialInfected <= 0 || days <= 0) {
            log.warn("SEIR模拟参数无效: population={}, initialInfected={}, days={}",
                    population, initialInfected, days);
            return SeirResult.empty();
        }

        if (initialInfected > population) {
            initialInfected = population;
        }

        double beta = params != null && params.getBeta() > 0 ? params.getBeta() : this.betaBase;
        double sigma = params != null && params.getSigma() > 0 ? params.getSigma() : this.sigma;
        double gamma = params != null && params.getGamma() > 0 ? params.getGamma() : this.gamma;

        beta = adjustBetaByContactNetwork(beta, contactEdges);

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

        double currentBeta = beta;
        double qRate = quarantineConfig != null ? quarantineConfig.getQuarantineRate() : 0.0;
        if (qRate <= 0) {
            qRate = 0.5;
        }

        for (int day = 1; day <= days; day++) {
            if (quarantineConfig != null && quarantineConfig.getQuarantineStartDay() > 0
                    && day >= quarantineConfig.getQuarantineStartDay()) {
                double effect = quarantineConfig.getIsolationEffectiveness();
                if (effect < 0) effect = 0;
                if (effect > 1) effect = 1;
                currentBeta = beta * (1 - effect);
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

        log.info("SEIR模拟完成: 人口={}, 初始感染={}, 模拟天数={}, R0={}, 峰值日={}, 峰值感染={}, 总感染={}",
                population, initialInfected, days, String.format("%.4f", r0),
                peakDay, peakInfected, totalInfected);

        return SeirResult.builder()
                .dayPoints(dayPoints)
                .peakDay(peakDay)
                .peakInfected(peakInfected)
                .totalInfected(totalInfected)
                .durationDays(durationDays)
                .r0(r0)
                .build();
    }

    private double adjustBetaByContactNetwork(double betaBase, List<ContactEdge> contactEdges) {
        if (contactEdges == null || contactEdges.isEmpty()) {
            return betaBase;
        }

        double totalFrequency = 0;
        int count = 0;
        for (ContactEdge edge : contactEdges) {
            if (edge != null && edge.getContactFrequencyPerDay() != null) {
                totalFrequency += edge.getContactFrequencyPerDay();
                count++;
            }
        }

        if (count == 0) {
            return betaBase;
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
