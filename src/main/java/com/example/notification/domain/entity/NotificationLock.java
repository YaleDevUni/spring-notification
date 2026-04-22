package com.example.notification.domain.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_locks")
public class NotificationLock {

    @Id
    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;

    @Column(name = "locked_at", nullable = false, updatable = false)
    private Instant lockedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected NotificationLock() {}

    public static NotificationLock create(UUID notificationId, String lockedBy, long expireSeconds) {
        NotificationLock lock = new NotificationLock();
        lock.notificationId = notificationId;
        lock.lockedBy = lockedBy;
        lock.lockedAt = Instant.now();
        lock.expiresAt = lock.lockedAt.plusSeconds(expireSeconds);
        return lock;
    }

    public UUID getNotificationId() { return notificationId; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
