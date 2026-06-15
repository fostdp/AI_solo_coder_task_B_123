package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "intervention_recommendation")
public class InterventionRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "soldier_id")
    private Long soldierId;

    @Column(name = "soldier_name", length = 100)
    private String soldierName;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "overall_risk_score", precision = 5, scale = 4)
    private BigDecimal overallRiskScore;

    @Column(name = "matched_rule_ids", length = 500)
    private String matchedRuleIds;

    @Column(name = "recommended_supplements", length = 1000)
    private String recommendedSupplements;

    @Column(name = "recommended_dosage", length = 1000)
    private String recommendedDosage;

    @Column(name = "estimated_cost_total", precision = 12, scale = 2)
    private BigDecimal estimatedCostTotal;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "implemented_at")
    private LocalDateTime implementedAt;
}
