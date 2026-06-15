package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.FecalSensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FecalSensorDataRepository extends JpaRepository<FecalSensorData, Long> {
    List<FecalSensorData> findByBarracksId(Long barracksId);

    List<FecalSensorData> findByBarracksIdAndSampleTimeBetween(Long barracksId, LocalDateTime startTime, LocalDateTime endTime);

    @Query("SELECT COUNT(f) FROM FecalSensorData f WHERE f.barracksId = :barracksId AND f.sampleTime BETWEEN :startTime AND :endTime")
    Long countByBarracksIdAndSampleTimeBetween(@Param("barracksId") Long barracksId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    @Query("SELECT COUNT(f) FROM FecalSensorData f WHERE f.barracksId = :barracksId AND f.isPositive = true AND f.sampleTime BETWEEN :startTime AND :endTime")
    Long countPositiveByBarracksIdAndSampleTimeBetween(@Param("barracksId") Long barracksId,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);

    @Query("SELECT f FROM FecalSensorData f WHERE f.barracksId = :barracksId AND f.sampleTime BETWEEN :startTime AND :endTime ORDER BY f.sampleTime DESC")
    List<FecalSensorData> findByBarracksIdAndTimeRange(@Param("barracksId") Long barracksId,
                                                       @Param("startTime") LocalDateTime startTime,
                                                       @Param("endTime") LocalDateTime endTime);
}
