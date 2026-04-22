package com.example.notification.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "in_app_notifications")
public class InAppNotification {

    @Id
    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "notification_id")
    private Notification notification;

    @Column(name = "read_at")
    private Instant readAt;

    protected InAppNotification() {}

    public static InAppNotification create(Notification notification) {
        InAppNotification n = new InAppNotification();
        n.notification = notification;
        return n;
    }

    public boolean markRead() {
        if (readAt != null) return false;
        readAt = Instant.now();
        return true;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getNotificationId() { return notificationId; }
    @JsonIgnore
    public Notification getNotification() { return notification; }
    public Instant getReadAt() { return readAt; }
}
