package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.AssociationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssociationRuleRepository extends JpaRepository<AssociationRule, Long> {

    @Query("SELECT r FROM AssociationRule r WHERE r.isSignificant = true ORDER BY r.confidence DESC, r.lift DESC")
    List<AssociationRule> findSignificantRules();

    @Query("SELECT r FROM AssociationRule r WHERE r.isSignificant = true AND r.lift > 1.5 ORDER BY r.lift DESC")
    List<AssociationRule> findHighLiftRules();

    List<AssociationRule> findTop50ByOrderByAnalyzedAtDesc();
}
