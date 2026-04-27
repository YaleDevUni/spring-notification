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
import org.springframework.beans.factory.annotation.Autowired;
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

    // 단일 스레드: 동시에 여러 폴링 사이클이 실행되지 않도록 보장
    // scheduleWithFixedDelay: 이전 실행 완료 후 delay 시작 → 처리 지연 시 중첩 폴링 없음
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Spring-managed constructor
    @Autowired
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

    @Override
    @PostConstruct
    public void start() {
        scheduler.scheduleWithFixedDelay(this::pollOnce, 0, pollIntervalSeconds, TimeUnit.SECONDS);
        log.info("[DbPollingConsumer] started instance={} batchSize={}", instanceId, batchSize);
    }

    @Override
    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        workerPool.shutdown();
    }

    void pollOnce() {
        try {
            // TransactionTemplate 사용 이유: ScheduledExecutorService 스레드는 Spring 관리 밖이므로
            // @Transactional AOP 프록시가 동작하지 않음 — 명시적 트랜잭션 필요
            List<Notification> acquired = transactionTemplate.execute(status -> {
                List<Notification> pending = notificationRepository.findPendingForUpdate(batchSize);
                List<Notification> locked = new java.util.ArrayList<>();
                for (Notification n : pending) {
                    int updated = notificationRepository.updateStatusIfMatch(
                            n.getId(), NotificationStatus.PENDING, NotificationStatus.PROCESSING);
                    if (updated > 0) {
                        notificationLockRepository.save(NotificationLock.create(n.getId(), instanceId, lockExpireSeconds));
                        locked.add(n);
                    }
                }
                return locked;
            });
            if (acquired != null) {
                for (Notification n : acquired) {
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
            // processor.process: 자체 @Transactional — 발송 성공 시 in_app_notifications 저장까지 원자적
            // 처리
            ProcessResult result = processor.process(notification);
            // 결과 기록은 별도 트랜잭션: processor 트랜잭션과 분리해 발송 결과 기록 실패가 발송 롤백을 유발하지 않도록
            transactionTemplate.executeWithoutResult(status -> {
                // countByNotificationId: notifications.retry_count 컬럼 없이 attempts 테이블 집계로 대체
                int attemptNumber = (int) notificationAttemptRepository.countByNotificationId(id) + 1;
                recordAttempt(id, result, attemptNumber, instanceId);
                // 상태 업데이트와 락 삭제를 같은 트랜잭션으로 묶어 원자성 보장
                // finally 블록에서 분리 시: 상태는 갱신됐는데 락 삭제 실패 → RecoveryScheduler가 PROCESSING 알림을
                // PENDING으로 복귀시켜 중복 발송 시도
                notificationLockRepository.deleteById(id);
            });
        } catch (Exception e) {
            log.error("[DbPollingConsumer] unexpected error notification={}", id, e);
        }
    }

    @Override
    public void recordAttempt(UUID notificationId, ProcessResult result, int attemptNumber, String instanceId) {
        if (result instanceof ProcessResult.Success) {
            notificationAttemptRepository.save(NotificationAttempt.success(notificationId, attemptNumber, instanceId));
            notificationRepository.markSent(notificationId);
            log.info("[DbPollingConsumer] SENT notification={} attempt={}", notificationId, attemptNumber);
        } else {
            String reason = ((ProcessResult.Failure) result).cause().getMessage();
            notificationAttemptRepository.save(NotificationAttempt.failure(notificationId, attemptNumber, reason, instanceId));
            if (attemptNumber >= maxAttempts) {
                // 자동 재시도 한도 소진 → DEAD (수동 재시도 대기)
                notificationRepository.updateStatusIfMatch(
                        notificationId, NotificationStatus.PROCESSING, NotificationStatus.DEAD);
                log.warn("[DbPollingConsumer] DEAD notification={} attempts={}", notificationId, attemptNumber);
            } else {
                // 재시도 여지 있음 → 즉시 PENDING 복귀 (FAILED로 두지 않음)
                notificationRepository.updateStatusIfMatch(
                        notificationId, NotificationStatus.PROCESSING, NotificationStatus.PENDING);
                log.warn("[DbPollingConsumer] PENDING(retry) notification={} attempt={}/{}", notificationId,
                        attemptNumber, maxAttempts);
            }
        }
    }
}
