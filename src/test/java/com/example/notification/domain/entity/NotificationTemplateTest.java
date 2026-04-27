package com.example.notification.domain.entity;

import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateTest {

    private Notification notification() {
        return Notification.create("user-42", NotificationType.LECTURE_START,
                NotificationChannel.EMAIL, "LECTURE", "lec-99", null);
    }

    @Test
    @DisplayName("render — {recipientId}, {type}, {refType}, {refId} 모두 치환된다")
    void render_replaces_all_variables() {
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.LECTURE_START, NotificationChannel.EMAIL,
                "강의 알림",
                "{recipientId}님, {refType}({refId}) {type} 알림입니다.");

        String result = template.render(notification());

        assertThat(result).isEqualTo("user-42님, LECTURE(lec-99) LECTURE_START 알림입니다.");
    }

    @Test
    @DisplayName("render — 변수 없는 템플릿은 그대로 반환")
    void render_with_no_variables() {
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.LECTURE_START, NotificationChannel.EMAIL,
                null, "고정 메시지입니다.");

        String result = template.render(notification());

        assertThat(result).isEqualTo("고정 메시지입니다.");
    }

    @Test
    @DisplayName("render — 동일 변수 여러 번 사용 시 모두 치환")
    void render_replaces_repeated_variables() {
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.LECTURE_START, NotificationChannel.EMAIL,
                null, "{recipientId}님 안녕하세요. {recipientId}님의 강의입니다.");

        String result = template.render(notification());

        assertThat(result).isEqualTo("user-42님 안녕하세요. user-42님의 강의입니다.");
    }
}
