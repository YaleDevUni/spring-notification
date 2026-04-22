package com.example.notification.presentation;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;

import java.time.Instant;
import java.util.UUID;

// API 응답 전용 DTO — 엔티티 직접 노출 시 발생하는 문제 해결:
//   1. DB 스키마 변경이 API breaking change로 전파되는 것을 차단
//   2. open-in-view=false 환경에서 LAZY 필드 직렬화 오류 방지
//   3. 채널별 의미 있는 필드(read)만 선택적으로 노출
public record NotificationResponse(
        UUID id,
        String recipientId,
        NotificationType type,
        NotificationChannel channel,
        String refType,
        String refId,
        NotificationStatus status,
        Instant scheduledAt,
        Instant sentAt,
        Instant createdAt,
        Boolean read  // IN_APP 전용: null(EMAIL), false(안읽음), true(읽음)
) {
    public static NotificationResponse from(Notification n) {
        Boolean read = null;
        // inAppNotification은 fetch join으로 이미 로드된 경우에만 접근 (LAZY 세션 이슈 없음)
        if (n.getChannel() == NotificationChannel.IN_APP && n.getInAppNotification() != null) {
            read = n.getInAppNotification().isRead();
        }
        return new NotificationResponse(
                n.getId(), n.getRecipientId(), n.getType(), n.getChannel(),
                n.getRefType(), n.getRefId(), n.getStatus(),
                n.getScheduledAt(), n.getSentAt(), n.getCreatedAt(),
                read
        );
    }
}
