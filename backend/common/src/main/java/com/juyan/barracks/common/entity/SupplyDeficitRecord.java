package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "supply_deficit_record")
public class SupplyDeficitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "barracks_id")
    private Long barracksId;

    @Column(name = "deficit_date")
    private LocalDate deficitDate;

    @Column(name = "food_category", length = 50)
    private String foodCategory;

    @Column(name = "food_name", length = 100)
    private String foodName;

    @Column(name = "standard_ration_g", precision = 12, scale = 2)
    private BigDecimal standardRationG;

    @Column(name = "actual_delivered_g", precision = 12, scale = 2)
    private BigDecimal actualDeliveredG;

    @Column(name = "deficit_amount_g", precision = 12, scale = 2)
    private BigDecimal deficitAmountG;

    @Column(name = "deficit_rate", precision = 5, scale = 2)
    private BigDecimal deficitRate;

    @Column(name = "weather_condition", length = 30)
    private String weatherCondition;

    @Column(name = "supply_route", length = 50)
    private String supplyRoute;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
