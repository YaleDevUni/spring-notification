package com.example.notification.presentation;

import com.example.notification.application.service.DuplicateNotificationException;
import com.example.notification.application.service.NotificationNotFoundException;
import com.example.notification.application.service.NotificationService;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean NotificationService notificationService;

    private Notification stubNotification() {
        return Notification.create("user-1", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-1", null);
    }

    @Test
    @DisplayName("POST /notifications — 정상 접수 시 202 Accepted")
    void create_returns_202() throws Exception {
        when(notificationService.createNotification(any())).thenReturn(stubNotification());

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientId", "user-1",
                                "type", "LECTURE_START",
                                "channel", "EMAIL",
                                "refType", "LECTURE",
                                "refId", "lec-1"
                        ))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("POST /notifications — 중복 시 409 Conflict")
    void create_returns_409_on_duplicate() throws Exception {
        when(notificationService.createNotification(any()))
                .thenThrow(new DuplicateNotificationException());

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "recipientId", "user-1",
                                "type", "LECTURE_START",
                                "channel", "EMAIL",
                                "refType", "LECTURE",
                                "refId", "lec-1"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /notifications — 필수 필드 누락 시 400 Bad Request")
    void create_returns_400_on_missing_field() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "LECTURE_START"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /notifications/{id} — 존재하면 200 OK")
    void get_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.getNotification(id)).thenReturn(stubNotification());

        mockMvc.perform(get("/notifications/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /notifications/{id} — 없으면 404 Not Found")
    void get_returns_404() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.getNotification(id))
                .thenThrow(new NotificationNotFoundException(id));

        mockMvc.perform(get("/notifications/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /notifications — recipientId로 전체 목록 조회")
    void list_returns_200() throws Exception {
        when(notificationService.listByRecipient("user-1", null))
                .thenReturn(List.of(stubNotification()));

        mockMvc.perform(get("/notifications").param("recipientId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /notifications?read=false — 미읽음 필터")
    void list_unread_filter() throws Exception {
        when(notificationService.listByRecipient("user-1", false))
                .thenReturn(List.of());

        mockMvc.perform(get("/notifications")
                        .param("recipientId", "user-1")
                        .param("read", "false"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /notifications/{id}/read — 성공 시 200 OK")
    void markAsRead_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.markAsRead(id)).thenReturn(true);

        mockMvc.perform(patch("/notifications/{id}/read", id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /notifications/{id}/read — 이미 읽음이면 204 No Content")
    void markAsRead_returns_204_when_already_read() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.markAsRead(id)).thenReturn(false);

        mockMvc.perform(patch("/notifications/{id}/read", id))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /notifications/{id}/retry — 성공 시 200 OK")
    void retry_returns_200() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.retryDead(id)).thenReturn(stubNotification());

        mockMvc.perform(post("/notifications/{id}/retry", id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /notifications/{id}/retry — DEAD 아닌 경우 409 Conflict")
    void retry_returns_409_when_not_dead() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.retryDead(id))
                .thenThrow(new IllegalStateException("not DEAD"));

        mockMvc.perform(post("/notifications/{id}/retry", id))
                .andExpect(status().isConflict());
    }
}
