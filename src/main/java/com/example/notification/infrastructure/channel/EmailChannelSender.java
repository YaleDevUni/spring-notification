package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailChannelSender implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

    @Override
    public void send(Notification notification) {
        log.info("[EMAIL] recipient={} type={} refId={}",
                notification.getRecipientId(),
                notification.getType(),
                notification.getRefId());
    }
}
