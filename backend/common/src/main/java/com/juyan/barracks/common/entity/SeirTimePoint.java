package com.juyan.barracks.common.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "seir_time_point")
public class SeirTimePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulation_id")
    private SeirSimulation simulation;

    @Column(name = "day")
    private Integer day;

    @Column(name = "susceptible_count")
    private Integer susceptibleCount;

    @Column(name = "exposed_count")
    private Integer exposedCount;

    @Column(name = "infected_count")
    private Integer infectedCount;

    @Column(name = "recovered_count")
    private Integer recoveredCount;

    @Column(name = "quarantined_count")
    private Integer quarantinedCount;
}
