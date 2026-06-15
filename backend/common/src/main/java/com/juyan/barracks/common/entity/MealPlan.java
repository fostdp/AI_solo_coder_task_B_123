package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "meal_plan")
public class MealPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "barracks_id")
    private Long barracksId;

    @Column(name = "plan_name", length = 200)
    private String planName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "target_soldier_count")
    private Integer targetSoldierCount;

    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "daily_protein_g", precision = 10, scale = 2)
    private BigDecimal dailyProteinG;

    @Column(name = "daily_fat_g", precision = 10, scale = 2)
    private BigDecimal dailyFatG;

    @Column(name = "daily_vitamin_c_mg", precision = 10, scale = 2)
    private BigDecimal dailyVitaminCMg;

    @Column(name = "daily_calorie_kcal", precision = 10, scale = 2)
    private BigDecimal dailyCalorieKcal;

    @Column(name = "solver_status", length = 50)
    private String solverStatus;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "mealPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MealPlanItem> items = new ArrayList<>();
}
