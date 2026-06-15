package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.InterventionRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterventionRecommendationRepository extends JpaRepository<InterventionRecommendation, Long> {

    @Query("SELECT r FROM InterventionRecommendation r WHERE r.soldierId = :soldierId ORDER BY r.generatedAt DESC")
    List<InterventionRecommendation> findBySoldierIdOrderByGeneratedAtDesc(Long soldierId);

    @Query("SELECT r FROM InterventionRecommendation r WHERE r.status = 'PENDING' ORDER BY r.overallRiskScore DESC")
    List<InterventionRecommendation> findPendingRecommendations();

    List<InterventionRecommendation> findTop50ByOrderByGeneratedAtDesc();
}
