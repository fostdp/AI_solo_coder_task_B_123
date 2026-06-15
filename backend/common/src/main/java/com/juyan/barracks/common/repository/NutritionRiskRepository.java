package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.NutritionRisk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface NutritionRiskRepository extends JpaRepository<NutritionRisk, Long> {
    List<NutritionRisk> findBySoldierId(Long soldierId);

    Optional<NutritionRisk> findBySoldierIdAndIsCurrentTrue(Long soldierId);

    List<NutritionRisk> findByRiskLevel(String riskLevel);

    @Query("SELECT nr FROM NutritionRisk nr WHERE nr.isCurrent = true")
    List<NutritionRisk> findAllCurrent();

    @Modifying
    @Transactional
    @Query("UPDATE NutritionRisk nr SET nr.isCurrent = false WHERE nr.soldierId = :soldierId AND nr.isCurrent = true")
    void markOldAsNotCurrent(@Param("soldierId") Long soldierId);
}
