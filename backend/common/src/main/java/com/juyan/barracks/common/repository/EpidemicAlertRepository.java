package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.EpidemicAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EpidemicAlertRepository extends JpaRepository<EpidemicAlert, Long> {
    List<EpidemicAlert> findByBarracksId(Long barracksId);

    List<EpidemicAlert> findByStatus(String status);

    List<EpidemicAlert> findByBarracksIdAndStatus(Long barracksId, String status);

    @Query("SELECT e FROM EpidemicAlert e WHERE e.status = 'ACTIVE' AND e.barracksId = :barracksId AND e.startTime BETWEEN :startTime AND :endTime")
    List<EpidemicAlert> findActiveAlertsByBarracksAndTimeRange(@Param("barracksId") Long barracksId,
                                                                @Param("startTime") LocalDateTime startTime,
                                                                @Param("endTime") LocalDateTime endTime);
}
