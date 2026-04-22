package com.example.notification.domain.entity;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationStatus;
import com.example.notification.domain.enums.NotificationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private String recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "notification_type")
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "notification_channel")
    private NotificationChannel channel;

    @Column(name = "ref_type", nullable = false)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private String refId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "notification_status")
    private NotificationStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToOne(mappedBy = "notification", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private InAppNotification inAppNotification;

    protected Notification() {}

    public static Notification create(String recipientId, NotificationType type,
                                      NotificationChannel channel, String refType,
                                      String refId, Instant scheduledAt) {
        Notification n = new Notification();
        n.recipientId = recipientId;
        n.type = type;
        n.channel = channel;
        n.refType = refType;
        n.refId = refId;
        n.status = NotificationStatus.PENDING;
        n.scheduledAt = scheduledAt;
        n.createdAt = Instant.now();
        return n;
    }

    public boolean markProcessing() {
        if (status != NotificationStatus.PENDING) return false;
        status = NotificationStatus.PROCESSING;
        return true;
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed() {
        status = NotificationStatus.FAILED;
    }

    public void markDead() {
        status = NotificationStatus.DEAD;
    }

    public void markPending() {
        status = NotificationStatus.PENDING;
    }

    public boolean isReadyToProcess() {
        if (status != NotificationStatus.PENDING) return false;
        return scheduledAt == null || !Instant.now().isBefore(scheduledAt);
    }

    public UUID getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getRefType() { return refType; }
    public String getRefId() { return refId; }
    public NotificationStatus getStatus() { return status; }
    public Instant getScheduledAt() { return scheduledAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public InAppNotification getInAppNotification() { return inAppNotification; }
}
