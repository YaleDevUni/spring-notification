package com.example.notification.domain.processor;

import com.example.notification.domain.entity.InAppNotification;
import com.example.notification.domain.entity.Notification;
import com.example.notification.domain.enums.NotificationChannel;
import com.example.notification.infrastructure.channel.EmailChannelSender;
import com.example.notification.infrastructure.channel.InAppChannelSender;
import com.example.notification.infrastructure.repository.InAppNotificationRepository;
import com.example.notification.infrastructure.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationProcessorImpl implements NotificationProcessor {

    private final EmailChannelSender emailSender;
    private final InAppChannelSender inAppSender;
    private final InAppNotificationRepository inAppRepository;
    private final NotificationRepository notificationRepository;

    public NotificationProcessorImpl(EmailChannelSender emailSender,
                                     InAppChannelSender inAppSender,
                                     InAppNotificationRepository inAppRepository,
                                     NotificationRepository notificationRepository) {
        this.emailSender = emailSender;
        this.inAppSender = inAppSender;
        this.inAppRepository = inAppRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public ProcessResult process(Notification notification) {
        try {
            if (notification.getChannel() == NotificationChannel.EMAIL) {
                emailSender.send(notification);
            } else {
                inAppSender.send(notification);
                // getReferenceById creates a managed proxy within the current transaction,
                // avoiding DetachedObjectException when persisting @MapsId relationship
                Notification ref = notificationRepository.getReferenceById(notification.getId());
                inAppRepository.save(InAppNotification.create(ref));
            }
            return new ProcessResult.Success();
        } catch (Exception e) {
            return new ProcessResult.Failure(e);
        }
    }
}
