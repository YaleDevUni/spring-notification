package com.example.notification.domain.processor;

import com.example.notification.domain.entity.InAppNotification;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.infrastructure.channel.EmailChannelSender;
import com.example.notification.infrastructure.channel.InAppChannelSender;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProcessorTest {

    @Mock
    private EmailChannelSender emailSender;
    @Mock
    private InAppChannelSender inAppSender;
    @Mock
    private InAppNotificationRepository inAppRepository;

    private NotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationProcessorImpl(emailSender, inAppSender, inAppRepository);
    }

    @Test
    @DisplayName("EMAIL 채널 — EmailChannelSender 호출 후 Success 반환")
    void email_channel_returns_success() {
        Notification n = Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);

        ProcessResult result = processor.process(n);

        assertThat(result).isInstanceOf(ProcessResult.Success.class);
        verify(emailSender).send(n);
        verifyNoInteractions(inAppSender, inAppRepository);
    }

    @Test
    @DisplayName("IN_APP 채널 — InAppChannelSender 호출 + InAppNotification 저장 후 Success 반환")
    void inApp_channel_saves_record_and_returns_success() {
        Notification n = Notification.create("user-1", NotificationType.EVENT_REMINDER,
                NotificationChannel.IN_APP, "EVENT", "evt-1", null);

        ProcessResult result = processor.process(n);

        assertThat(result).isInstanceOf(ProcessResult.Success.class);
        verify(inAppSender).send(n);
        verify(inAppRepository).save(any(InAppNotification.class));
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("EMAIL 채널 — 전송 예외 발생 시 Failure 반환")
    void email_channel_exception_returns_failure() {
        Notification n = Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
        RuntimeException ex = new RuntimeException("SMTP error");
        doThrow(ex).when(emailSender).send(n);

        ProcessResult result = processor.process(n);

        assertThat(result).isInstanceOf(ProcessResult.Failure.class);
        assertThat(((ProcessResult.Failure) result).cause()).isEqualTo(ex);
    }

    @Test
    @DisplayName("IN_APP 채널 — 전송 예외 발생 시 Failure 반환")
    void inApp_channel_exception_returns_failure() {
        Notification n = Notification.create("user-1", NotificationType.EVENT_REMINDER,
                NotificationChannel.IN_APP, "EVENT", "evt-1", null);
        RuntimeException ex = new RuntimeException("DB error");
        doThrow(ex).when(inAppSender).send(n);

        ProcessResult result = processor.process(n);

        assertThat(result).isInstanceOf(ProcessResult.Failure.class);
        assertThat(((ProcessResult.Failure) result).cause()).isEqualTo(ex);
    }
}
