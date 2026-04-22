package com.example.notification.domain.processor;

import com.example.notification.domain.entity.Notification;

public interface NotificationProcessor {
    ProcessResult process(Notification notification);
}
