package com.juyan.barracks.epidemic.listener;

import com.juyan.barracks.common.event.FecalDataReceivedEvent;
import com.juyan.barracks.epidemic.service.EpidemicDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class FecalDataListener {

    private final EpidemicDetectionService epidemicDetectionService;

    private final AtomicInteger eventCount = new AtomicInteger(0);

    private static final int SCAN_THRESHOLD = 100;

    @EventListener
    public void handleFecalDataReceived(FecalDataReceivedEvent event) {
        int count = eventCount.incrementAndGet();
        log.debug("收到粪便传感器数据事件，累计计数: {}", count);

        if (count >= SCAN_THRESHOLD) {
            log.info("粪便数据事件数达到阈值 {}, 触发聚合扫描", SCAN_THRESHOLD);
            triggerScan();
            eventCount.set(0);
        }
    }

    @Scheduled(fixedRate = 300000)
    public void scheduledScanTrigger() {
        int count = eventCount.get();
        if (count > 0) {
            log.info("定时触发聚合扫描，当前累计事件数: {}", count);
            triggerScan();
            eventCount.set(0);
        }
    }

    private void triggerScan() {
        try {
            epidemicDetectionService.runEpidemicScan();
        } catch (Exception e) {
            log.error("触发聚合扫描失败: {}", e.getMessage(), e);
        }
    }
}
