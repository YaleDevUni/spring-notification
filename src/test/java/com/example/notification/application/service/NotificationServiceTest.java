package com.example.notification.application.service;

import com.example.notification.application.dto.CreateNotificationRequest;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import com.example.notification.infrastructure.repository.NotificationAttemptRepository;
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

// NotificationService 단위 테스트: Repository를 Mock으로 격리하여 서비스 유스케이스 로직만 검증
// @Transactional이 붙은 메서드도 Mock 환경에서는 트랜잭션 없이 실행됨 — 트랜잭션 경계 검증은 통합 테스트로
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private InAppNotificationRepository inAppNotificationRepository;
    @Mock
    private NotificationAttemptRepository notificationAttemptRepository;

    // maxAttempts=3, manualMaxAttempts=2 로 고정하여 경계값 테스트 명확화
    private static final int MAX_ATTEMPTS = 3;
    private static final int MANUAL_MAX_ATTEMPTS = 2;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository, inAppNotificationRepository,
                notificationAttemptRepository, MAX_ATTEMPTS, MANUAL_MAX_ATTEMPTS);
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
        // getNotification은 findByIdWithInApp 사용 (open-in-view=false + LAZY 안전 보장)
        when(notificationRepository.findByIdWithInApp(id)).thenReturn(Optional.of(n));

        Notification result = service.getNotification(id);

        assertThat(result).isEqualTo(n);
    }

    @Test
    @DisplayName("getNotification — 존재하지 않는 ID 시 NotificationNotFoundException 발생")
    void get_throws_when_not_found() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findByIdWithInApp(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotification(id))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // --- listByRecipient ---

    @Test
    @DisplayName("listByRecipient — channel/read 필터 없으면 전체 목록 반환")
    void list_without_filter_returns_all() {
        Notification n = savedNotification(emailRequest());
        when(notificationRepository.findByRecipientId("user-1")).thenReturn(List.of(n));

        List<Notification> result = service.listByRecipient("user-1", null, null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listByRecipient — channel=IN_APP, read=false 이면 미읽음 목록 반환")
    void list_inapp_unread_filter() {
        Notification n = savedNotification(new CreateNotificationRequest("user-1",
                NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP, "E", "1", null));
        when(notificationRepository.findUnreadByRecipientId("user-1", NotificationChannel.IN_APP))
                .thenReturn(List.of(n));

        List<Notification> result = service.listByRecipient("user-1", NotificationChannel.IN_APP, false);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listByRecipient — channel=IN_APP, read=true 이면 읽음 목록 반환")
    void list_inapp_read_filter() {
        Notification n = savedNotification(new CreateNotificationRequest("user-1",
                NotificationType.EVENT_REMINDER, NotificationChannel.IN_APP, "E", "1", null));
        when(notificationRepository.findReadByRecipientId("user-1", NotificationChannel.IN_APP))
                .thenReturn(List.of(n));

        List<Notification> result = service.listByRecipient("user-1", NotificationChannel.IN_APP, true);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listByRecipient — IN_APP 외 채널에 read 필터 시 예외")
    void list_non_inapp_with_read_filter_throws() {
        assertThatThrownBy(() -> service.listByRecipient("user-1", NotificationChannel.EMAIL, false))
                .isInstanceOf(IllegalArgumentException.class);
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
    @DisplayName("retryDead — DEAD 알림을 PENDING으로 변경 후 반환 (자동 재시도 이력 3회 = 수동 한도 미소진)")
    void retryDead_marks_pending() {
        UUID id = UUID.randomUUID();
        Notification dead = savedNotification(emailRequest());
        dead.markDead();
        when(notificationRepository.findByIdWithInApp(id)).thenReturn(Optional.of(dead));
        when(notificationRepository.save(dead)).thenReturn(dead);
        // totalAttempts=3 == maxAttempts → manualAttemptsDone=0 < manualMaxAttempts=2 → 재시도 허용
        when(notificationAttemptRepository.countByNotificationId(id)).thenReturn(3L);

        Notification result = service.retryDead(id);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("retryDead — DEAD가 아닌 알림은 IllegalStateException 발생")
    void retryDead_throws_when_not_dead() {
        UUID id = UUID.randomUUID();
        Notification pending = savedNotification(emailRequest());
        when(notificationRepository.findByIdWithInApp(id)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.retryDead(id))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("retryDead — 수동 재시도 한도(2회) 초과 시 IllegalStateException 발생")
    void retryDead_throws_when_manual_limit_exceeded() {
        UUID id = UUID.randomUUID();
        Notification dead = savedNotification(emailRequest());
        dead.markDead();
        when(notificationRepository.findByIdWithInApp(id)).thenReturn(Optional.of(dead));
        // totalAttempts=5 = maxAttempts(3) + manualMaxAttempts(2) → manualAttemptsDone=2 >= 2 → 거부
        when(notificationAttemptRepository.countByNotificationId(id)).thenReturn(5L);

        assertThatThrownBy(() -> service.retryDead(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Manual retry limit exceeded");
    }

    @Test
    @DisplayName("retryDead — 수동 재시도 1회 남은 경우 허용 (totalAttempts=4)")
    void retryDead_allows_last_manual_retry() {
        UUID id = UUID.randomUUID();
        Notification dead = savedNotification(emailRequest());
        dead.markDead();
        when(notificationRepository.findByIdWithInApp(id)).thenReturn(Optional.of(dead));
        // totalAttempts=4 → manualAttemptsDone=1 < 2 → 재시도 허용
        when(notificationAttemptRepository.countByNotificationId(id)).thenReturn(4L);
        when(notificationRepository.save(dead)).thenReturn(dead);

        Notification result = service.retryDead(id);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }
}
