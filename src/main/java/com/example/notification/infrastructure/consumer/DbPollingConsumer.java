package com.example.notification.infrastructure.consumer;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.entity.NotificationAttempt;
import com.example.notification.domain.entity.NotificationLock;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.processor.NotificationProcessor;
import com.example.notification.domain.processor.ProcessResult;
import com.example.notification.infrastructure.repository.NotificationAttemptRepository;
import com.example.notification.infrastructure.repository.NotificationLockRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Profile("db")
public class DbPollingConsumer implements NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(DbPollingConsumer.class);

    private final NotificationRepository notificationRepository;
    private final NotificationLockRepository notificationLockRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final NotificationProcessor processor;
    private final TransactionTemplate transactionTemplate;

    private final int batchSize;
    private final int maxAttempts;
    private final long lockExpireSeconds;
    private final long pollIntervalSeconds;
    private final String instanceId;
    private final ExecutorService workerPool;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Spring-managed constructor
    public DbPollingConsumer(
            NotificationRepository notificationRepository,
            NotificationLockRepository notificationLockRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            NotificationProcessor processor,
            TransactionTemplate transactionTemplate,
            @Value("${notification.worker.batch-size:10}") int batchSize,
            @Value("${notification.retry.max-attempts:3}") int maxAttempts,
            @Value("${notification.lock.expire-seconds:60}") long lockExpireSeconds,
            @Value("${notification.worker.pool-size:10}") int workerPoolSize,
            @Value("${notification.worker.poll-interval-seconds:5}") long pollIntervalSeconds) {
        this(notificationRepository, notificationLockRepository, notificationAttemptRepository,
                processor, transactionTemplate, batchSize, maxAttempts, lockExpireSeconds,
                pollIntervalSeconds, UUID.randomUUID().toString(), Executors.newFixedThreadPool(workerPoolSize));
    }

    // Test-friendly constructor
    DbPollingConsumer(
            NotificationRepository notificationRepository,
            NotificationLockRepository notificationLockRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            NotificationProcessor processor,
            TransactionTemplate transactionTemplate,
            int batchSize,
            int maxAttempts,
            long lockExpireSeconds,
            String instanceId,
            ExecutorService workerPool) {
        this(notificationRepository, notificationLockRepository, notificationAttemptRepository,
                processor, transactionTemplate, batchSize, maxAttempts, lockExpireSeconds,
                5L, instanceId, workerPool);
    }

    private DbPollingConsumer(
            NotificationRepository notificationRepository,
            NotificationLockRepository notificationLockRepository,
            NotificationAttemptRepository notificationAttemptRepository,
            NotificationProcessor processor,
            TransactionTemplate transactionTemplate,
            int batchSize,
            int maxAttempts,
            long lockExpireSeconds,
            long pollIntervalSeconds,
            String instanceId,
            ExecutorService workerPool) {
        this.notificationRepository = notificationRepository;
        this.notificationLockRepository = notificationLockRepository;
        this.notificationAttemptRepository = notificationAttemptRepository;
        this.processor = processor;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.lockExpireSeconds = lockExpireSeconds;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.instanceId = instanceId;
        this.workerPool = workerPool;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(this::pollOnce, 0, pollIntervalSeconds, TimeUnit.SECONDS);
        log.info("[DbPollingConsumer] started instance={} batchSize={}", instanceId, batchSize);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        workerPool.shutdown();
    }

    @Override
    public void pollOnce() {
        try {
            List<Notification> batch = transactionTemplate.execute(status -> {
                List<Notification> pending = notificationRepository.findPendingForUpdate(batchSize);
                for (Notification n : pending) {
                    int updated = notificationRepository.updateStatusIfMatch(
                            n.getId(), NotificationStatus.PENDING, NotificationStatus.PROCESSING);
                    if (updated > 0) {
                        notificationLockRepository.save(
                                NotificationLock.create(n.getId(), instanceId, lockExpireSeconds));
                    }
                }
                return pending;
            });
            if (batch != null) {
                for (Notification n : batch) {
                    workerPool.submit(() -> processNotification(n));
                }
            }
        } catch (Exception e) {
            log.error("[DbPollingConsumer] pollOnce failed", e);
        }
    }

    void processNotification(Notification notification) {
        UUID id = notification.getId();
        try {
            int acquired = notificationRepository.updateStatusIfMatch(
                    id, NotificationStatus.PENDING, NotificationStatus.PROCESSING);
            if (acquired == 0) {
                log.debug("[DbPollingConsumer] skipped notification={} already taken", id);
                return;
            }
            notificationLockRepository.save(NotificationLock.create(id, instanceId, lockExpireSeconds));

            ProcessResult result = processor.process(notification);
            int attemptNumber = notificationAttemptRepository.countByNotificationId(id) + 1;

            if (result instanceof ProcessResult.Success) {
                notificationAttemptRepository.save(
                        NotificationAttempt.success(id, attemptNumber, instanceId));
                notificationRepository.updateStatusIfMatch(
                        id, NotificationStatus.PROCESSING, NotificationStatus.SENT);
                log.info("[DbPollingConsumer] SENT notification={} attempt={}", id, attemptNumber);
            } else {
                String reason = ((ProcessResult.Failure) result).cause().getMessage();
                notificationAttemptRepository.save(
                        NotificationAttempt.failure(id, attemptNumber, reason, instanceId));
                if (attemptNumber >= maxAttempts) {
                    notificationRepository.updateStatusIfMatch(
                            id, NotificationStatus.PROCESSING, NotificationStatus.DEAD);
                    log.warn("[DbPollingConsumer] DEAD notification={} attempts={}", id, attemptNumber);
                } else {
                    notificationRepository.updateStatusIfMatch(
                            id, NotificationStatus.PROCESSING, NotificationStatus.FAILED);
                    log.warn("[DbPollingConsumer] FAILED notification={} attempt={}/{}", id, attemptNumber, maxAttempts);
                }
            }
        } catch (Exception e) {
            log.error("[DbPollingConsumer] unexpected error notification={}", id, e);
        } finally {
            notificationLockRepository.deleteById(id);
        }
    }
}
