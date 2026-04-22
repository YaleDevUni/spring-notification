package com.example.notification.infrastructure.scheduler;

import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.infrastructure.repository.NotificationLockRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class RecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecoveryScheduler.class);

    private final NotificationLockRepository notificationLockRepository;
    private final NotificationRepository notificationRepository;

    public RecoveryScheduler(NotificationLockRepository notificationLockRepository,
                             NotificationRepository notificationRepository) {
        this.notificationLockRepository = notificationLockRepository;
        this.notificationRepository = notificationRepository;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverZombieLocks() {
        List<UUID> expiredIds = notificationLockRepository.deleteExpiredLocksReturningIds();
        if (expiredIds.isEmpty()) return;

        log.warn("[RecoveryScheduler] recovering {} zombie lock(s)", expiredIds.size());
        for (UUID id : expiredIds) {
            int updated = notificationRepository.updateStatusIfMatch(
                    id, NotificationStatus.PROCESSING, NotificationStatus.PENDING);
            if (updated > 0) {
                log.info("[RecoveryScheduler] reset notification={} to PENDING", id);
            }
        }
    }
}
