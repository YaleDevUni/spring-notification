package com.example.notification.application.service;

import com.example.notification.application.dto.CreateNotificationRequest;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private InAppNotificationRepository inAppNotificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, inAppNotificationRepository);
    }

    private CreateNotificationRequest emailRequest() {
        return new CreateNotificationRequest("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
    }

    private Notification savedNotification(CreateNotificationRequest req) {
        return Notification.create(req.recipientId(), req.type(), req.channel(),
                req.refType(), req.refId(), req.scheduledAt());
    }

    // --- createNotification ---

    @Test
    @DisplayName("createNotification — 정상 접수 시 PENDING 알림 반환")
    void create_returns_saved_notification() {
        CreateNotificationRequest req = emailRequest();
        Notification saved = savedNotification(req);
        when(notificationRepository.saveAndFlush(any())).thenReturn(saved);

        Notification result = service.createNotification(req);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        verify(notificationRepository).saveAndFlush(any(Notification.class));
    }

    @Test
    @DisplayName("createNotification — UNIQUE 충돌 시 DuplicateNotificationException 발생")
    void create_throws_on_unique_violation() {
        when(notificationRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.createNotification(emailRequest()))
                .isInstanceOf(DuplicateNotificationException.class);
    }

    // --- getNotification ---

    @Test
    @DisplayName("getNotification — 존재하는 ID 조회 성공")
    void get_returns_notification() {
        UUID id = UUID.randomUUID();
        Notification n = savedNotification(emailRequest());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(n));

        Notification result = service.getNotification(id);

        assertThat(result).isEqualTo(n);
    }

    @Test
    @DisplayName("getNotification — 존재하지 않는 ID 시 NotificationNotFoundException 발생")
    void get_throws_when_not_found() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotification(id))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // --- listByRecipient ---

    @Test
    @DisplayName("listByRecipient — read 필터 없으면 전체 목록 반환")
    void list_without_filter_returns_all() {
        Notification n = savedNotification(emailRequest());
        when(notificationRepository.findByRecipientId("user-1")).thenReturn(List.of(n));

        List<Notification> result = service.listByRecipient("user-1", null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listByRecipient — read=false 이면 미읽음 목록 반환")
    void list_unread_filter() {
        Notification n = savedNotification(new CreateNotificationRequest("user-1",
                NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP, "E", "1", null));
        when(notificationRepository.findUnreadByRecipientId("user-1", NotificationChannel.IN_APP))
                .thenReturn(List.of(n));

        List<Notification> result = service.listByRecipient("user-1", false);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listByRecipient — read=true 이면 읽음 목록 반환")
    void list_read_filter() {
        Notification n = savedNotification(new CreateNotificationRequest("user-1",
                NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP, "E", "1", null));
        when(notificationRepository.findReadByRecipientId("user-1", NotificationChannel.IN_APP))
                .thenReturn(List.of(n));

        List<Notification> result = service.listByRecipient("user-1", true);

        assertThat(result).hasSize(1);
    }

    // --- markAsRead ---

    @Test
    @DisplayName("markAsRead — 업데이트 성공 시 true 반환")
    void markAsRead_returns_true_when_updated() {
        UUID id = UUID.randomUUID();
        when(inAppNotificationRepository.markReadById(id)).thenReturn(1);

        boolean result = service.markAsRead(id);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("markAsRead — 이미 읽음 또는 없을 때 false 반환 (멱등)")
    void markAsRead_returns_false_when_already_read() {
        UUID id = UUID.randomUUID();
        when(inAppNotificationRepository.markReadById(id)).thenReturn(0);

        boolean result = service.markAsRead(id);

        assertThat(result).isFalse();
    }

    // --- retryDead ---

    @Test
    @DisplayName("retryDead — DEAD 알림을 PENDING으로 변경 후 반환")
    void retryDead_marks_pending() {
        UUID id = UUID.randomUUID();
        Notification dead = savedNotification(emailRequest());
        dead.markProcessing();
        dead.markFailed();
        dead.markDead();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(dead));
        when(notificationRepository.save(dead)).thenReturn(dead);

        Notification result = service.retryDead(id);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("retryDead — DEAD가 아닌 알림은 IllegalStateException 발생")
    void retryDead_throws_when_not_dead() {
        UUID id = UUID.randomUUID();
        Notification pending = savedNotification(emailRequest());
        when(notificationRepository.findById(id)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.retryDead(id))
                .isInstanceOf(IllegalStateException.class);
    }
}
