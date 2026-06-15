package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "soldier")
public class Soldier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "soldier_code", nullable = false, length = 50, unique = true)
    private String soldierCode;

    @Column(name = "barracks_id", nullable = false)
    private Long barracksId;

    @Column(nullable = false)
    private Integer age;

    @Column(length = 50)
    private String rank;

    @Column(name = "origin_region", length = 20)
    private String originRegion;

    @Column(nullable = false)
    private Point position;

    @Column(name = "position_x", nullable = false)
    private Integer positionX;

    @Column(name = "position_y", nullable = false)
    private Integer positionY;

    @Column(nullable = false, length = 20)
    private String status = "HEALTHY";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
