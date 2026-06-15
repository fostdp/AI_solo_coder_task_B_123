package com.juyan.barracks.seir.listener;

import com.juyan.barracks.common.event.EpidemicAlertTriggeredEvent;
import com.juyan.barracks.seir.service.SeirSimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EpidemicAlertListener {

    private final SeirSimulationService seirSimulationService;

    @Async
    @EventListener
    public void onEpidemicAlertTriggered(EpidemicAlertTriggeredEvent event) {
        if (event.getAlert() == null || event.getAlert().getBarracksId() == null) {
            log.warn("收到疫情告警事件但数据不完整，跳过SEIR模拟");
            return;
        }

        Long barracksId = event.getAlert().getBarracksId();
        String alertLevel = event.getAlertLevel();
        Double positiveRate = event.getPositiveRate();

        log.info("收到疫情告警事件: 兵营={}, 告警级别={}, 阳性率={}%, 自动启动SEIR传播模拟评估",
                barracksId, alertLevel, String.format("%.2f", positiveRate != null ? positiveRate * 100 : 0));

        try {
            int initialInfected = Math.max(1, (int) Math.ceil(
                    (positiveRate != null ? positiveRate : 0.05) * 50));

            SeirSimulationService.SimulationRequest request = SeirSimulationService.SimulationRequest.builder()
                    .barracksId(barracksId)
                    .initialInfected(initialInfected)
                    .days(60)
                    .quarantineStartDay(3)
                    .isolationEffectiveness(0.6)
                    .build();

            SeirSimulationService.SimulationComparison comparison = seirSimulationService.runSimulation(request);

            if (comparison != null) {
                log.info("疫情告警触发SEIR模拟完成: 兵营={}, 感染减少={}%, 峰值延迟={}天, 摘要: {}",
                        barracksId,
                        String.format("%.2f", comparison.getInfectionReductionPercent() != null ?
                                comparison.getInfectionReductionPercent() : 0),
                        comparison.getPeakDelayDays() != null ? comparison.getPeakDelayDays() : 0,
                        comparison.getSummary());
            } else {
                log.warn("疫情告警触发SEIR模拟返回空结果: 兵营={}", barracksId);
            }

        } catch (Exception e) {
            log.error("疫情告警触发SEIR模拟失败: 兵营={}, 错误={}", barracksId, e.getMessage(), e);
        }
    }
}
