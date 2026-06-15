package com.juyan.barracks.alert.listener;

import com.juyan.barracks.alert.service.NotificationService;
import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.EpidemicAlert;
import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.event.EpidemicAlertTriggeredEvent;
import com.juyan.barracks.common.event.NutritionRiskComputedEvent;
import com.juyan.barracks.common.repository.BarracksRepository;
import com.juyan.barracks.common.repository.SoldierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final NotificationService notificationService;
    private final BarracksRepository barracksRepository;
    private final SoldierRepository soldierRepository;

    @EventListener
    public void onNutritionRiskComputed(NutritionRiskComputedEvent event) {
        Soldier soldier = event.getSoldier();
        NutritionRisk risk = event.getNutritionRisk();

        if (soldier == null) {
            Optional<Soldier> soldierOpt = soldierRepository.findById(risk.getSoldierId());
            if (soldierOpt.isEmpty()) {
                log.warn("未找到士兵信息，无法发送营养告警: soldierId={}", risk.getSoldierId());
                return;
            }
            soldier = soldierOpt.get();
        }

        log.info("收到营养风险计算事件，士兵: {}, 风险等级: {}", soldier.getName(), risk.getRiskLevel());

        if ("HIGH".equals(risk.getRiskLevel()) || "CRITICAL".equals(risk.getRiskLevel())) {
            notificationService.sendNutritionAlert(soldier, risk);
        }
    }

    @EventListener
    public void onEpidemicAlertTriggered(EpidemicAlertTriggeredEvent event) {
        EpidemicAlert alert = event.getAlert();

        Optional<Barracks> barracksOpt = barracksRepository.findById(alert.getBarracksId());
        if (barracksOpt.isEmpty()) {
            log.warn("未找到兵营信息，无法发送疫情告警: barracksId={}", alert.getBarracksId());
            return;
        }

        Barracks barracks = barracksOpt.get();
        log.info("收到疫情告警触发事件，兵营: {}, 告警等级: {}", barracks.getName(), alert.getAlertLevel());

        notificationService.sendEpidemicAlert(barracks, alert);
    }
}
