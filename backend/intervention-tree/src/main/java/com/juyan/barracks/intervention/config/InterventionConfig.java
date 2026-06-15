package com.juyan.barracks.intervention.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Data
@Configuration
@EnableScheduling
@ConfigurationProperties(prefix = "intervention")
public class InterventionConfig {

    private double highRiskThreshold = 0.5;

    private int defaultDurationDays = 14;

    private String retrainCron = "0 0 3 * * ?";

    private int treeMaxDepth = 10;

    private int treeMinSamplesSplit = 5;

    private int crossValidationFolds = 5;

    private int minHistorySamplesForTraining = 50;

    private boolean enableBuiltinRulesFallback = true;
}
