package com.juyan.barracks.intervention.listener;

import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.event.NutritionRiskComputedEvent;
import com.juyan.barracks.intervention.config.InterventionConfig;
import com.juyan.barracks.intervention.dto.RecommendationResultDTO;
import com.juyan.barracks.intervention.service.NutritionInterventionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskUpdateListener {

    private final NutritionInterventionService interventionService;
    private final InterventionConfig config;

    @Async
    @EventListener
    public void handleNutritionRiskComputed(NutritionRiskComputedEvent event) {
        Soldier soldier = event.getSoldier();
        NutritionRisk risk = event.getNutritionRisk();

        if (soldier == null || risk == null) {
            log.warn("收到营养风险事件但数据不完整，跳过处理");
            return;
        }

        log.info("收到营养风险计算事件: soldierId={}, soldierName={}, riskLevel={}, overallScore={}",
                soldier.getId(), soldier.getName(), risk.getRiskLevel(), risk.getOverallRiskScore());

        try {
            BigDecimal overallRisk = risk.getOverallRiskScore();
            double threshold = config.getHighRiskThreshold();

            boolean isHighRisk = false;
            if (overallRisk != null && overallRisk.doubleValue() >= threshold) {
                isHighRisk = true;
            }

            String riskLevel = risk.getRiskLevel();
            if (riskLevel != null) {
                String upper = riskLevel.toUpperCase();
                if (upper.contains("HIGH") || upper.contains("CRITICAL")
                        || upper.contains("高") || upper.contains("极高")) {
                    isHighRisk = true;
                }
            }

            if (isHighRisk) {
                log.info("士兵 {} 风险等级≥HIGH (score={}, threshold={})，自动生成干预推荐",
                        soldier.getName(), overallRisk, threshold);

                RecommendationResultDTO result =
                        interventionService.generateForSoldierAndRisk(soldier, risk);

                log.info("自动干预推荐生成完成: soldierId={}, supplements={}, totalCost={}",
                        soldier.getId(), result.getSupplements().size(), result.getTotalEstimatedCost());
            } else {
                log.debug("士兵 {} 风险等级未达到阈值 (score={}, threshold={})，跳过自动推荐",
                        soldier.getName(), overallRisk, threshold);
            }
        } catch (Exception e) {
            log.error("处理营养风险事件失败: soldierId={}", soldier.getId(), e);
        }
    }
}
