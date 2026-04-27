package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.entity.NotificationTemplate;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.enums.NotificationType;
import com.example.notification.infrastructure.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

// 실 DB에 템플릿을 저장하고 sender가 변수 치환된 body를 로그에 출력하는지 검증
@SuppressWarnings("null")
@DataJpaTest
@Import({InAppChannelSender.class, EmailChannelSender.class})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5433/notification_test",
        "spring.datasource.username=user",
        "spring.datasource.password=password",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ChannelSenderIntegrationTest {

    @Autowired NotificationTemplateRepository templateRepository;
    @Autowired InAppChannelSender inAppSender;
    @Autowired EmailChannelSender emailSender;

    @Test
    @DisplayName("InAppChannelSender — DB 템플릿 조회 후 변수 치환된 body 출력")
    void inApp_renders_template_from_db(CapturedOutput output) {
        NotificationTemplate template = NotificationTemplate.create(
                NotificationType.LECTURE_START, NotificationChannel.IN_APP,
                null, "{refType} ({refId}) 강의가 시작되었습니다.");
        templateRepository.save(template);

        Notification n = Notification.create("user-99", NotificationType.LECTURE_START,
                NotificationChannel.IN_APP, "COURSE", "course-7", null);

        inAppSender.send(n);

        assertThat(output.getOut()).contains("body=COURSE (course-7) 강의가 시작되었습니다.");
    }

    @Test
    @DisplayName("EmailChannelSender — DB 템플릿 조회 후 subject·body 치환 출력")
    void email_renders_template_from_db(CapturedOutput output) {
        templateRepository.save(NotificationTemplate.create(
                NotificationType.EVENT_REMINDER, NotificationChannel.EMAIL,
                "이벤트 일정 안내",
                "안녕하세요 {recipientId}님, {refType} ({refId}) 이벤트가 곧 시작됩니다."));

        Notification n = Notification.create("user-99", NotificationType.EVENT_REMINDER,
                NotificationChannel.EMAIL, "SEMINAR", "sem-3", null);

        emailSender.send(n);

        assertThat(output.getOut())
                .contains("subject=이벤트 일정 안내")
                .contains("body=안녕하세요 user-99님, SEMINAR (sem-3) 이벤트가 곧 시작됩니다.");
    }

    @Test
    @DisplayName("InAppChannelSender — DB에 템플릿 없으면 (no template) 출력")
    void inApp_fallback_when_no_template_in_db(CapturedOutput output) {
        Notification n = Notification.create("user-99", NotificationType.SYSTEM_ALERT,
                NotificationChannel.IN_APP, "SYS", "sys-1", null);

        inAppSender.send(n);

        assertThat(output.getOut()).contains("(no template)");
    }
}
