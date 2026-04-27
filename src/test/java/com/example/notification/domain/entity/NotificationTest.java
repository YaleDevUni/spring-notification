package com.example.notification.domain.entity;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private Notification pending() {
        return Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
    }

    @Test
    @DisplayName("생성 시 상태는 PENDING이다")
    void create_status_is_pending() {
        Notification n = pending();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING → PROCESSING 전이 성공")
    void markProcessing_from_pending_succeeds() {
        Notification n = pending();
        boolean result = n.markProcessing();
        assertThat(result).isTrue();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    @Test
    @DisplayName("PROCESSING 상태에서 markProcessing 재호출 시 false 반환")
    void markProcessing_from_processing_returns_false() {
        Notification n = pending();
        n.markProcessing();
        boolean result = n.markProcessing();
        assertThat(result).isFalse();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }
}
