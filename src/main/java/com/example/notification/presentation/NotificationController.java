package com.example.notification.presentation;

import com.example.notification.application.dto.CreateNotificationRequest;
import com.example.notification.application.service.NotificationService;
import jakarta.validation.Valid;
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

    // 202 Accepted: DB INSERT까지만 동기, 실제 발송은 Worker 스레드가 비동기 처리
    // HTTP 스레드는 발송 성공 여부를 알 수 없으므로 200 OK가 아닌 202가 의미상 정확
    @PostMapping
    public ResponseEntity<NotificationResponse> create(@Valid @RequestBody CreateNotificationRequestBody body) {
        CreateNotificationRequest req = new CreateNotificationRequest(
                body.recipientId(), body.type(), body.channel(),
                body.refType(), body.refId(), body.scheduledAt());
        return ResponseEntity.accepted().body(NotificationResponse.from(notificationService.createNotification(req)));
    }

    // 디버깅 및 관리자 대시보드용
    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(NotificationResponse.from(notificationService.getNotification(id)));
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam String recipientId,
            @RequestParam(required = false) Boolean read) {
        return ResponseEntity.ok(
                notificationService.listByRecipient(recipientId, read).stream()
                        .map(NotificationResponse::from)
                        .toList());
    }

    // 200 vs 204: 최초 읽음 처리 성공이면 200, 이미 읽은 상태(no-op)면 204
    // 클라이언트가 읽음 처리 성공 여부를 상태 코드로 구분 가능
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        boolean updated = notificationService.markAsRead(id);
        return updated
                ? ResponseEntity.ok().build()
                : ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<NotificationResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(NotificationResponse.from(notificationService.retryDead(id)));
    }
}
