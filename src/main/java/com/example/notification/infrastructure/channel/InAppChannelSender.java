package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InAppChannelSender implements NotificationChannelSender {

    private static final Logger log = LoggerFactory.getLogger(InAppChannelSender.class);

    @Override
    public void send(Notification notification) {
        log.info("[IN_APP] recipient={} type={} refId={}",
                notification.getRecipientId(),
                notification.getType(),
                notification.getRefId());
    }
}
