package com.juyan.barracks.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FecalSensorMessage {
    private String sensorId;
    private String barracksCode;
    private String soldierCode;
    private Boolean isPositive;
    private LocalDateTime sampleTime;
}
