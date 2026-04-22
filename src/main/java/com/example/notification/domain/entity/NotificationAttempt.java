package com.example.notification.domain.entity;

import com.example.notification.domain.enums.AttemptStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_attempts")
public class NotificationAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttemptStatus status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    protected NotificationAttempt() {}

    public static NotificationAttempt success(UUID notificationId, int attemptNumber, String lockedBy) {
        NotificationAttempt a = new NotificationAttempt();
        a.notificationId = notificationId;
        a.attemptNumber = attemptNumber;
        a.status = AttemptStatus.SUCCESS;
        a.lockedBy = lockedBy;
        a.attemptedAt = Instant.now();
        return a;
    }

    public static NotificationAttempt failure(UUID notificationId, int attemptNumber,
                                               String failureReason, String lockedBy) {
        NotificationAttempt a = new NotificationAttempt();
        a.notificationId = notificationId;
        a.attemptNumber = attemptNumber;
        a.status = AttemptStatus.FAILURE;
        a.failureReason = failureReason;
        a.lockedBy = lockedBy;
        a.attemptedAt = Instant.now();
        return a;
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public int getAttemptNumber() { return attemptNumber; }
    public AttemptStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public String getLockedBy() { return lockedBy; }
}
