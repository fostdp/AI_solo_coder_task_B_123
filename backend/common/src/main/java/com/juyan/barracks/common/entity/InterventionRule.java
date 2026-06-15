package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "intervention_rule")
public class InterventionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", length = 50)
    private String ruleCode;

    @Column(name = "tree_node_path", length = 200)
    private String treeNodePath;

    @Column(name = "condition_risk_level", length = 20)
    private String conditionRiskLevel;

    @Column(name = "condition_age_min")
    private Integer conditionAgeMin;

    @Column(name = "condition_age_max")
    private Integer conditionAgeMax;

    @Column(name = "condition_origin_region", length = 20)
    private String conditionOriginRegion;

    @Column(name = "condition_protein_risk_min", precision = 5, scale = 2)
    private BigDecimal conditionProteinRiskMin;

    @Column(name = "condition_vitamin_c_risk_min", precision = 5, scale = 2)
    private BigDecimal conditionVitaminCRiskMin;

    @Column(name = "condition_fat_risk_min", precision = 5, scale = 2)
    private BigDecimal conditionFatRiskMin;

    @Column(name = "recommendation_supplement", length = 200)
    private String recommendationSupplement;

    @Column(name = "recommendation_dosage", length = 200)
    private String recommendationDosage;

    @Column(name = "recommendation_duration_days")
    private Integer recommendationDurationDays;

    @Column(name = "supplement_cost_per_day", precision = 10, scale = 2)
    private BigDecimal supplementCostPerDay;

    @Column(name = "support_count")
    private Integer supportCount;

    @Column(name = "confidence", precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
