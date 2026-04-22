package com.example.notification.infrastructure.consumer;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.entity.NotificationAttempt;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.domain.processor.NotificationProcessor;
import com.example.notification.domain.processor.ProcessResult;
import com.example.notification.infrastructure.repository.NotificationAttemptRepository;
import com.example.notification.infrastructure.repository.NotificationLockRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Executors;

import static com.example.notification.domain.enums.NotificationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DbPollingConsumerTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationLockRepository notificationLockRepository;
    @Mock NotificationAttemptRepository notificationAttemptRepository;
    @Mock NotificationProcessor processor;
    @Mock TransactionTemplate transactionTemplate;

    DbPollingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DbPollingConsumer(
                notificationRepository, notificationLockRepository,
                notificationAttemptRepository, processor, transactionTemplate,
                10, 3, 60L, "test-instance",
                Executors.newSingleThreadExecutor()
        );

        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallbackWithoutResult cb =
                    new org.springframework.transaction.support.TransactionCallbackWithoutResult() {
                        @Override protected void doInTransactionWithoutResult(TransactionStatus s) {
                            ((java.util.function.Consumer<TransactionStatus>) inv.getArgument(0)).accept(s);
                        }
                    };
            cb.doInTransaction(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    private Notification pendingNotification() {
        return Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
    }

    @Test
    @DisplayName("pollOnce — 대기 알림 조회 후 PROCESSING 전환 + 락 저장 + 처리 제출")
    void pollOnce_fetches_and_submits() throws InterruptedException {
        Notification n = pendingNotification();
        when(notificationRepository.findPendingForUpdate(10)).thenReturn(List.of(n));
        when(notificationRepository.updateStatusIfMatch(any(), eq(PENDING), eq(PROCESSING))).thenReturn(1);
        when(processor.process(n)).thenReturn(new ProcessResult.Success());
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(0);

        consumer.pollOnce();

        Thread.sleep(200);
        verify(notificationRepository).findPendingForUpdate(10);
        verify(notificationRepository).updateStatusIfMatch(any(), eq(PENDING), eq(PROCESSING));
        verify(notificationLockRepository).save(any());
        verify(processor).process(n);
    }

    @Test
    @DisplayName("processNotification — 성공 시 SENT 상태 + SUCCESS 이력 저장")
    void processNotification_success() {
        Notification n = pendingNotification();
        when(processor.process(n)).thenReturn(new ProcessResult.Success());
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(0);

        consumer.processNotification(n);

        verify(processor).process(n);

        ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus().name()).isEqualTo("SUCCESS");

        verify(notificationRepository).updateStatusIfMatch(any(), eq(PROCESSING), eq(SENT));
        verify(notificationLockRepository).deleteById(any());
    }

    @Test
    @DisplayName("processNotification — 실패, max_retry 미만이면 FAILED")
    void processNotification_failure_below_max() {
        Notification n = pendingNotification();
        RuntimeException ex = new RuntimeException("SMTP error");
        when(processor.process(n)).thenReturn(new ProcessResult.Failure(ex));
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(0);

        consumer.processNotification(n);

        ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus().name()).isEqualTo("FAILURE");

        verify(notificationRepository).updateStatusIfMatch(any(), eq(PROCESSING), eq(FAILED));
        verify(notificationRepository, never()).updateStatusIfMatch(any(), eq(PROCESSING), eq(DEAD));
        verify(notificationLockRepository).deleteById(any());
    }

    @Test
    @DisplayName("processNotification — 실패, max_retry 도달하면 DEAD")
    void processNotification_failure_at_max() {
        Notification n = pendingNotification();
        RuntimeException ex = new RuntimeException("persistent error");
        when(processor.process(n)).thenReturn(new ProcessResult.Failure(ex));
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(2); // 3rd attempt

        consumer.processNotification(n);

        verify(notificationRepository).updateStatusIfMatch(any(), eq(PROCESSING), eq(DEAD));
        verify(notificationRepository, never()).updateStatusIfMatch(any(), eq(PROCESSING), eq(FAILED));
        verify(notificationLockRepository).deleteById(any());
    }
}
