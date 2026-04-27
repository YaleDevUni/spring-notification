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

// DbPollingConsumer 단위 테스트
// TransactionTemplate을 Mock으로 교체해 실제 DB 없이 폴링/처리 로직만 검증
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
        // 테스트 전용 생성자: instanceId="test-instance", 단일 워커 스레드로 제어 가능하게 구성
        consumer = new DbPollingConsumer(
                notificationRepository, notificationLockRepository,
                notificationAttemptRepository, processor, transactionTemplate,
                10, 3, 60L, "test-instance",
                Executors.newSingleThreadExecutor()
        );

        // lenient(): 모든 테스트에서 사용되지 않더라도 UnnecessaryStubbingException 발생 방지
        // TransactionTemplate.execute 콜백을 즉시 실행 — 트랜잭션 없이 람다 내부 로직만 테스트
        lenient().when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(mock(TransactionStatus.class));
        });
        // executeWithoutResult는 void Consumer<TransactionStatus> 형태라 별도 처리 필요
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
    @DisplayName("pollOnce — 락 획득한 알림만 워커에 제출")
    void pollOnce_fetches_and_submits() throws InterruptedException {
        Notification n = pendingNotification();
        when(notificationRepository.findPendingForUpdate(10)).thenReturn(List.of(n));
        // updateStatusIfMatch=1: 락 획득 성공 → acquired 리스트에 포함 → 워커 제출
        when(notificationRepository.updateStatusIfMatch(any(), eq(PENDING), eq(PROCESSING))).thenReturn(1);
        when(processor.process(n)).thenReturn(new ProcessResult.Success());
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(0L);

        consumer.pollOnce();

        // 워커 스레드 비동기 실행 완료 대기 — ExecutorService.submit() 후 결과는 비동기
        Thread.sleep(200);
        verify(notificationRepository).findPendingForUpdate(10);
        verify(notificationRepository).updateStatusIfMatch(any(), eq(PENDING), eq(PROCESSING));
        verify(notificationLockRepository).save(any());
        verify(processor).process(n);
    }

    @Test
    @DisplayName("pollOnce — updateStatusIfMatch=0이면 해당 알림은 워커에 제출하지 않음")
    void pollOnce_skips_unacquired() throws InterruptedException {
        Notification n = pendingNotification();
        when(notificationRepository.findPendingForUpdate(10)).thenReturn(List.of(n));
        // updateStatusIfMatch=0: 다른 인스턴스가 이미 획득 → acquired 리스트에서 제외
        when(notificationRepository.updateStatusIfMatch(any(), eq(PENDING), eq(PROCESSING))).thenReturn(0);

        consumer.pollOnce();
        Thread.sleep(200);

        verify(notificationLockRepository, never()).save(any());
        verifyNoInteractions(processor);
    }

    @Test
    @DisplayName("processNotification — 성공 시 SENT + SUCCESS 이력 저장 + 락 삭제 (같은 트랜잭션)")
    void processNotification_success() {
        Notification n = pendingNotification();
        when(processor.process(n)).thenReturn(new ProcessResult.Success());
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(0L);

        consumer.processNotification(n);

        verify(processor).process(n);

        ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus().name()).isEqualTo("SUCCESS");

        verify(notificationRepository).markSent(any());
        // deleteById는 결과 기록 트랜잭션 내부에서 호출 — finally 블록 아님
        verify(notificationLockRepository).deleteById(any());
    }

    @Test
    @DisplayName("processNotification — 실패, max_retry 미만이면 PENDING 복귀 (자동 재시도)")
    void processNotification_failure_below_max() {
        Notification n = pendingNotification();
        RuntimeException ex = new RuntimeException("SMTP error");
        when(processor.process(n)).thenReturn(new ProcessResult.Failure(ex));
        // 0회 이력 → attemptNumber=1 < maxAttempts=3 → PENDING 복귀
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(0L);

        consumer.processNotification(n);

        ArgumentCaptor<NotificationAttempt> captor = ArgumentCaptor.forClass(NotificationAttempt.class);
        verify(notificationAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus().name()).isEqualTo("FAILURE");

        // FAILED가 아닌 PENDING으로 즉시 복귀 — findPendingForUpdate가 다음 사이클에 재수집
        verify(notificationRepository).updateStatusIfMatch(any(), eq(PROCESSING), eq(PENDING));
        verify(notificationRepository, never()).updateStatusIfMatch(any(), eq(PROCESSING), eq(DEAD));
        verify(notificationLockRepository).deleteById(any());
    }

    @Test
    @DisplayName("processNotification — 실패, max_retry 도달하면 DEAD (수동 재시도 대기)")
    void processNotification_failure_at_max() {
        Notification n = pendingNotification();
        RuntimeException ex = new RuntimeException("persistent error");
        when(processor.process(n)).thenReturn(new ProcessResult.Failure(ex));
        // 2회 이력 → attemptNumber=3 == maxAttempts=3 → DEAD
        when(notificationAttemptRepository.countByNotificationId(any())).thenReturn(2L);

        consumer.processNotification(n);

        verify(notificationRepository).updateStatusIfMatch(any(), eq(PROCESSING), eq(DEAD));
        verify(notificationRepository, never()).updateStatusIfMatch(any(), eq(PROCESSING), eq(PENDING));
        verify(notificationLockRepository).deleteById(any());
    }
}
