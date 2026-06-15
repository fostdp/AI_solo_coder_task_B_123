package com.juyan.barracks.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionSensorMessage {
    private String sensorId;
    private String barracksCode;
    private String soldierCode;
    private BigDecimal proteinG;
    private BigDecimal fatG;
    private BigDecimal vitaminCMg;
    private LocalDateTime sampleTime;
}
