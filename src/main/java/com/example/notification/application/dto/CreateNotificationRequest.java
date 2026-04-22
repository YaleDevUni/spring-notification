package com.example.notification.application.dto;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import java.time.Instant;

public record CreateNotificationRequest(
        String recipientId,
        NotificationType type,
        NotificationChannel channel,
        String refType,
        String refId,
        Instant scheduledAt
) {}
