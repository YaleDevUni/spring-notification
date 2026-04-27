package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.entity.NotificationTemplate;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.infrastructure.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelSenderTest {

    @Mock NotificationTemplateRepository templateRepository;

    private Notification emailNotification() {
        return Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
    }

    private Notification inAppNotification() {
        return Notification.create("user-1", NotificationType.EVENT_REMINDER,
                NotificationChannel.IN_APP, "EVENT", "evt-1", null);
    }

    @Test
    @DisplayName("EmailChannelSender — 템플릿 존재 시 렌더링하여 전송")
    void email_send_with_template() {
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.LECTURE_START, NotificationChannel.EMAIL,
                "강의 시작 알림", "안녕하세요 {recipientId}님, {refId} 강의가 곧 시작됩니다.");
        when(templateRepository.findByTypeAndChannel(NotificationType.LECTURE_START, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(template));

        EmailChannelSender sender = new EmailChannelSender(templateRepository);
        assertThatCode(() -> sender.send(emailNotification())).doesNotThrowAnyException();
        verify(templateRepository).findByTypeAndChannel(NotificationType.LECTURE_START, NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("EmailChannelSender — 템플릿 없으면 fallback 로그로 전송")
    void email_send_without_template_fallback() {
        when(templateRepository.findByTypeAndChannel(NotificationType.LECTURE_START, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());

        EmailChannelSender sender = new EmailChannelSender(templateRepository);
        assertThatCode(() -> sender.send(emailNotification())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("InAppChannelSender — 템플릿 존재 시 렌더링하여 전송")
    void inApp_send_with_template() {
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP,
                null, "{refType} 이벤트({refId})가 곧 시작됩니다.");
        when(templateRepository.findByTypeAndChannel(NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(template));

        InAppChannelSender sender = new InAppChannelSender(templateRepository);
        assertThatCode(() -> sender.send(inAppNotification())).doesNotThrowAnyException();
        verify(templateRepository).findByTypeAndChannel(NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP);
    }

    @Test
    @DisplayName("InAppChannelSender — 템플릿 없으면 fallback 로그로 전송")
    void inApp_send_without_template_fallback() {
        when(templateRepository.findByTypeAndChannel(NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());

        InAppChannelSender sender = new InAppChannelSender(templateRepository);
        assertThatCode(() -> sender.send(inAppNotification())).doesNotThrowAnyException();
    }
}
