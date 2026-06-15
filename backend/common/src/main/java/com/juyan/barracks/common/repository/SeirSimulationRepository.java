package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.SeirSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeirSimulationRepository extends JpaRepository<SeirSimulation, Long> {

    List<SeirSimulation> findByBarracksIdOrderByCreatedAtDesc(Long barracksId);

    @Query("SELECT s FROM SeirSimulation s WHERE s.barracksId = :barracksId AND s.isCompleted = true ORDER BY s.createdAt DESC")
    List<SeirSimulation> findCompletedByBarracksId(Long barracksId);

    List<SeirSimulation> findTop10ByOrderByCreatedAtDesc();
}
