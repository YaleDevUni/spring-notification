package com.example.notification.infrastructure.channel;

import com.example.notification.domain.entity.Notification;

public interface NotificationChannelSender {

    void send(Notification notification);
}
