package com.example.notification.application.service;

import com.example.notification.application.dto.CreateNotificationRequest;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final InAppNotificationRepository inAppNotificationRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               InAppNotificationRepository inAppNotificationRepository) {
        this.notificationRepository = notificationRepository;
        this.inAppNotificationRepository = inAppNotificationRepository;
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
        return notificationRepository.findById(id)
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
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        if (n.getStatus() != NotificationStatus.DEAD) {
            throw new IllegalStateException("Only DEAD notifications can be retried, current status: " + n.getStatus());
        }
        n.markPending();
        return notificationRepository.save(n);
    }
}
