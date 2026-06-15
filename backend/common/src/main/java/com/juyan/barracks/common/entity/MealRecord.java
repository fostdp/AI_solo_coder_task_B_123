package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "meal_record")
public class MealRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "soldier_id", nullable = false)
    private Long soldierId;

    @Column(name = "meal_type", nullable = false, length = 20)
    private String mealType;

    @Column(name = "meal_time", nullable = false)
    private LocalDateTime mealTime;

    @Column(name = "protein_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal proteinG = BigDecimal.ZERO;

    @Column(name = "fat_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal fatG = BigDecimal.ZERO;

    @Column(name = "vitamin_c_mg", nullable = false, precision = 10, scale = 2)
    private BigDecimal vitaminCMg = BigDecimal.ZERO;

    @Column(name = "calorie_kcal", nullable = false, precision = 10, scale = 2)
    private BigDecimal calorieKcal = BigDecimal.ZERO;

    @Column(name = "food_items", columnDefinition = "TEXT")
    private String foodItems;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
