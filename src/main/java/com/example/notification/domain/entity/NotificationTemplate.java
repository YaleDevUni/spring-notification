package com.example.notification.domain.entity;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "subject_template", columnDefinition = "TEXT")
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    protected NotificationTemplate() {}

    public static NotificationTemplate create(NotificationType type, NotificationChannel channel,
                                              String subjectTemplate, String bodyTemplate) {
        NotificationTemplate t = new NotificationTemplate();
        t.type = type;
        t.channel = channel;
        t.subjectTemplate = subjectTemplate;
        t.bodyTemplate = bodyTemplate;
        return t;
    }

    // 지원 변수: {recipientId}, {type}, {refType}, {refId}
    public String render(Notification n) {
        return bodyTemplate
                .replace("{recipientId}", n.getRecipientId())
                .replace("{type}", n.getType().name())
                .replace("{refType}", n.getRefType())
                .replace("{refId}", n.getRefId());
    }

    public UUID getId() { return id; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public String getBodyTemplate() { return bodyTemplate; }
}
