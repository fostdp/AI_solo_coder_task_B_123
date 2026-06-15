package com.juyan.barracks.intervention.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TreeTrainResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private int trainingSamples;
    private int maxDepth;
    private String splitCriterion;
    private double crossValidationAccuracy;
    private int ruleCount;
    private boolean usedBuiltinFallback;
    private String message;
    private LocalDateTime trainedAt;
    private long trainingTimeMs;
}
