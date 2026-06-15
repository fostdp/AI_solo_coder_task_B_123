package com.juyan.barracks.common.event;

import com.juyan.barracks.common.dto.FecalSensorMessage;
import com.juyan.barracks.common.dto.NutritionSensorMessage;
import com.juyan.barracks.common.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SpringEventsTest {

    @Test
    void testNutritionDataReceivedEvent() {
        NutritionSensorMessage message = NutritionSensorMessage.builder()
                .sensorId("N001")
                .soldierCode("S001")
                .proteinG(70.5)
                .fatG(50.2)
                .vitaminCMg(75.0)
                .calorieKcal(3200.0)
                .timestamp(LocalDateTime.now())
                .build();

        NutritionDataReceivedEvent event = new NutritionDataReceivedEvent(this, message, 100L);

        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals("N001", event.getMessage().getSensorId());
        assertEquals("S001", event.getMessage().getSoldierCode());
        assertEquals(70.5, event.getMessage().getProteinG());
        assertEquals(50.2, event.getMessage().getFatG());
        assertEquals(75.0, event.getMessage().getVitaminCMg());
        assertEquals(3200.0, event.getMessage().getCalorieKcal());
        assertEquals(100L, event.getNutritionDataId());
    }

    @Test
    void testFecalDataReceivedEvent() {
        FecalSensorMessage message = FecalSensorMessage.builder()
                .sensorId("F001")
                .barracksCode("B001")
                .totalCount(50)
                .positiveCount(10)
                .positiveRate(0.2)
                .timestamp(LocalDateTime.now())
                .build();

        FecalDataReceivedEvent event = new FecalDataReceivedEvent(this, message, 200L);

        assertNotNull(event);
        assertNotNull(event.getMessage());
        assertEquals("F001", event.getMessage().getSensorId());
        assertEquals("B001", event.getMessage().getBarracksCode());
        assertEquals(50, event.getMessage().getTotalCount());
        assertEquals(10, event.getMessage().getPositiveCount());
        assertEquals(0.2, event.getMessage().getPositiveRate());
        assertEquals(200L, event.getFecalDataId());
    }

    @Test
    void testNutritionRiskComputedEvent() {
        Soldier soldier = new Soldier();
        soldier.setId(1L);
        soldier.setName("张三");
        soldier.setSoldierCode("S001");

        NutritionRisk risk = new NutritionRisk();
        risk.setId(10L);
        risk.setSoldierId(1L);
        risk.setRiskLevel("HIGH");
        risk.setOverallRiskScore(BigDecimal.valueOf(0.75));
        risk.setDietarySuggestion("增加蛋白质摄入");

        NutritionRiskComputedEvent event = new NutritionRiskComputedEvent(this, soldier, risk);

        assertNotNull(event);
        assertNotNull(event.getSoldier());
        assertNotNull(event.getNutritionRisk());
        assertEquals("张三", event.getSoldier().getName());
        assertEquals("HIGH", event.getNutritionRisk().getRiskLevel());
        assertEquals(0.75, event.getNutritionRisk().getOverallRiskScore().doubleValue(), 0.001);
    }

    @Test
    void testEpidemicAlertTriggeredEvent() {
        EpidemicAlert alert = new EpidemicAlert();
        alert.setId(1L);
        alert.setBarracksId(2L);
        alert.setAlertLevel("HIGH");
        alert.setPositiveRate(BigDecimal.valueOf(0.25));
        alert.setAffectedCount(10);
        alert.setTotalCount(40);
        alert.setDescription("肠道感染聚集性疫情");

        EpidemicAlertTriggeredEvent event = new EpidemicAlertTriggeredEvent(this, alert);

        assertNotNull(event);
        assertNotNull(event.getAlert());
        assertEquals(1L, event.getAlert().getId());
        assertEquals("HIGH", event.getAlertLevel());
        assertEquals(0.25, event.getPositiveRate(), 0.001);
        assertEquals("肠道感染聚集性疫情", event.getAlert().getDescription());
    }
}
