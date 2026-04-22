package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.InAppNotification;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/notification_test",
        "spring.datasource.username=user",
        "spring.datasource.password=password",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false"
})
class NotificationRepositoryTest {

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    InAppNotificationRepository inAppNotificationRepository;

    private Notification saveEmail(String recipientId, String refId) {
        return notificationRepository.save(
                Notification.create(recipientId, NotificationType.LECTURE_START,
                        NotificationChannel.EMAIL, "LECTURE", refId, null));
    }

    private Notification saveInApp(String recipientId, String refId) {
        Notification n = notificationRepository.save(
                Notification.create(recipientId, NotificationType.EVENT_REMINDER,
                        NotificationChannel.IN_APP, "EVENT", refId, null));
        inAppNotificationRepository.save(InAppNotification.create(n));
        return n;
    }

    @Test
    @DisplayName("PENDING 알림을 SKIP LOCKED로 조회한다")
    void findPendingForUpdate_returns_pending() {
        saveEmail("repo-test-user-1", "lec-r1");
        saveEmail("repo-test-user-2", "lec-r2");

        List<Notification> result = notificationRepository.findPendingForUpdate(10);

        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(n -> n.getStatus() == NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("수신자별 전체 알림 목록을 조회한다")
    void findByRecipientId_returns_all() {
        String recipientId = "repo-test-recipient-A";
        saveEmail(recipientId, "lec-ra1");
        saveInApp(recipientId, "evt-ra1");
        saveEmail("other-user", "lec-ra2");

        List<Notification> result = notificationRepository.findByRecipientId(recipientId);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(n -> n.getRecipientId().equals(recipientId));
    }

    @Test
    @DisplayName("읽지 않은 IN_APP 알림만 조회한다")
    void findUnreadByRecipientId_returns_unread_only() {
        String recipientId = "repo-test-recipient-B";
        Notification unread = saveInApp(recipientId, "evt-rb1");
        Notification read = saveInApp(recipientId, "evt-rb2");
        inAppNotificationRepository.markReadById(read.getId());

        List<Notification> result = notificationRepository.findUnreadByRecipientId(
                recipientId, NotificationChannel.IN_APP);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(unread.getId());
    }

    @Test
    @DisplayName("읽은 IN_APP 알림만 조회한다")
    void findReadByRecipientId_returns_read_only() {
        String recipientId = "repo-test-recipient-C";
        saveInApp(recipientId, "evt-rc1");
        Notification read = saveInApp(recipientId, "evt-rc2");
        inAppNotificationRepository.markReadById(read.getId());

        List<Notification> result = notificationRepository.findReadByRecipientId(
                recipientId, NotificationChannel.IN_APP);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(read.getId());
    }

    @Test
    @DisplayName("상태 조건부 UPDATE — PENDING → PROCESSING 성공 시 1 반환")
    void updateStatusIfMatch_matching_returns_one() {
        Notification n = saveEmail("repo-test-user-upd1", "lec-upd1");

        int updated = notificationRepository.updateStatusIfMatch(
                n.getId(), NotificationStatus.PENDING, NotificationStatus.PROCESSING);

        assertThat(updated).isEqualTo(1);
    }

    @Test
    @DisplayName("상태 조건부 UPDATE — 이미 PROCESSING이면 0 반환")
    void updateStatusIfMatch_not_matching_returns_zero() {
        Notification n = saveEmail("repo-test-user-upd2", "lec-upd2");
        n.markProcessing();
        notificationRepository.save(n);

        int updated = notificationRepository.updateStatusIfMatch(
                n.getId(), NotificationStatus.PENDING, NotificationStatus.PROCESSING);

        assertThat(updated).isEqualTo(0);
    }
}
