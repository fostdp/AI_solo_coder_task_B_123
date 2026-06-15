package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.InterventionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InterventionRuleRepository extends JpaRepository<InterventionRule, Long> {

    @Query("SELECT r FROM InterventionRule r WHERE r.isActive = true ORDER BY r.confidence DESC")
    List<InterventionRule> findAllActiveOrderByConfidence();

    @Query("SELECT r FROM InterventionRule r WHERE r.isActive = true AND " +
           "(:riskLevel IS NULL OR r.conditionRiskLevel = :riskLevel) AND " +
           "(:age IS NULL OR (r.conditionAgeMin IS NULL OR r.conditionAgeMin <= :age) AND (r.conditionAgeMax IS NULL OR r.conditionAgeMax >= :age)) AND " +
           "(:region IS NULL OR r.conditionOriginRegion IS NULL OR r.conditionOriginRegion = :region) AND " +
           "(:proteinRisk IS NULL OR r.conditionProteinRiskMin IS NULL OR r.conditionProteinRiskMin <= :proteinRisk) AND " +
           "(:vitaminCRisk IS NULL OR r.conditionVitaminCRiskMin IS NULL OR r.conditionVitaminCRiskMin <= :vitaminCRisk) AND " +
           "(:fatRisk IS NULL OR r.conditionFatRiskMin IS NULL OR r.conditionFatRiskMin <= :fatRisk) " +
           "ORDER BY r.confidence DESC")
    List<InterventionRule> findMatchingRules(
            String riskLevel,
            Integer age,
            String region,
            BigDecimal proteinRisk,
            BigDecimal vitaminCRisk,
            BigDecimal fatRisk
    );
}
