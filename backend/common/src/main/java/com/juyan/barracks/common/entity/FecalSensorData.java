package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fecal_sensor_data")
public class FecalSensorData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sensor_id", nullable = false, length = 50)
    private String sensorId;

    @Column(name = "barracks_id", nullable = false)
    private Long barracksId;

    @Column(name = "soldier_id")
    private Long soldierId;

    @Column(name = "is_positive", nullable = false)
    private Boolean isPositive = false;

    @Column(name = "sample_time", nullable = false)
    private LocalDateTime sampleTime;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;
}
