package com.juyan.barracks.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.juyan.barracks.common.entity.Barracks;
import com.juyan.barracks.common.entity.EpidemicAlert;
import com.juyan.barracks.common.entity.NotificationLog;
import com.juyan.barracks.common.entity.NutritionRisk;
import com.juyan.barracks.common.entity.Soldier;
import com.juyan.barracks.common.repository.BarracksRepository;
import com.juyan.barracks.common.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final BarracksRepository barracksRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${notification.wechat.enabled:true}")
    private boolean wechatEnabled;

    @Value("${notification.wechat.webhook-url}")
    private String wechatWebhookUrl;

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.api-url}")
    private String smsApiUrl;

    @Value("${notification.sms.api-key}")
    private String smsApiKey;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Async
    public void sendNutritionAlert(Soldier soldier, NutritionRisk risk) {
        String title = "【营养告警】士兵维生素C/营养摄入不足";
        String content = buildNutritionAlertContent(soldier, risk);

        log.warn("营养告警: 士兵={}, 风险等级={}, 维C风险={}",
                soldier.getName(), risk.getRiskLevel(), risk.getVitaminCRiskScore());

        sendWechatNotification(title, content, null, risk.getId());
        sendSmsNotification("NUTRITION_ALERT", soldier.getName(), content, null, risk.getId());

        saveNotificationLog("NUTRITION_ALERT", "WECHAT", "兵营管理员", title + "\n" + content, null, risk.getId());
    }

    @Async
    public void sendEpidemicAlert(Barracks barracks, EpidemicAlert alert) {
        String title = "【疫情预警】" + barracks.getName() + " 检测到肠道感染聚集";
        String content = buildEpidemicAlertContent(barracks, alert);

        log.error("疫情预警: 兵营={}, 告警等级={}, 阳性率={}%",
                barracks.getName(), alert.getAlertLevel(),
                alert.getPositiveRate().multiply(BigDecimal.valueOf(100)));

        sendWechatNotification(title, content, alert.getId(), null);
        sendSmsNotification("EPIDEMIC_ALERT", barracks.getName(), content, alert.getId(), null);

        saveNotificationLog("EPIDEMIC_ALERT", "WECHAT", "兵营指挥官", title + "\n" + content, alert.getId(), null);
    }

    private String buildNutritionAlertContent(Soldier soldier, NutritionRisk risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("告警时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        sb.append("士兵编号: ").append(soldier.getSoldierCode()).append("\n");
        sb.append("士兵姓名: ").append(soldier.getName()).append("\n");
        sb.append("所属兵营ID: ").append(soldier.getBarracksId()).append("\n");
        sb.append("风险等级: ").append(risk.getRiskLevel()).append("\n");
        sb.append("综合风险评分: ").append(risk.getOverallRiskScore()).append("\n");
        sb.append("维生素C风险: ").append(risk.getVitaminCRiskScore()).append("\n");
        sb.append("蛋白质风险: ").append(risk.getProteinRiskScore()).append("\n");
        sb.append("脂肪风险: ").append(risk.getFatRiskScore()).append("\n\n");
        sb.append("膳食建议:\n").append(risk.getDietarySuggestion());
        return sb.toString();
    }

    private String buildEpidemicAlertContent(Barracks barracks, EpidemicAlert alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("告警时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        sb.append("兵营名称: ").append(barracks.getName()).append("\n");
        sb.append("兵营编号: ").append(barracks.getCode()).append("\n");
        sb.append("告警类型: 肠道感染聚集性疫情\n");
        sb.append("告警级别: ").append(alert.getAlertLevel()).append("\n");
        sb.append("检测样本数: ").append(alert.getTotalCount()).append("\n");
        sb.append("阳性样本数: ").append(alert.getAffectedCount()).append("\n");
        sb.append("阳性率: ").append(alert.getPositiveRate().multiply(BigDecimal.valueOf(100))).append("%\n");
        sb.append("告警阈值: 20%\n\n");
        sb.append("详细信息: ").append(alert.getDescription()).append("\n\n");
        sb.append("建议措施:\n");
        sb.append("1. 立即隔离阳性患者，送至医疗所诊治\n");
        sb.append("2. 加强饮水消毒和食品卫生监管\n");
        sb.append("3. 对受感染区域进行全面消毒\n");
        sb.append("4. 密切监测其他士兵健康状况\n");
        sb.append("5. 上报上级指挥机构");
        return sb.toString();
    }

    private void sendWechatNotification(String title, String content, Long alertId, Long riskId) {
        if (!wechatEnabled) {
            log.info("企业微信通知已禁用，跳过发送");
            return;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(wechatWebhookUrl);
            httpPost.setHeader("Content-Type", "application/json; charset=utf-8");

            Map<String, Object> markdown = new HashMap<>();
            markdown.put("content", "## " + title + "\n\n" + content.replace("\n", "\n> "));

            Map<String, Object> payload = new HashMap<>();
            payload.put("msgtype", "markdown");
            payload.put("markdown", markdown);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            httpPost.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.info("企业微信通知发送结果: {}", responseBody);
            }
        } catch (Exception e) {
            log.error("发送企业微信通知失败: {}", e.getMessage(), e);
        }
    }

    private void sendSmsNotification(String alertType, String recipient, String content,
                                      Long alertId, Long riskId) {
        if (!smsEnabled) {
            log.info("短信通知已禁用，跳过发送");
            return;
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(smsApiUrl);
            httpPost.setHeader("Content-Type", "application/json; charset=utf-8");
            httpPost.setHeader("Authorization", "Bearer " + smsApiKey);

            Map<String, Object> payload = new HashMap<>();
            payload.put("templateId", "YOUR_TEMPLATE_ID");
            payload.put("recipient", recipient);
            payload.put("content", content.substring(0, Math.min(content.length(), 500)));

            String jsonPayload = objectMapper.writeValueAsString(payload);
            httpPost.setEntity(new StringEntity(jsonPayload, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.info("短信通知发送结果: {}", responseBody);
            }
        } catch (Exception e) {
            log.error("发送短信通知失败: {}", e.getMessage(), e);
        }
    }

    private void saveNotificationLog(String notificationType, String channel,
                                      String recipient, String content,
                                      Long alertId, Long nutritionRiskId) {
        try {
            NotificationLog logEntity = new NotificationLog();
            logEntity.setNotificationType(notificationType);
            logEntity.setChannel(channel);
            logEntity.setRecipient(recipient);
            logEntity.setContent(content);
            logEntity.setAlertId(alertId);
            logEntity.setNutritionRiskId(nutritionRiskId);
            logEntity.setStatus("SENT");
            notificationLogRepository.save(logEntity);
        } catch (Exception e) {
            log.error("保存通知日志失败: {}", e.getMessage(), e);
        }
    }
}
