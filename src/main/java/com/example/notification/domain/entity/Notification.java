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
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "ref_type", nullable = false)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private String refId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // cascade=ALL: InAppNotification은 독립 생명주기 없음 — Notification 삭제 시 함께 제거
    // fetch=LAZY: 조회 API에서 N+1 방지를 위해 필요한 경우만 @Query로 명시적 fetch join
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

    // boolean 반환: Worker가 updateStatusIfMatch(PENDING→PROCESSING) 결과와 이중 검증하는 용도
    // DB 레벨 CAS(updateStatusIfMatch)가 1차 방어선, 이 메서드는 엔티티 상태 동기화
    public boolean markProcessing() {
        if (status != NotificationStatus.PENDING) return false;
        status = NotificationStatus.PROCESSING;
        return true;
    }

    public void markPending() {
        status = NotificationStatus.PENDING;
    }

    public void markDead() {
        status = NotificationStatus.DEAD;
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
