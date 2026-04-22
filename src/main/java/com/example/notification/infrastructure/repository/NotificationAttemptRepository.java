package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.NotificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationAttemptRepository extends JpaRepository<NotificationAttempt, UUID> {

    @Query("SELECT COUNT(a) FROM NotificationAttempt a WHERE a.notificationId = :notificationId")
    long countByNotificationId(@Param("notificationId") UUID notificationId);
}
