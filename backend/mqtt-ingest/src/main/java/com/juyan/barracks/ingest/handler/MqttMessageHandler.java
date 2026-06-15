package com.juyan.barracks.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.juyan.barracks.common.dto.FecalSensorMessage;
import com.juyan.barracks.common.dto.NutritionSensorMessage;
import com.juyan.barracks.common.event.FecalDataReceivedEvent;
import com.juyan.barracks.common.event.NutritionDataReceivedEvent;
import com.juyan.barracks.ingest.service.SensorIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler {

    private final SensorIngestService sensorIngestService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleMessage(Message<String> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        String payload = message.getPayload();

        log.info("收到MQTT消息 - Topic: {}, Payload: {}", topic, payload);

        try {
            if (topic != null && topic.contains("nutrition")) {
                NutritionSensorMessage nutritionMessage = objectMapper.readValue(payload, NutritionSensorMessage.class);
                Long nutritionDataId = sensorIngestService.saveNutritionData(nutritionMessage);
                eventPublisher.publishEvent(new NutritionDataReceivedEvent(this, nutritionMessage, nutritionDataId));
                log.info("营养数据已保存并发布事件: sensorId={}, dataId={}", nutritionMessage.getSensorId(), nutritionDataId);
            } else if (topic != null && topic.contains("fecal")) {
                FecalSensorMessage fecalMessage = objectMapper.readValue(payload, FecalSensorMessage.class);
                Long fecalDataId = sensorIngestService.saveFecalData(fecalMessage);
                eventPublisher.publishEvent(new FecalDataReceivedEvent(this, fecalMessage, fecalDataId));
                log.info("粪便隐血数据已保存并发布事件: sensorId={}, dataId={}", fecalMessage.getSensorId(), fecalDataId);
            } else {
                log.warn("未知的MQTT主题: {}", topic);
            }
        } catch (Exception e) {
            log.error("处理MQTT消息失败 - Topic: {}, Error: {}", topic, e.getMessage(), e);
        }
    }
}
