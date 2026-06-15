package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.MealPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {

    List<MealPlan> findByBarracksIdOrderByCreatedAtDesc(Long barracksId);

    @Query("SELECT m FROM MealPlan m WHERE m.barracksId = :barracksId AND m.isActive = true ORDER BY m.createdAt DESC")
    Optional<MealPlan> findActiveByBarracksId(Long barracksId);

    List<MealPlan> findTop10ByOrderByCreatedAtDesc();
}
