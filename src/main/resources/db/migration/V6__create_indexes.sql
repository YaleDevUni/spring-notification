-- Worker 폴링 성능: PENDING 상태 + 예약 시각 기준
CREATE INDEX idx_notifications_status_scheduled
    ON notifications (status, scheduled_at)
    WHERE status = 'PENDING';

-- 수신자별 목록 조회
CREATE INDEX idx_notifications_recipient
    ON notifications (recipient_id, created_at DESC);

-- 읽음/안읽음 필터 조회
CREATE INDEX idx_in_app_unread
    ON in_app_notifications (notification_id)
    WHERE read_at IS NULL;

-- 좀비 락 감지
CREATE INDEX idx_locks_expires_at
    ON notification_locks (expires_at);

-- 재시도 이력 조회
CREATE INDEX idx_attempts_notification_id
    ON notification_attempts (notification_id, attempt_number);
