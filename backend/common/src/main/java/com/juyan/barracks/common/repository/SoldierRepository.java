package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.Soldier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoldierRepository extends JpaRepository<Soldier, Long> {
    Optional<Soldier> findBySoldierCode(String soldierCode);

    List<Soldier> findByBarracksId(Long barracksId);

    @Query("SELECT s FROM Soldier s WHERE s.barracksId = :barracksId")
    List<Soldier> findAllByBarracksId(@Param("barracksId") Long barracksId);
}
