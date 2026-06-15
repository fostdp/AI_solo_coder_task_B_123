package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "nutrition_sensor_data")
public class NutritionSensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Column(name = "barracks_id", nullable = false)
    private Long barracksId;

    @Column(name = "soldier_id")
    private Long soldierId;

    @Column(name = "protein_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal proteinG = BigDecimal.ZERO;

    @Column(name = "fat_g", nullable = false, precision = 10, scale = 2)
    private BigDecimal fatG = BigDecimal.ZERO;

    @Column(name = "vitamin_c_mg", nullable = false, precision = 10, scale = 2)
    private BigDecimal vitaminCMg = BigDecimal.ZERO;

    @Column(name = "sample_time", nullable = false)
    private LocalDateTime sampleTime;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
}
