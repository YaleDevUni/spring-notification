package com.example.notification.infrastructure.scheduler;

import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.infrastructure.repository.NotificationLockRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoverySchedulerTest {

    @Mock NotificationLockRepository notificationLockRepository;
    @Mock NotificationRepository notificationRepository;

    RecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RecoveryScheduler(notificationLockRepository, notificationRepository);
    }

    @Test
    @DisplayName("좀비 락 없을 때 — 복구 작업 없음")
    void no_zombie_locks_nothing_recovered() {
        when(notificationLockRepository.deleteExpiredLocksReturningIds()).thenReturn(List.of());

        scheduler.recoverZombieLocks();

        verify(notificationLockRepository).deleteExpiredLocksReturningIds();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("좀비 락 존재 시 — 해당 알림을 PENDING으로 복귀")
    void zombie_locks_reset_notifications_to_pending() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(notificationLockRepository.deleteExpiredLocksReturningIds()).thenReturn(List.of(id1, id2));

        scheduler.recoverZombieLocks();

        verify(notificationRepository).updateStatusIfMatch(eq(id1), eq(NotificationStatus.PROCESSING), eq(NotificationStatus.PENDING));
        verify(notificationRepository).updateStatusIfMatch(eq(id2), eq(NotificationStatus.PROCESSING), eq(NotificationStatus.PENDING));
    }
}
