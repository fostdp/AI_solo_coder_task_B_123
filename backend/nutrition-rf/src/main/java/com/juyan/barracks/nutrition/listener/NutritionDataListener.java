package com.juyan.barracks.nutrition.listener;

import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.event.NutritionDataReceivedEvent;
import com.juyan.barracks.common.event.NutritionRiskComputedEvent;
import com.juyan.barracks.common.repository.NutritionRiskRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import com.juyan.barracks.nutrition.service.NutritionPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NutritionDataListener {

    private final SoldierRepository soldierRepository;
    private final NutritionRiskRepository nutritionRiskRepository;
    private final NutritionPredictionService nutritionPredictionService;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    @Transactional
    public void handleNutritionDataReceived(NutritionDataReceivedEvent event) {
        String soldierCode = event.getMessage().getSoldierCode();
        log.debug("收到营养数据事件, 士兵编号: {}", soldierCode);

        Optional<Soldier> soldierOpt = soldierRepository.findBySoldierCode(soldierCode);
        if (soldierOpt.isEmpty()) {
            log.warn("未找到士兵: {}", soldierCode);
            return;
        }

        Soldier soldier = soldierOpt.get();

        try {
            NutritionRisk risk = nutritionPredictionService.predictSoldierNutritionRisk(soldier);
            if (risk != null) {
                nutritionRiskRepository.markOldAsNotCurrent(soldier.getId());
                nutritionRiskRepository.save(risk);

                if ("HIGH".equals(risk.getRiskLevel()) || "CRITICAL".equals(risk.getRiskLevel())) {
                    eventPublisher.publishEvent(new NutritionRiskComputedEvent(this, soldier, risk));
                }

                log.info("即时营养预测完成, 士兵: {}, 风险等级: {}", soldierCode, risk.getRiskLevel());
            }
        } catch (Exception e) {
            log.error("即时营养预测失败, 士兵: {}, 错误: {}", soldierCode, e.getMessage(), e);
        }
    }
}
