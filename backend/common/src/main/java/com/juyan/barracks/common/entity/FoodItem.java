package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "food_item")
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(name = "protein_per_100g", precision = 10, scale = 2)
    private BigDecimal proteinPer100g;

    @Column(name = "fat_per_100g", precision = 10, scale = 2)
    private BigDecimal fatPer100g;

    @Column(name = "vitamin_c_per_100g_mg", precision = 10, scale = 2)
    private BigDecimal vitaminCPer100gMg;

    @Column(name = "calorie_per_100g_kcal", precision = 10, scale = 2)
    private BigDecimal caloriePer100gKcal;

    @Column(name = "cost_per_kg", precision = 10, scale = 2)
    private BigDecimal costPerKg;

    @Column(name = "is_available")
    private Boolean isAvailable = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
