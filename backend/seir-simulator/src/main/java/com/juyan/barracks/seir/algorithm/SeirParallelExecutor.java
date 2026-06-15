package com.juyan.barracks.seir.algorithm;

import com.juyan.barracks.common.entity.ContactEdge;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class SeirParallelExecutor {

    @Value("${seir.default-beta:0.35}")
    private double defaultBeta;

    @Value("${seir.default-sigma:0.6667}")
    private double defaultSigma;

    @Value("${seir.default-gamma:0.2}")
    private double defaultGamma;

    private SeirModel seirModel;

    @PostConstruct
    public void init() {
        seirModel = new SeirModel(defaultBeta, defaultSigma, defaultGamma);
        log.info("SeirParallelExecutor初始化完成: β={}, σ={}, γ={}",
                defaultBeta, defaultSigma, defaultGamma);
    }

    public List<Future<SeirResult>> simulateParallel(
            List<SeirModel.SimulationParams> paramSets,
            int population, int initialInfected, int days,
            List<ContactEdge> contactEdges,
            SeirModel.QuarantineConfig quarantineConfig) {

        int poolSize = Math.min(Runtime.getRuntime().availableProcessors(), paramSets.size());
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);

        List<Future<SeirResult>> futures = new ArrayList<>();
        for (SeirModel.SimulationParams params : paramSets) {
            futures.add(executor.submit(() ->
                    seirModel.simulate(population, initialInfected, days, params, contactEdges, quarantineConfig)
            ));
        }

        executor.shutdown();
        return futures;
    }

    public SeirResult simulateSingleWithParallelODE(
            int population, int initialInfected, int days,
            SeirModel.SimulationParams params,
            List<ContactEdge> contactEdges,
            SeirModel.QuarantineConfig quarantineConfig) {

        if (population <= 0 || days <= 0) {
            log.warn("SEIR ODE模拟参数无效: population={}, days={}", population, days);
            return SeirResult.empty();
        }

        initialInfected = SeirModel.clampInitialInfected(initialInfected, population);
        if (initialInfected <= 0) {
            log.warn("初始感染人数钳制后为0，返回空结果");
            return SeirResult.empty();
        }

        double beta = params != null && params.getBeta() > 0 ? params.getBeta() : defaultBeta;
        double sigma = params != null && params.getSigma() > 0 ? params.getSigma() : defaultSigma;
        double gamma = params != null && params.getGamma() > 0 ? params.getGamma() : defaultGamma;

        beta = SeirModel.clampBeta(beta);
        sigma = SeirModel.clampSigma(sigma);
        gamma = SeirModel.clampGamma(gamma);

        double qRate = 0.0;
        if (quarantineConfig != null && quarantineConfig.getQuarantineRate() > 0) {
            qRate = quarantineConfig.getQuarantineRate();
        }

        SeirOdeSystem odeSystem = new SeirOdeSystem(beta, sigma, gamma, qRate, population);

        double minStep = 0.01;
        double maxStep = 1.0;
        double scalAbsoluteTolerance = 1.0e-8;
        double scalRelativeTolerance = 1.0e-8;

        DormandPrince853Integrator integrator = new DormandPrince853Integrator(
                minStep, maxStep, scalAbsoluteTolerance, scalRelativeTolerance);

        double[] state = new double[]{
                population - initialInfected, 0, initialInfected, 0, 0
        };

        List<SeirResult.SeirDayPoint> dayPoints = new ArrayList<>();
        dayPoints.add(SeirResult.SeirDayPoint.builder()
                .day(0)
                .susceptible((int) Math.round(state[0]))
                .exposed((int) Math.round(state[1]))
                .infected((int) Math.round(state[2]))
                .recovered((int) Math.round(state[3]))
                .quarantined((int) Math.round(state[4]))
                .build());

        for (int day = 1; day <= days; day++) {
            integrator.integrate(odeSystem, day - 1, state, day, state);

            for (int i = 0; i < state.length; i++) {
                if (state[i] < 0) {
                    state[i] = 0;
                }
            }

            dayPoints.add(SeirResult.SeirDayPoint.builder()
                    .day(day)
                    .susceptible((int) Math.round(state[0]))
                    .exposed((int) Math.round(state[1]))
                    .infected((int) Math.round(state[2]))
                    .recovered((int) Math.round(state[3]))
                    .quarantined((int) Math.round(state[4]))
                    .build());
        }

        int peakDay = 0;
        int peakInfected = 0;
        int lastInfectedDay = 0;

        for (SeirResult.SeirDayPoint dp : dayPoints) {
            int currentInfected = dp.getInfected() + dp.getQuarantined();
            if (currentInfected > peakInfected) {
                peakInfected = currentInfected;
                peakDay = dp.getDay();
            }
            if (currentInfected > 0) {
                lastInfectedDay = dp.getDay();
            }
        }

        SeirResult.SeirDayPoint last = dayPoints.get(dayPoints.size() - 1);
        int totalInfected = last.getRecovered() + last.getQuarantined();

        double r0 = beta / gamma;

        log.info("SEIR ODE模拟完成: 人口={}, 初始感染={}, 模拟天数={}, R0={}, 峰值日={}, 峰值感染={}, 总感染={}",
                population, initialInfected, days, String.format("%.4f", r0),
                peakDay, peakInfected, totalInfected);

        return SeirResult.builder()
                .dayPoints(dayPoints)
                .peakDay(peakDay)
                .peakInfected(peakInfected)
                .totalInfected(totalInfected)
                .durationDays(lastInfectedDay)
                .r0(r0)
                .build();
    }

    private static class SeirOdeSystem implements FirstOrderDifferentialEquations {

        private final double beta;
        private final double sigma;
        private final double gamma;
        private final double qRate;
        private final double N;

        SeirOdeSystem(double beta, double sigma, double gamma, double qRate, double N) {
            this.beta = beta;
            this.sigma = sigma;
            this.gamma = gamma;
            this.qRate = qRate;
            this.N = N;
        }

        @Override
        public int getDimension() {
            return 5;
        }

        @Override
        public void computeDerivatives(double t, double[] y, double[] yDot) {
            double S = y[0];
            double E = y[1];
            double I = y[2];

            yDot[0] = -beta * S * I / N;
            yDot[1] = beta * S * I / N - sigma * E;
            yDot[2] = sigma * E - gamma * I;
            yDot[3] = gamma * I - qRate * I;
            yDot[4] = qRate * I;
        }
    }
}
