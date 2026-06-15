package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.MealRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MealRecordRepository extends JpaRepository<MealRecord, Long> {
    List<MealRecord> findBySoldierId(Long soldierId);

    List<MealRecord> findBySoldierIdAndMealTimeBetween(Long soldierId, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT SUM(m.proteinG) FROM MealRecord m WHERE m.soldierId = :soldierId AND m.mealTime BETWEEN :startTime AND :endTime")
    BigDecimal sumProteinBySoldierAndTime(@Param("soldierId") Long soldierId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    @Query("SELECT SUM(m.fatG) FROM MealRecord m WHERE m.soldierId = :soldierId AND m.mealTime BETWEEN :startTime AND :endTime")
    BigDecimal sumFatBySoldierAndTime(@Param("soldierId") Long soldierId,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);

    @Query("SELECT SUM(m.vitaminCMg) FROM MealRecord m WHERE m.soldierId = :soldierId AND m.mealTime BETWEEN :startTime AND :endTime")
    BigDecimal sumVitaminCBySoldierAndTime(@Param("soldierId") Long soldierId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    @Query("SELECT SUM(m.calorieKcal) FROM MealRecord m WHERE m.soldierId = :soldierId AND m.mealTime BETWEEN :startTime AND :endTime")
    BigDecimal sumCalorieBySoldierAndTime(@Param("soldierId") Long soldierId,
                                          @Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);
}
