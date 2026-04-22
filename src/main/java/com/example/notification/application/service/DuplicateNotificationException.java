package com.example.notification.application.service;

public class DuplicateNotificationException extends RuntimeException {
    public DuplicateNotificationException() {
        super("Duplicate notification");
    }
}
