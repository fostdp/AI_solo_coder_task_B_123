package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.FoodItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FoodItemRepository extends JpaRepository<FoodItem, Long> {

    List<FoodItem> findByIsAvailableTrue();

    List<FoodItem> findByCategory(String category);

    @Query("SELECT f FROM FoodItem f WHERE f.isAvailable = true ORDER BY f.costPerKg ASC")
    List<FoodItem> findAllAvailableOrderByCost();
}
