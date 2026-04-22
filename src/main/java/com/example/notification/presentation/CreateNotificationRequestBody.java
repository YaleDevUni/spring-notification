package com.example.notification.presentation;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateNotificationRequestBody(
        @NotBlank String recipientId,
        @NotNull NotificationType type,
        @NotNull NotificationChannel channel,
        @NotBlank String refType,
        @NotBlank String refId,
        Instant scheduledAt
) {}
