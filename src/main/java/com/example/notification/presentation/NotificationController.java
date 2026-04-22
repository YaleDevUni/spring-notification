package com.example.notification.presentation;

import com.example.notification.application.dto.CreateNotificationRequest;
import com.example.notification.application.service.NotificationService;
import com.example.notification.domain.entity.Notification;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<Notification> create(@Valid @RequestBody CreateNotificationRequestBody body) {
        CreateNotificationRequest req = new CreateNotificationRequest(
                body.recipientId(), body.type(), body.channel(),
                body.refType(), body.refId(), body.scheduledAt());
        Notification saved = notificationService.createNotification(req);
        return ResponseEntity.accepted().body(saved);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notification> get(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.getNotification(id));
    }

    @GetMapping
    public ResponseEntity<List<Notification>> list(
            @RequestParam String recipientId,
            @RequestParam(required = false) Boolean read) {
        return ResponseEntity.ok(notificationService.listByRecipient(recipientId, read));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        boolean updated = notificationService.markAsRead(id);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Notification> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.retryDead(id));
    }
}
