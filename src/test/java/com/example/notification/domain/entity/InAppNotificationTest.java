package com.example.notification.domain.entity;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InAppNotificationTest {

    private Notification notification() {
        return Notification.create("user-1", NotificationType.EVENT_REMINDER,
                NotificationChannel.IN_APP, "EVENT", "evt-1", null);
    }

    @Test
    @DisplayName("생성 시 읽지 않은 상태")
    void create_is_unread() {
        InAppNotification inApp = InAppNotification.create(notification());
        assertThat(inApp.isRead()).isFalse();
        assertThat(inApp.getReadAt()).isNull();
    }

    @Test
    @DisplayName("최초 읽음 처리 성공 — true 반환 + readAt 기록")
    void markRead_first_time_succeeds() {
        InAppNotification inApp = InAppNotification.create(notification());
        boolean result = inApp.markRead();
        assertThat(result).isTrue();
        assertThat(inApp.isRead()).isTrue();
        assertThat(inApp.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 읽은 경우 markRead 재호출 시 false 반환 — 멱등 보장")
    void markRead_already_read_returns_false() {
        InAppNotification inApp = InAppNotification.create(notification());
        inApp.markRead();
        boolean result = inApp.markRead();
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("readAt은 최초 읽음 시각을 유지한다")
    void markRead_preserves_first_read_at() throws InterruptedException {
        InAppNotification inApp = InAppNotification.create(notification());
        inApp.markRead();
        var firstReadAt = inApp.getReadAt();
        Thread.sleep(10);
        inApp.markRead();
        assertThat(inApp.getReadAt()).isEqualTo(firstReadAt);
    }
}
