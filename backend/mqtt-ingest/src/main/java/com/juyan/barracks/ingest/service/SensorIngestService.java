package com.juyan.barracks.ingest.service;

import com.juyan.barracks.common.dto.FecalSensorMessage;
import com.juyan.barracks.common.dto.NutritionSensorMessage;
import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.FecalSensorData;
import com.juyan.barracks.common.entity.NutritionSensorData;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.BarracksRepository;
import com.juyan.barracks.common.repository.FecalSensorDataRepository;
import com.juyan.barracks.common.repository.NutritionSensorDataRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorIngestService {

    private final NutritionSensorDataRepository nutritionSensorDataRepository;
    private final FecalSensorDataRepository fecalSensorDataRepository;
    private final BarracksRepository barracksRepository;
    private final SoldierRepository soldierRepository;

    public Long saveNutritionData(NutritionSensorMessage message) {
        Long barracksId = getBarracksIdByCode(message.getBarracksCode());
        Long soldierId = getSoldierIdByCode(message.getSoldierCode());

        NutritionSensorData data = new NutritionSensorData();
        data.setSensorId(message.getSensorId());
        data.setBarracksId(barracksId);
        data.setSoldierId(soldierId);
        data.setProteinG(message.getProteinG());
        data.setFatG(message.getFatG());
        data.setVitaminCMg(message.getVitaminCMg());
        data.setSampleTime(message.getSampleTime());

        NutritionSensorData saved = nutritionSensorDataRepository.save(data);
        log.info("营养数据已保存: id={}, sensorId={}", saved.getId(), saved.getSensorId());
        return saved.getId();
    }

    public Long saveFecalData(FecalSensorMessage message) {
        Long barracksId = getBarracksIdByCode(message.getBarracksCode());
        Long soldierId = getSoldierIdByCode(message.getSoldierCode());

        FecalSensorData data = new FecalSensorData();
        data.setSensorId(message.getSensorId());
        data.setBarracksId(barracksId);
        data.setSoldierId(soldierId);
        data.setIsPositive(message.getIsPositive());
        data.setSampleTime(message.getSampleTime());

        FecalSensorData saved = fecalSensorDataRepository.save(data);
        log.info("粪便隐血数据已保存: id={}, sensorId={}, isPositive={}", saved.getId(), saved.getSensorId(), saved.getIsPositive());
        return saved.getId();
    }

    private Long getBarracksIdByCode(String barracksCode) {
        if (barracksCode == null) {
            return null;
        }
        return barracksRepository.findByCode(barracksCode)
                .map(Barracks::getId)
                .orElse(null);
    }

    private Long getSoldierIdByCode(String soldierCode) {
        if (soldierCode == null) {
            return null;
        }
        return soldierRepository.findBySoldierCode(soldierCode)
                .map(Soldier::getId)
                .orElse(null);
    }
}
