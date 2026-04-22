package com.example.notification.infrastructure.processor;

import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.domain.processor.NotificationProcessor;
import com.example.notification.domain.processor.ProcessResult;
import com.example.notification.infrastructure.channel.EmailChannelSender;
import com.example.notification.infrastructure.channel.InAppChannelSender;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 레이어 위치: infrastructure — domain 인터페이스(NotificationProcessor)를 구현하되,
// 채널/레포지토리 등 infrastructure 의존성을 직접 사용하므로 domain 패키지에 둘 수 없음
@Component
public class NotificationProcessorImpl implements NotificationProcessor {

    private final EmailChannelSender emailSender;
    private final InAppChannelSender inAppSender;
    private final InAppNotificationRepository inAppRepository;

    public NotificationProcessorImpl(EmailChannelSender emailSender,
                                     InAppChannelSender inAppSender,
                                     InAppNotificationRepository inAppRepository) {
        this.emailSender = emailSender;
        this.inAppSender = inAppSender;
        this.inAppRepository = inAppRepository;
    }

    // @Transactional: 워커 스레드에서 호출되므로 AOP 프록시가 정상 동작하려면
    //   이 빈이 Spring 컨테이너를 통해 주입된 프록시여야 함 — 직접 new 금지
    @Override
    @Transactional
    public ProcessResult process(Notification notification) {
        try {
            if (notification.getChannel() == NotificationChannel.EMAIL) {
                emailSender.send(notification);
            } else {
                inAppSender.send(notification);
                // insertIfAbsent: ON CONFLICT DO NOTHING — 좀비 복구 후 재처리 시 중복 PK 충돌 방지
                // 기존 save()는 PK 충돌 시 DataIntegrityViolationException → ProcessResult.Failure → DEAD 전락
                inAppRepository.insertIfAbsent(notification.getId());
            }
            return new ProcessResult.Success();
        } catch (Exception e) {
            return new ProcessResult.Failure(e);
        }
    }
}
