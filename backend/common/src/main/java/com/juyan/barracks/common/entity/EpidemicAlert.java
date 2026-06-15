package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "epidemic_alert")
public class EpidemicAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "barracks_id", nullable = false)
    private Long barracksId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "alert_level", nullable = false, length = 20)
    private String alertLevel;

    @Column(name = "positive_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal positiveRate = BigDecimal.ZERO;

    @Column(name = "affected_count", nullable = false)
    private Integer affectedCount = 0;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    @Column(name = "cluster_center")
    private Point clusterCenter;

    @Column(name = "cluster_radius", precision = 10, scale = 2)
    private BigDecimal clusterRadius;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
