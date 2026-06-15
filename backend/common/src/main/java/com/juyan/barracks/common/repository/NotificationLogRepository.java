package com.juyan.barracks.common.repository;

import com.juyan.barracks.common.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByStatus(String status);

    List<NotificationLog> findByNotificationType(String notificationType);

    List<NotificationLog> findByAlertId(Long alertId);

    List<NotificationLog> findByNutritionRiskId(Long nutritionRiskId);
}
