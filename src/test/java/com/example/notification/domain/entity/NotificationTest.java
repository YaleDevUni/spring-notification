package com.example.notification.domain.entity;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

    @Test
    @DisplayName("markSent 호출 시 SENT + sentAt 기록")
    void markSent_sets_status_and_sent_at() {
        Notification n = pending();
        n.markProcessing();
        n.markSent();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed 호출 시 FAILED")
    void markFailed_sets_failed() {
        Notification n = pending();
        n.markProcessing();
        n.markFailed();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    @DisplayName("markDead 호출 시 DEAD")
    void markDead_sets_dead() {
        Notification n = pending();
        n.markDead();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DEAD);
    }

    @Test
    @DisplayName("markPending 호출 시 PENDING 복귀")
    void markPending_restores_pending() {
        Notification n = pending();
        n.markDead();
        n.markPending();
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("scheduledAt 없으면 즉시 처리 대상")
    void isReadyToProcess_no_schedule_returns_true() {
        Notification n = pending();
        assertThat(n.isReadyToProcess()).isTrue();
    }

    @Test
    @DisplayName("scheduledAt이 미래면 처리 대상 아님")
    void isReadyToProcess_future_schedule_returns_false() {
        Notification n = Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1",
                Instant.now().plusSeconds(3600));
        assertThat(n.isReadyToProcess()).isFalse();
    }

    @Test
    @DisplayName("scheduledAt이 과거면 처리 대상")
    void isReadyToProcess_past_schedule_returns_true() {
        Notification n = Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1",
                Instant.now().minusSeconds(1));
        assertThat(n.isReadyToProcess()).isTrue();
    }

    @Test
    @DisplayName("PENDING이 아니면 처리 대상 아님")
    void isReadyToProcess_non_pending_returns_false() {
        Notification n = pending();
        n.markProcessing();
        assertThat(n.isReadyToProcess()).isFalse();
    }
}
