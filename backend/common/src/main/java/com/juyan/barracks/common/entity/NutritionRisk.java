package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "nutrition_risk")
public class NutritionRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "soldier_id", nullable = false)
    private Long soldierId;

    @Column(name = "risk_level", nullable = false, length = 20)
    private String riskLevel = "LOW";

    @Column(name = "protein_risk_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal proteinRiskScore = BigDecimal.ZERO;

    @Column(name = "fat_risk_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal fatRiskScore = BigDecimal.ZERO;

    @Column(name = "vitamin_c_risk_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal vitaminCRiskScore = BigDecimal.ZERO;

    @Column(name = "overall_risk_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal overallRiskScore = BigDecimal.ZERO;

    @Column(name = "dietary_suggestion", columnDefinition = "TEXT")
    private String dietarySuggestion;

    @CreationTimestamp
    @Column(name = "predicted_at", nullable = false, updatable = false)
    private LocalDateTime predictedAt;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;
}
