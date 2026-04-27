package com.example.notification.infrastructure.consumer;

import com.example.notification.domain.processor.ProcessResult;

import java.util.UUID;

/**
 * 알림 발송 Consumer 인터페이스.
 *
 * 구현체:
 *   - DbPollingConsumer  (@Profile("db"))   : DB 폴링 기반, 앱 레벨 재시도
 *   - KafkaConsumer      (@Profile("kafka")) : Kafka 기반, 인프라 레벨 재시도
 *
 * 모든 구현체는 반드시 발송 결과를 notification_attempts에 기록해야 한다.
 * recordAttempt()를 구현하지 않으면 컴파일 에러로 강제된다.
 */
public interface NotificationConsumer {
    void start();
    void stop();
    void recordAttempt(UUID notificationId, ProcessResult result, int attemptNumber, String instanceId);
}
