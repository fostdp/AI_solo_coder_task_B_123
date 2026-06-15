package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.SupplyDeficitRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SupplyDeficitRecordRepository extends JpaRepository<SupplyDeficitRecord, Long> {

    List<SupplyDeficitRecord> findByBarracksIdOrderByDeficitDateDesc(Long barracksId);

    @Query("SELECT d FROM SupplyDeficitRecord d WHERE d.deficitDate BETWEEN :start AND :end ORDER BY d.deficitDate ASC")
    List<SupplyDeficitRecord> findByDateRange(LocalDate start, LocalDate end);

    @Query("SELECT d FROM SupplyDeficitRecord d WHERE d.barracksId = :barracksId AND d.deficitRate >= 0.15 ORDER BY d.deficitDate DESC")
    List<SupplyDeficitRecord> findSignificantDeficits(Long barracksId);
}
