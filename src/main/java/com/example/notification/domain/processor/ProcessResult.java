package com.example.notification.domain.processor;

public sealed interface ProcessResult {
    record Success() implements ProcessResult {}
    record Failure(Throwable cause) implements ProcessResult {}
}
