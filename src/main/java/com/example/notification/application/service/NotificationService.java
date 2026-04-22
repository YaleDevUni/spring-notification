package com.example.notification.application.service;

import com.example.notification.application.dto.CreateNotificationRequest;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import com.example.notification.infrastructure.repository.NotificationAttemptRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final InAppNotificationRepository inAppNotificationRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final int maxAttempts;
    private final int manualMaxAttempts;

    public NotificationService(NotificationRepository notificationRepository,
                               InAppNotificationRepository inAppNotificationRepository,
                               NotificationAttemptRepository notificationAttemptRepository,
                               @Value("${notification.retry.max-attempts:3}") int maxAttempts,
                               @Value("${notification.retry.manual-max-attempts:2}") int manualMaxAttempts) {
        this.notificationRepository = notificationRepository;
        this.inAppNotificationRepository = inAppNotificationRepository;
        this.notificationAttemptRepository = notificationAttemptRepository;
        this.maxAttempts = maxAttempts;
        this.manualMaxAttempts = manualMaxAttempts;
    }

    @Transactional
    public Notification createNotification(CreateNotificationRequest req) {
        try {
            Notification n = Notification.create(req.recipientId(), req.type(), req.channel(),
                    req.refType(), req.refId(), req.scheduledAt());
            return notificationRepository.saveAndFlush(n);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateNotificationException();
        }
    }

    @Transactional(readOnly = true)
    public Notification getNotification(UUID id) {
        // findByIdWithInApp: open-in-view=false 환경에서 LAZY inAppNotification 직렬화 안전 보장
        return notificationRepository.findByIdWithInApp(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Notification> listByRecipient(String recipientId, Boolean read) {
        if (read == null) {
            return notificationRepository.findByRecipientId(recipientId);
        }
        if (Boolean.FALSE.equals(read)) {
            return notificationRepository.findUnreadByRecipientId(recipientId, NotificationChannel.IN_APP);
        }
        return notificationRepository.findReadByRecipientId(recipientId, NotificationChannel.IN_APP);
    }

    @Transactional
    public boolean markAsRead(UUID notificationId) {
        return inAppNotificationRepository.markReadById(notificationId) > 0;
    }

    @Transactional
    public Notification retryDead(UUID id) {
        Notification n = notificationRepository.findByIdWithInApp(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (n.getStatus() != NotificationStatus.DEAD) {
            throw new IllegalStateException("Only DEAD notifications can be retried, current status: " + n.getStatus());
        }
        // 수동 재시도 한도 체크: 자동 재시도(maxAttempts) 이후 몇 번 더 허용할지를 manualMaxAttempts로 제어
        // 기존 이력은 초기화하지 않고 누적 — 실패 패턴 분석 및 감사 목적 보존
        long totalAttempts = notificationAttemptRepository.countByNotificationId(id);
        long manualAttemptsDone = Math.max(0L, totalAttempts - maxAttempts);
        if (manualAttemptsDone >= manualMaxAttempts) {
            throw new IllegalStateException(
                    "Manual retry limit exceeded (" + manualMaxAttempts + " manual retries after auto-retry exhaustion)");
        }
        n.markPending();
        return notificationRepository.save(n);
    }
}
