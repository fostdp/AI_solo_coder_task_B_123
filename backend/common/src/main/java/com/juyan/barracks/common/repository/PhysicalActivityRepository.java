package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.PhysicalActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PhysicalActivityRepository extends JpaRepository<PhysicalActivity, Long> {
    List<PhysicalActivity> findBySoldierId(Long soldierId);

    List<PhysicalActivity> findBySoldierIdAndActivityDateBetween(Long soldierId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT SUM(p.calorieBurned) FROM PhysicalActivity p WHERE p.soldierId = :soldierId AND p.activityDate BETWEEN :startDate AND :endDate")
    BigDecimal sumCalorieBurnedBySoldierAndDate(@Param("soldierId") Long soldierId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
}
