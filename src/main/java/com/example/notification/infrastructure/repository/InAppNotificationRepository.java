package com.example.notification.infrastructure.repository;

import com.example.notification.domain.entity.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

    // ON CONFLICT DO NOTHING: 좀비 복구 후 재처리 시 동일 notification_id로 INSERT 재시도해도 PK 충돌 없이 멱등 처리
    // save() 대신 이 메서드를 사용해야 재처리 안전성이 보장됨
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO in_app_notifications (notification_id)
            VALUES (:id)
            ON CONFLICT (notification_id) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID notificationId);

    // read_at IS NULL 조건: 최초 1회만 반영, 이후 동시 요청은 no-op → DB 레벨 멱등 보장
    // clearAutomatically=true: 벌크 UPDATE 후 캐시 무효화
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE in_app_notifications
            SET read_at = NOW()
            WHERE notification_id = :id
            AND read_at IS NULL
            """, nativeQuery = true)
    int markReadById(@Param("id") UUID notificationId);
}
