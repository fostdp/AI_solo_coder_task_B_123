package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "meal_plan_item")
public class MealPlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_plan_id")
    private MealPlan mealPlan;

    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "meal_type", length = 20)
    private String mealType;

    @Column(name = "food_item_id")
    private Long foodItemId;

    @Column(name = "food_name", length = 100)
    private String foodName;

    @Column(name = "quantity_grams", precision = 10, scale = 2)
    private BigDecimal quantityGrams;

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "protein_g", precision = 10, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", precision = 10, scale = 2)
    private BigDecimal fatG;

    @Column(name = "vitamin_c_mg", precision = 10, scale = 2)
    private BigDecimal vitaminCMg;

    @Column(name = "calorie_kcal", precision = 10, scale = 2)
    private BigDecimal calorieKcal;
}
