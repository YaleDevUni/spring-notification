package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ChannelSenderTest {

    private final EmailChannelSender emailSender = new EmailChannelSender();
    private final InAppChannelSender inAppSender = new InAppChannelSender();

    private Notification emailNotification() {
        return Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
    }

    private Notification inAppNotification() {
        return Notification.create("user-1", NotificationType.EVENT_REMINDER,
                NotificationChannel.IN_APP, "EVENT", "evt-1", null);
    }

    @Test
    @DisplayName("EmailChannelSender — 예외 없이 전송 완료")
    void email_send_completes_without_exception() {
        assertThatCode(() -> emailSender.send(emailNotification()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("InAppChannelSender — 예외 없이 전송 완료")
    void inApp_send_completes_without_exception() {
        assertThatCode(() -> inAppSender.send(inAppNotification()))
                .doesNotThrowAnyException();
    }
}
