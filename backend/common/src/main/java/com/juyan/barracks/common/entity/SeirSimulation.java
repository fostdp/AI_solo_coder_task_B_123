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
@Table(name = "seir_simulation")
public class SeirSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "barracks_id")
    private Long barracksId;

    @Column(name = "simulation_name", length = 200)
    private String simulationName;

    @Column(name = "virus_type", length = 50)
    private String virusType = "诺如病毒";

    @Column(name = "simulation_days")
    private Integer simulationDays;

    @Column(name = "initial_infected_count")
    private Integer initialInfectedCount;

    @Column(name = "transmission_rate_beta", precision = 8, scale = 4)
    private BigDecimal transmissionRateBeta;

    @Column(name = "latent_rate_sigma", precision = 8, scale = 4)
    private BigDecimal latentRateSigma;

    @Column(name = "recovery_rate_gamma", precision = 8, scale = 4)
    private BigDecimal recoveryRateGamma;

    @Column(name = "isolation_effectiveness", precision = 5, scale = 2)
    private BigDecimal isolationEffectiveness;

    @Column(name = "quarantine_start_day")
    private Integer quarantineStartDay;

    @Column(name = "max_infected_count")
    private Integer maxInfectedCount;

    @Column(name = "total_infected_count")
    private Integer totalInfectedCount;

    @Column(name = "peak_day")
    private Integer peakDay;

    @Column(name = "is_completed")
    private Boolean isCompleted = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "simulation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeirTimePoint> timePoints = new ArrayList<>();
}
