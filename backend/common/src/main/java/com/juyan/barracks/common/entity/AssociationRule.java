package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "association_rule")
public class AssociationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "antecedent_items", length = 500)
    private String antecedentItems;

    @Column(name = "consequent_items", length = 500)
    private String consequentItems;

    @Column(name = "support", precision = 6, scale = 4)
    private BigDecimal support;

    @Column(name = "confidence", precision = 6, scale = 4)
    private BigDecimal confidence;

    @Column(name = "lift", precision = 8, scale = 4)
    private BigDecimal lift;

    @Column(name = "antecedent_count")
    private Integer antecedentCount;

    @Column(name = "consequent_count")
    private Integer consequentCount;

    @Column(name = "both_count")
    private Integer bothCount;

    @Column(name = "total_transactions")
    private Integer totalTransactions;

    @Column(name = "rule_description", length = 500)
    private String ruleDescription;

    @Column(name = "is_significant")
    private Boolean isSignificant;

    @Column(name = "analyzed_at")
    private LocalDateTime analyzedAt = LocalDateTime.now();
}
