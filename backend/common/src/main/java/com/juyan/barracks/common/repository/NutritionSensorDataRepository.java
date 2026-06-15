package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.NutritionSensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NutritionSensorDataRepository extends JpaRepository<NutritionSensorData, Long> {
    List<NutritionSensorData> findByBarracksId(Long barracksId);

    List<NutritionSensorData> findBySoldierId(Long soldierId);

    List<NutritionSensorData> findByBarracksIdAndSampleTimeBetween(Long barracksId, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT n FROM NutritionSensorData n WHERE n.soldierId = :soldierId AND n.sampleTime BETWEEN :startTime AND :endTime ORDER BY n.sampleTime DESC")
    List<NutritionSensorData> findBySoldierIdAndSampleTimeBetween(@Param("soldierId") Long soldierId,
                                                                   @Param("startTime") LocalDateTime startTime,
                                                                   @Param("endTime") LocalDateTime endTime);
}
