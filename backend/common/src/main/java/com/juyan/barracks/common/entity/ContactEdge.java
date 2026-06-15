package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "contact_edge")
public class ContactEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "barracks_id")
    private Long barracksId;

    @Column(name = "soldier_id_a")
    private Long soldierIdA;

    @Column(name = "soldier_id_b")
    private Long soldierIdB;

    @Column(name = "contact_type", length = 30)
    private String contactType;

    @Column(name = "contact_frequency_per_day")
    private Double contactFrequencyPerDay;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
