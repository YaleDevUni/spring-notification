package com.example.notification;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import com.example.notification.infrastructure.repository.NotificationAttemptRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 전체 스택 통합 테스트: HTTP → Service → Repository → 실 DB 흐름을 검증
// @Transactional: 각 테스트 후 롤백 — 테스트 간 데이터 격리, DB 정리 불필요
// spring.profiles.active="": "db" 프로파일 비활성화 → DbPollingConsumer 미시작 (테스트 중 폴링 간섭 방지)
// 사전 조건: docker compose up db (포트 5433, notification_test DB 존재)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/notification_test",
        "spring.datasource.username=user",
        "spring.datasource.password=password",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false",
        "spring.profiles.active="
})
class NotificationApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired NotificationRepository notificationRepository;
    @Autowired InAppNotificationRepository inAppNotificationRepository;
    @Autowired NotificationAttemptRepository notificationAttemptRepository;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // --- POST /notifications ---

    @Test
    @DisplayName("POST /notifications — 정상 접수 시 202, DB에 PENDING 저장")
    void create_returns_202_and_persists() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recipientId", "u1",
                                "type", "LECTURE_START",
                                "channel", "EMAIL",
                                "refType", "LECTURE",
                                "refId", "lec-1"
                        ))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").isNotEmpty());

        assertThat(notificationRepository.findByRecipientId("u1")).hasSize(1);
    }

    @Test
    @DisplayName("POST /notifications — 중복 요청 시 409 Conflict")
    void create_duplicate_returns_409() throws Exception {
        String body = json(Map.of(
                "recipientId", "u2",
                "type", "EVENT_REMINDER",
                "channel", "EMAIL",
                "refType", "EVENT",
                "refId", "evt-1"
        ));

        mockMvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /notifications — 필수 필드 누락 시 400 Bad Request")
    void create_missing_field_returns_400() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "LECTURE_START"))))
                .andExpect(status().isBadRequest());
    }

    // --- GET /notifications/{id} ---

    @Test
    @DisplayName("GET /notifications/{id} — 존재하면 200 OK, 필드 반환")
    void get_existing_returns_200() throws Exception {
        Notification saved = notificationRepository.save(
                Notification.create("u3", NotificationType.LECTURE_START,
                        NotificationChannel.EMAIL, "LECTURE", "lec-3", null));

        mockMvc.perform(get("/notifications/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientId").value("u3"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /notifications/{id} — 없으면 404 Not Found")
    void get_missing_returns_404() throws Exception {
        mockMvc.perform(get("/notifications/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    // --- GET /notifications?recipientId ---

    @Test
    @DisplayName("GET /notifications?recipientId — 해당 수신자 목록 반환")
    void list_by_recipient_returns_all() throws Exception {
        notificationRepository.save(Notification.create("u4", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-4a", null));
        notificationRepository.save(Notification.create("u4", NotificationType.EVENT_REMINDER,
                NotificationChannel.EMAIL, "EVENT", "evt-4a", null));

        mockMvc.perform(get("/notifications").param("recipientId", "u4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // --- PATCH /notifications/{id}/read ---

    @Test
    @DisplayName("PATCH /{id}/read — 읽음 처리 성공 시 200, 멱등 204")
    void mark_as_read_idempotent() throws Exception {
        Notification n = notificationRepository.saveAndFlush(
                Notification.create("u5", NotificationType.EVENT_REMINDER,
                        NotificationChannel.IN_APP, "EVENT", "evt-5", null));

        // in_app_notifications 행 직접 생성
        var ref = notificationRepository.getReferenceById(n.getId());
        inAppNotificationRepository.saveAndFlush(
                com.example.notification.domain.entity.InAppNotification.create(ref));

        mockMvc.perform(patch("/notifications/{id}/in-app/read", n.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/notifications/{id}/in-app/read", n.getId()))
                .andExpect(status().isNoContent());
    }

    // --- POST /notifications/{id}/retry ---

    @Test
    @DisplayName("POST /{id}/retry — DEAD 알림을 PENDING으로 변경")
    void retry_dead_returns_200() throws Exception {
        Notification n = Notification.create("u6", NotificationType.SYSTEM_ALERT,
                NotificationChannel.EMAIL, "SYS", "s-6", null);
        n.markDead();
        notificationRepository.save(n);

        mockMvc.perform(post("/notifications/{id}/retry", n.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /{id}/retry — DEAD가 아닌 알림에 retry 시 409")
    void retry_non_dead_returns_409() throws Exception {
        Notification n = notificationRepository.save(
                Notification.create("u7", NotificationType.SYSTEM_ALERT,
                        NotificationChannel.EMAIL, "SYS", "s-7", null));

        mockMvc.perform(post("/notifications/{id}/retry", n.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /{id}/retry — 수동 재시도 한도 초과 시 409 (attempts >= maxAttempts + manualMaxAttempts)")
    void retry_manual_limit_exceeded_returns_409() throws Exception {
        // application.yml: max-attempts=3, manual-max-attempts=2 → total limit = 5
        Notification n = Notification.create("u8", NotificationType.SYSTEM_ALERT,
                NotificationChannel.EMAIL, "SYS", "s-8", null);
        n.markDead();
        notificationRepository.save(n);

        // attempts 5건 직접 저장 (3 auto + 2 manual = 한도 도달)
        for (int i = 1; i <= 5; i++) {
            notificationAttemptRepository.save(
                    com.example.notification.domain.entity.NotificationAttempt
                            .failure(n.getId(), i, "test failure", "test-instance"));
        }

        mockMvc.perform(post("/notifications/{id}/retry", n.getId()))
                .andExpect(status().isConflict());
    }
}
