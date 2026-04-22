package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.NotificationTemplate;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findByTypeAndChannel(NotificationType type, NotificationChannel channel);
}
